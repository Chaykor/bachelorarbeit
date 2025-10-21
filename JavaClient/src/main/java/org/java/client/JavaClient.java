package org.java.client;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Time;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.json.JSONObject;
import com.opencsv.CSVWriter;

public class JavaClient {
    private static final String JSON_URL = "http://localhost:3000/json";
    private static final String XML_URL = "http://localhost:3000/xml";

    private static final int SAMPLE_SIZE = 1000;
    private static final int NUM_THREADS = 2;// Anzahl der parallelen Threads
    private static final List<String> FILESIZE = List.of("10kb", "50kb", "100kb", "500kb", "1000kb", "1500kb", "5000kb", "10000kb");

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        for (String size : FILESIZE) {
            String jsonFilePath ="test_data/" + size + "_JSON.json";
            String xmlFilePath ="test_data/" + size + "_XML.xml";

            List<Future<long[]>> futures = new ArrayList<>();
            Instant totalStartTime = Instant.now();


            String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
            String xmlContent = new String(Files.readAllBytes(Paths.get(xmlFilePath)));
            JSONObject json = new JSONObject(jsonContent);


            for (int i = 0; i < SAMPLE_SIZE; i++) {
                futures.add(executor.submit(() -> sendRequests(json.toString(), xmlContent)));
            }

            // Ergebnisse einsammeln
            List<long[]> results = new ArrayList<>();
            for (Future<long[]> future : futures) {
                results.add(future.get());
            }

            long totalTime = ChronoUnit.MILLIS.between(totalStartTime, Instant.now());
            long jsonRes = results.stream().mapToLong(data -> data[0]).sum();
            long xmlRes = results.stream().mapToLong(data -> data[1]).sum();

            System.out.println("Results " + size + ": " + results.size());
            System.out.printf("JSON Durchschnitt: %s µs\n", (jsonRes / SAMPLE_SIZE));
            System.out.printf("XML Durchschnitt: %s µs\n", (xmlRes / SAMPLE_SIZE));
            System.out.printf("Total Time: %s ms\n", totalTime);

            writeToFile(results, totalTime, size);
            Thread.sleep(10000);


        }
        executor.shutdown();
    }

    private static long[] sendRequests(String jsonData, String xmlData) throws IOException, InterruptedException {
        System.out.println("Sending JSON Request");
        long startJson = System.nanoTime();
        sendJson(jsonData);
        long timeJson = (System.nanoTime() - startJson) / 1000; // In Mikrosekunden

        System.out.println("Sending XML Request");
        long startXml = System.nanoTime();
        sendXml(xmlData);
        long timeXml = (System.nanoTime() - startXml) / 1000; // In Mikrosekunden
        return new long[]{timeJson, timeXml};
    }

    private static void sendJson(String jsonData) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(JSON_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void sendXml(String xmlData) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(XML_URL))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(xmlData))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void writeToFile(List<long[]> results, long totalTime, String size) {
        String fileName = size + "_JavaResults.csv";
        List<String[]> resultStrings = new ArrayList<>();
        resultStrings.add(new String[]{"JsonRes", "XmlRes", "TotalTime"});
        resultStrings.add(new String[]{"NaN", "NaN", String.valueOf(totalTime)});

        for (long[] data : results) {
            resultStrings.add(new String[]{String.valueOf(data[0]), String.valueOf(data[1])});
        }

        try (CSVWriter writer = new CSVWriter(new FileWriter("output/" + fileName))) {
            writer.writeAll(resultStrings);
            System.out.println("CSV file saved successfully.");
        } catch (IOException e) {
            System.out.println("Fehler beim Speichern der CSV: " + e.getMessage());
        }
    }
}