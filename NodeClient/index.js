const fs = require("fs");
const axios = require("axios");

const client = axios.create();
const sampleSize = 1000;
const concurrencyLimit = 2; // Maximale Anzahl paralleler Requests

const sizes = ["10kb", "50kb", "100kb", "500kb", "1000kb", "1500kb", "5000kb", "10000kb", "15000kb"];
const URL = "http://localhost:3000";

async function main() {
    sizes.forEach((element) => {
        mainLoop(element)
        }
    )
}

async function mainLoop(size) {
    const jsonFilePath = "test_data/" + size + "_JSON.json";
    const jsonFileContent = fs.readFileSync(jsonFilePath, "utf-8");
    const jsonData = JSON.parse(jsonFileContent);

    const xmlFilePath = "test_data/" + size + "_XML.xml";
    const xmlData = fs.readFileSync(xmlFilePath, "utf-8");

    let results = [];
    let totalTimeStart = process.hrtime.bigint();

    let promises = [];
    for (let n = 0; n < sampleSize; n++) {
        promises.push(await runRequest(n, jsonData, xmlData));

        // Starte Requests in Gruppen von `concurrencyLimit`
        if (promises.length >= concurrencyLimit) {
            const batchResults = await Promise.all(promises);
            results.push(...batchResults);
            promises = []; // Leere die Promise-Liste
        }
    }

    // Warte auf die restlichen Requests
    if (promises.length > 0) {
        const batchResults = await Promise.all(promises);
        results.push(...batchResults);
    }

    let totalTime = Number(process.hrtime.bigint() - totalTimeStart) / 1_000_000; // In ms

    let jsonRes = 0;
    let xmlRes = 0;
    results.forEach((data) => {
        jsonRes += data[0];
        xmlRes += data[1];
    });

    console.log("Results:", results.length);
    console.log(`JSON Durchschnitt: ${jsonRes / sampleSize} µs`);
    console.log(`XML Durchschnitt: ${xmlRes / sampleSize} µs`);
    console.log(`Total Execution Time: ${totalTime} ms`);

    writeCSV(results, totalTime, size);
    await sleep(10000);
}

// Führt eine JSON- und XML-Anfrage aus
async function runRequest(n, jsonData, xmlData) {
    console.log("json sent");
    let start = process.hrtime.bigint();
    let resJ = await sendJson(jsonData);
    let timeJson = Number(process.hrtime.bigint() - start) / 1000;
    if (resJ === false) {
        timeJson = "NaN"
    }
    console.log("xml sent");
    start = process.hrtime.bigint();
    let resX = await sendXml(xmlData);
    let timeXml = Number(process.hrtime.bigint() - start) / 1000; // Mikrosekunden
    if (resX === false) {
        timeXml = "NaN"
    }
    return [timeJson, timeXml];
}

// Sendet JSON-Anfragen
async function sendJson(jsonData) {
    try {
        await client.post(URL + "/json", jsonData, {
            headers: { "Content-Type": "application/json" },

        });
        return true;
    } catch (error) {
        console.error("Error sending JSON request:", error.message);
        return false;
    }
}

// Sendet XML-Anfragen
async function sendXml(xmlData) {
    try {
        await client.post(URL + "/xml", xmlData, {
            headers: { "Content-Type": "application/xml" },
        });
        return true;
    } catch (error) {
        console.error("Error sending XML request:", error.message);
        return false;
    }
}

// Speichert Ergebnisse in CSV
function writeCSV(results, totalTime, size) {
    const headers = "JsonRes,XmlRes,TotalTime";

    const firstRow = ["NaN", "NaN", totalTime].join(",");

    const csvData = [
        headers,
        firstRow,
        ...results.map((row) => [...row, "NaN"].join(",")) // Fügt "NaN" für TotalTime in allen Zeilen hinzu
    ].join("\n");

    fs.writeFileSync("output/" + size + "_NodeJsResults.csv", csvData, "utf8");

    console.log("CSV gespeichert!");
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch(console.error);