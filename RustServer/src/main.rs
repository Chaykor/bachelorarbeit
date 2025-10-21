use axum::{
    body::Bytes,
    extract::BodyStream,
    http::{header, StatusCode},
    response::{IntoResponse},
    routing::post,
    Json, Router,
};
use futures::StreamExt;
use serde_json::json;
use std::net::SocketAddr;
use tower::{ServiceBuilder, limit::ConcurrencyLimitLayer, buffer::BufferLayer};
use tower_http::{trace::TraceLayer, limit::RequestBodyLimitLayer, timeout::TimeoutLayer};
use tracing::{info, Level};
use tracing_subscriber::FmtSubscriber;
use std::time::Duration;

#[tokio::main]
async fn main() {

    let subscriber = FmtSubscriber::builder()
        .with_max_level(Level::INFO)
        .finish();
    tracing::subscriber::set_global_default(subscriber).expect("Logger konnte nicht gesetzt werden");


    let app = Router::new()
        .route("/json", post(handle_json_discard))
        .route("/xml",  post(handle_xml_discard))
        .layer(RequestBodyLimitLayer::new(15 * 1024 * 1024));


    // Server starten
    let addr = SocketAddr::from(([0,0,0,0], 3000));
    info!("Server läuft unter http://{}", addr);

    axum::Server::bind(&addr)
        .serve(app.into_make_service_with_connect_info::<SocketAddr>())
        .await
        .unwrap();
}

async fn handle_json_discard(mut stream: BodyStream) -> impl IntoResponse {
    let mut total = 0usize;

    while let Some(next) = stream.next().await {
        match next {
            Ok(chunk) => { total += chunk.len(); }
            Err(err) => {
                return (StatusCode::BAD_REQUEST, format!("read error: {err}")).into_response();
            }
        }
    }

    (StatusCode::OK, Json(json!({
        "message": "JSON erfolgreich empfangen",
        "receivedBytes": total
    }))).into_response()
}

async fn handle_xml_discard(mut stream: BodyStream) -> impl IntoResponse {
    let mut total = 0usize;

    while let Some(next) = stream.next().await {
        match next {
            Ok(chunk) => { total += chunk.len(); }
            Err(err) => {
                return (StatusCode::BAD_REQUEST, format!("read error: {err}")).into_response();
            }
        }
    }

    let body = format!(
        "<response><message>XML erfolgreich empfangen</message><receivedBytes>{}</receivedBytes></response>",
        total
    );
    (
        StatusCode::OK,
        [(header::CONTENT_TYPE, "application/xml")],
        body,
    ).into_response()
}