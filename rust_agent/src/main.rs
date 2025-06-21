use axum::{
    extract::{DefaultBodyLimit, Multipart},
    http::StatusCode,
    response::{Html, IntoResponse},
    routing::{get, post},
    Router,
};
use std::io;
use std::net::SocketAddr;
use tokio::{fs::File, io::AsyncWriteExt};
use tracing::{info, instrument};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use open;

#[tokio::main]
async fn main() {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "rust_agent=debug,tower_http=debug".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let app = Router::new()
        .route("/", get(root))
        .route("/upload", post(upload))
        .layer(DefaultBodyLimit::max(50 * 1024 * 1024));

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
