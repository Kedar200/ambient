use axum::{
    extract::{DefaultBodyLimit, Multipart, State},
    http::StatusCode,
    response::{Html, IntoResponse, Json},
    routing::{get, post},
    Router,
};
use mdns_sd::{ServiceDaemon, ServiceInfo};
use serde::{Deserialize, Serialize};
use std::io;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::{fs::File, io::AsyncWriteExt, sync::Mutex};
use tracing::{info, instrument, warn};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use open;
use arboard::Clipboard;

#[derive(Serialize, Deserialize)]
struct ClipboardPayload {
    content: String,
}

struct AppState {
    clipboard: Mutex<Clipboard>,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "rust_agent=debug,tower_http=debug".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    // Start mDNS service advertisement
    let mdns = ServiceDaemon::new().expect("Failed to create mDNS daemon");
    let service_type = "_ambient._tcp.local.";
    let instance_name = hostname::get()
        .map(|h| h.to_string_lossy().to_string())
        .unwrap_or_else(|_| "AmbientOS".to_string());
    let port = 23921u16;

    let service_info = ServiceInfo::new(
        service_type,
        &instance_name,
        &format!("{}.local.", instance_name),
        "",
        port,
        None,
    )
    .expect("Failed to create service info")
    .enable_addr_auto();

    match mdns.register(service_info) {
        Ok(_) => info!("mDNS service registered as '{}' on port {}", instance_name, port),
        Err(e) => warn!("Failed to register mDNS service: {:?}", e),
    }

    let state = Arc::new(AppState {
        clipboard: Mutex::new(Clipboard::new().expect("Failed to initialize clipboard")),
    });

    let app = Router::new()
        .route("/", get(root))
        .route("/upload", post(upload))
        .route("/clipboard", get(get_clipboard).post(set_clipboard))
        .layer(DefaultBodyLimit::max(50 * 1024 * 1024))
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    info!("listening on {}", addr);
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .unwrap();

    // Graceful shutdown: unregister mDNS service
    info!("Shutting down mDNS service...");
    let _ = mdns.shutdown();
}

// basic handler that responds with a static string
async fn root() -> &'static str {
    "Ambient OS Agent is running!"
}

#[instrument(skip(multipart))]
async fn upload(mut multipart: Multipart) -> Result<Html<String>, AppError> {
    while let Some(field) = multipart.next_field().await? {
        let name = field.name().ok_or_else(|| AppError(StatusCode::BAD_REQUEST, "Field name not found".to_string()))?.to_string();

        if name == "file" {
            let file_name = field.file_name().ok_or_else(|| AppError(StatusCode::BAD_REQUEST, "File name not found".to_string()))?.to_string();
            let data = field.bytes().await?;

            info!(file_name, "Length of `{}` is {} bytes", file_name, data.len());

            // Create uploads directory if it doesn't exist
            tokio::fs::create_dir_all("uploads").await?;

            let path = format!("uploads/{}", file_name);
            let absolute_path = std::fs::canonicalize(".").unwrap().join(&path);
            let mut file = File::create(&path).await?;
            file.write_all(&data).await?;

            // Show macOS notification with the image
            show_image_notification(&absolute_path.to_string_lossy(), &file_name).await;

            return Ok(Html(format!("File `{}` uploaded successfully.", file_name)));
        }
    }

    Ok(Html("No file uploaded.".to_string()))
}

async fn show_image_notification(image_path: &str, file_name: &str) {
    // Use our custom PhotoToast app for beautiful floating notifications
    let toast_app = "/Users/kedar/personal/ambient/mac_toast/PhotoToast";
    
    let result = tokio::process::Command::new(toast_app)
        .args([image_path, file_name])
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn();

    match result {
        Ok(mut child) => {
            info!("Toast notification process spawned for {}", file_name);
            let stdout = child.stdout.take();
            let stderr = child.stderr.take();

            // In separate tasks, read and log outputs
            if let Some(stdout) = stdout {
                tokio::spawn(async move {
                    let mut reader = tokio::io::BufReader::new(stdout);
                    let mut line = String::new();
                    while let Ok(n) = tokio::io::AsyncBufReadExt::read_line(&mut reader, &mut line).await {
                        if n == 0 { break; }
                        info!("PhotoToast stdout: {}", line.trim());
                        line.clear();
                    }
                });
            }

            if let Some(stderr) = stderr {
                tokio::spawn(async move {
                    let mut reader = tokio::io::BufReader::new(stderr);
                    let mut line = String::new();
                    while let Ok(n) = tokio::io::AsyncBufReadExt::read_line(&mut reader, &mut line).await {
                        if n == 0 { break; }
                        warn!("PhotoToast stderr: {}", line.trim());
                        line.clear();
                    }
                });
            }

            tokio::spawn(async move {
                let status = child.wait().await;
                info!("PhotoToast process exited with status: {:?}", status);
            });
        }
        Err(e) => {
            warn!("Failed to spawn toast process: {:?}, falling back to system notification", e);
            // Fallback to osascript notification
            let script = format!(
                r#"display notification "Received: {}" with title "📸 Live Photo Wall" sound name "default""#,
                file_name
            );
            let _ = tokio::process::Command::new("osascript")
                .args(["-e", &script])
                .output()
                .await;
        }
    }
}

#[instrument(skip(state))]
async fn get_clipboard(
    State(state): State<Arc<AppState>>,
) -> Result<Json<ClipboardPayload>, AppError> {
    let mut clipboard = state.clipboard.lock().await;
    let content = clipboard.get_text()?;
    info!("Read clipboard content of length {}", content.len());
    Ok(Json(ClipboardPayload { content }))
}

#[instrument(skip(state, payload))]
async fn set_clipboard(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<ClipboardPayload>,
) -> Result<StatusCode, AppError> {
    let mut clipboard = state.clipboard.lock().await;
    clipboard.set_text(payload.content.clone())?;
    info!(content_length = payload.content.len(), "Set clipboard content");
    Ok(StatusCode::OK)
}

struct AppError(StatusCode, String);

impl IntoResponse for AppError {
    fn into_response(self) -> axum::response::Response {
        (self.0, self.1).into_response()
    }
}

impl From<axum::extract::multipart::MultipartError> for AppError {
    fn from(err: axum::extract::multipart::MultipartError) -> Self {
        AppError(StatusCode::BAD_REQUEST, format!("Upload error: {}", err))
    }
}

impl From<io::Error> for AppError {
    fn from(err: io::Error) -> Self {
        AppError(
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("File system error: {}", err),
        )
    }
}

impl From<arboard::Error> for AppError {
    fn from(err: arboard::Error) -> Self {
        AppError(
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("Clipboard error: {}", err),
        )
    }
}

async fn shutdown_signal() {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }

    info!("signal received, starting graceful shutdown");
}
