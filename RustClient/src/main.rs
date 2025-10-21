use std::error::Error;
use std::fs::File;
use std::time::{Duration, Instant};

use csv::Writer;
use futures::{stream, StreamExt};
use reqwest::{Client, Response};
use serde_json::Value;
use std::{fs, io};
use tokio::time::sleep;

const SIZES: &[&str] = &["10kb","50kb","100kb","500kb","1000kb","1500kb","5000kb","10000kb","15000kb"];

const SAMPLES: usize = 1000;
const CONCURRENCY: usize = 2; // sinnvolles Limit

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<(), Box<dyn Error>> {
    let client = reqwest::Client::builder()
        .pool_max_idle_per_host(64)
        .connect_timeout(Duration::from_secs(10))
        .timeout(Duration::from_secs(60))
        .tcp_keepalive(Some(Duration::from_secs(60)))
        .build()?;

    for &size in SIZES {
        let file_path_json = format!("test_data/{}_JSON.json", size);
        let file_content_json = fs::read_to_string(&file_path_json)
            .map_err(|e| io::Error::new(e.kind(), format!("JSON lesen {}: {}", file_path_json, e)))?;
        let json_value: Value = serde_json::from_str(&file_content_json)
            .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, format!("JSON parse {}: {}", file_path_json, e)))?;

        let file_path_xml = format!("test_data/{}_XML.xml", size);
        let xml_string = fs::read_to_string(&file_path_xml)
            .map_err(|e| io::Error::new(e.kind(), format!("XML lesen {}: {}", file_path_xml, e)))?;

        let start_total = Instant::now();

        let jobs = stream::iter(0..SAMPLES).map(|_| {
            let c = client.clone();
            let json = json_value.clone();
            let xml = xml_string.clone();

            async move {

                let json_ns = measure_json_rtt(&c, "http://3.66.216.72:3000/json", json).await;
                let xml_ns  = measure_xml_rtt(&c, "http://3.66.216.72:3000/xml", &xml).await;

                (json_ns, xml_ns)
            }
        });

        let results: Vec<(Option<i128>, Option<i128>)> =
            jobs.buffer_unordered(CONCURRENCY).collect().await;

        let total_ms = start_total.elapsed().as_millis();

        let mut json_sum: i128 = 0;
        let mut json_cnt: i128 = 0;
        let mut xml_sum: i128  = 0;
        let mut xml_cnt: i128  = 0;

        for (j, x) in &results {
            if let Some(v) = j { json_sum += *v; json_cnt += 1; }
            if let Some(v) = x { xml_sum  += *v; xml_cnt  += 1; }
        }

        let json_avg = if json_cnt > 0 { Some(json_sum / json_cnt) } else { None };
        let xml_avg  = if xml_cnt  > 0 { Some(xml_sum  / xml_cnt) } else { None };



        // --- CSV schreiben ---
        write_csv(size, &results, total_ms)?;

        println!("=== Größe {} ===", size);
        println!("JSON Durchschnitt: {}",
                 json_avg.map(|v| format!("{v} ns")).unwrap_or_else(|| "—".into()));
        println!("XML  Durchschnitt: {}",
                 xml_avg.map(|v| format!("{v} ns")).unwrap_or_else(|| "—".into()));
        println!("Gesamtdauer: {} ms\n", total_ms);
        sleep(Duration::from_secs(10)).await;
    }

    Ok(())
}

// ---- Mess-Helpers ----

async fn send_json(client: &Client, url: &str, json_data: Value) -> Result<Response, reqwest::Error> {
    client.post(url)
        .header("Content-Type", "application/json")
        .json(&json_data)
        .send()
        .await
}

async fn send_xml(client: &Client, url: &str, xml_data: &str) -> Result<Response, reqwest::Error> {
    client.post(url)
        .header("Content-Type", "application/xml")
        .body(xml_data.to_owned())
        .send()
        .await
}

async fn measure_json_rtt(client: &Client, url: &str, json_data: Value) -> Option<i128> {
    println!("json message sent");
    let t0 = Instant::now();
    let resp = match send_json(client, url, json_data).await {
        Ok(r) => r,
        Err(e) => { eprintln!("[JSON] request failed: {e}"); return None; }
    };
    if let Err(e) = resp.bytes().await {
        eprintln!("[JSON] read body failed: {e}");
        return None;
    }
    Some(t0.elapsed().as_nanos() as i128)

}

async fn measure_xml_rtt(client: &Client, url: &str, xml_data: &str) -> Option<i128> {
    println!("xml message sent");
    let t0 = Instant::now();
    let resp = match send_xml(client, url, xml_data).await {
        Ok(r) => r,
        Err(e) => { eprintln!("[XML] request failed: {e}"); return None; }
    };
    if let Err(e) = resp.bytes().await {
        eprintln!("[XML] read body failed: {e}");
        return None;
    }
    Some(t0.elapsed().as_nanos() as i128)
}

fn write_csv(size: &str, results: &[(Option<i128>, Option<i128>)], total_ms: u128) -> Result<(), Box<dyn Error>> {
    let tmp_path = format!("{}_RustResults.csv", size);
    let file_path = format!("output/{}", tmp_path);

    let file = File::create(&file_path)?;
    let mut wtr = Writer::from_writer(file);

    wtr.write_record(&["JsonRes", "XmlRes", "TotalTime"])?;
    wtr.write_record(&["NaN", "NaN", &total_ms.to_string()])?;
    for (j, x) in results {
        let jv = j.map(|v| v.to_string()).unwrap_or_else(|| "NaN".into());
        let xv = x.map(|v| v.to_string()).unwrap_or_else(|| "NaN".into());
        wtr.write_record(&[jv, xv, "NaN".into()])?;

    }
    wtr.flush()?;
    println!("CSV Datei gespeichert: {}", file_path);
    Ok(())
}