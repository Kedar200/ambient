use axum::{
    extract::{DefaultBodyLimit, Multipart, State},
    http::StatusCode,
    response::{Html, IntoResponse, Json},
    routing::{get, post},
    Router,
};
use serde::{Deserialize, Serialize};
use std::io;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::{fs::File, io::AsyncWriteExt, sync::Mutex};
use tracing::{info, instrument};
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

    let state = Arc::new(AppState {
        clipboard: Mutex::new(Clipboard::new().expect("Failed to initialize clipboard")),
    });

    let app = Router::new()
        .route("/", get(root))
        .route("/upload", post(upload))
        .route("/clipboard", get(get_clipboard).post(set_clipboard))
        .layer(DefaultBodyLimit::max(50 * 1024 * 1024))
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], 3000));
    info!("listening on {}", addr);
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
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

            let path = format!("uploads/{}", file_name);
            let mut file = File::create(&path).await?;
            file.write_all(&data).await?;

            // Open the file with the default application
            if let Err(e) = open::that(&path) {
                return Err(AppError(
                    StatusCode::INTERNAL_SERVER_ERROR,
                    format!("Failed to open file: {}", e),
                ));
            }

            return Ok(Html(format!("File `{}` uploaded and opened successfully.", file_name)));
        }
    }

    Ok(Html("No file uploaded.".to_string()))
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
