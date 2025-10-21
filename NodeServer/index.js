const cluster = require("cluster");
const os = require("os");

const NUM_CPUS = os.cpus().length;

if (cluster.isPrimary) {
    console.log(`Master ${process.pid} startet ${NUM_CPUS} Worker...`);
    console.log("HUUUURE")
    for (let i = 0; i < NUM_CPUS; i++) cluster.fork();

    cluster.on("exit", (worker, code, signal) => {
        console.log(`Worker ${worker.process.pid} beendet (code=${code}, signal=${signal}). Starte neu...`);
        cluster.fork();
    });
} else {
    const express = require("express");
    const cors = require("cors");

    const app = express();
    const PORT = process.env.PORT || 3000;
    const HOST = process.env.HOST || "0.0.0.0"; // wichtig für Docker/extern
    const MAX_REQUEST_SIZE = "15mb";

    // Middleware
    app.use(cors());
    app.use(express.json({ limit: MAX_REQUEST_SIZE, type: ["application/json", "text/json"] }));
    app.use(express.text({ limit: MAX_REQUEST_SIZE, type: ["application/xml", "text/xml", "application/*+xml"] }));

    // Health
    app.get("/health", (_req, res) => res.status(200).json({ ok: true, pid: process.pid }));

    // JSON Endpoint
    app.post("/json", (req, res) => {
        // große Payloads nicht komplett loggen
        const size = typeof req.body === "string"
            ? Buffer.byteLength(req.body)
            : Buffer.byteLength(JSON.stringify(req.body || {}));
        console.log(`[Worker ${process.pid}] JSON empfangen (${size} bytes)`);

        // korrekt: Objekt senden; Express setzt Content-Type automatisch
        return res.status(200).json({
            message: "JSON erfolgreich empfangen",
            receivedBytes: size,
            pid: process.pid,
        });
    });

    // XML Endpoint
    app.post("/xml", (req, res) => {
        const size = Buffer.byteLength(req.body || "");
        console.log(`[Worker ${process.pid}] XML empfangen (${size} bytes)`);

        const responseBody =
            `<response><message>XML erfolgreich empfangen</message><receivedBytes>${size}</receivedBytes><pid>${process.pid}</pid></response>`;

        // Header VOR dem Senden setzen
        res.set("Content-Type", "application/xml");
        return res.status(200).send(responseBody);
    });

    // Fehler-Handler (sauberer JSON-Fehler für alle Routen)
    app.use((err, _req, res, _next) => {
        console.error(`[Worker ${process.pid}] Fehler:`, err?.message);
        if (!res.headersSent) {
            res.status(500).json({ error: "Internal Server Error", pid: process.pid });
        }
    });

    const server = app.listen(PORT, HOST, () => {
        console.log(`[Worker ${process.pid}] Server läuft auf http://${HOST}:${PORT}`);
    });

    // Graceful shutdown
    const shutdown = () => {
        console.log(`[Worker ${process.pid}] Shutdown...`);
        server.close(() => process.exit(0));
        setTimeout(() => process.exit(1), 5000);
    };
    process.on("SIGTERM", shutdown);
    process.on("SIGINT", shutdown);
}