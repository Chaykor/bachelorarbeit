package main

import (
	"bytes"
	"encoding/csv"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"
)

const (
	numThreads = 2 // Maximal erlaubte parallele Threads
	sampleSize = 1000
	URL        = "http://localhost:3000/"
)

func main() {
	time.Sleep(5 * time.Second)
	size := []string{"10kb", "50kb", "100kb", "500kb", "1000kb", "1500kb", "5000kb", "10000kb", "15000kb"}

	var client = &http.Client{
		Transport: &http.Transport{
			MaxIdleConns:        100,
			MaxIdleConnsPerHost: 50,
			IdleConnTimeout:     30 * time.Second,
		},
	}
	for _, fileSize := range size {

		results := make(chan [2]int64, sampleSize)
		var wg sync.WaitGroup
		semaphore := make(chan struct{}, numThreads)

		totalTimeStart := time.Now()
		for n := 0; n < sampleSize; n++ {
			semaphore <- struct{}{} // Blockiert, wenn `numThreads` erreicht ist
			wg.Add(1)
			go func(n int) {
				defer wg.Done()
				defer func() { <-semaphore }() // Gibt einen Thread-Slot nach Abschluss frei

				var data [2]int64

				// JSON-Datei einlesen
				filePath := "test_data/" + fileSize + "_JSON.json"
				fileContent, err := os.ReadFile(filePath)
				if err != nil {
					fmt.Println("Fehler beim Lesen der JSON-Datei:", err)
					return
				}

				// JSON senden
				println("Sending Json")
				start := time.Now()
				sendJSON(client, URL, fileContent)
				timeJSON := time.Since(start).Microseconds()

				// XML-Datei einlesen
				filePath = "test_data/" + fileSize + "_XML.xml"
				xmlContent, err := os.ReadFile(filePath)
				if err != nil {
					fmt.Println("Fehler beim Lesen der XML-Datei:", err)
					return
				}

				println("Sending XML")
				// XML senden
				start = time.Now()
				sendXML(client, URL, xmlContent)
				timeXML := time.Since(start).Microseconds()

				// Ergebnisse speichern
				data[0] = timeJSON
				data[1] = timeXML
				results <- data

			}(n)
		}

		wg.Wait()
		close(results)

		totalTime := time.Since(totalTimeStart)

		// Ergebnisse aggregieren
		var jsonRes, xmlRes int64
		var aggregatedResults [][2]int64

		for data := range results {
			jsonRes += data[0]
			xmlRes += data[1]
			aggregatedResults = append(aggregatedResults, data)
		}

		//fmt.Println("Ergebnisse:", aggregatedResults)
		//fmt.Printf("Ergebnisse %s", fileSize)
		//fmt.Printf("JSON Durchschnitt: %d µs\n", jsonRes/int64(sampleSize))
		//fmt.Printf("XML Durchschnitt: %d µs\n", xmlRes/int64(sampleSize))

		// CSV-Datei schreiben
		writeCSV(aggregatedResults, totalTime.Milliseconds(), fileSize)
		time.Sleep(10 * time.Second)
	}
}

func sendJSON(client *http.Client, url string, jsonData []byte) {
	req, err := http.NewRequest("POST", url+"json", bytes.NewBuffer(jsonData))
	if err != nil {
		fmt.Println("Fehler beim Erstellen der JSON-Anfrage:", err)
		return
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		fmt.Println("Fehler beim Senden der JSON-Anfrage:", err)
	}
	defer resp.Body.Close()
}

func sendXML(client *http.Client, url string, xmlData []byte) {
	req, err := http.NewRequest("POST", url+"xml", bytes.NewBuffer(xmlData))
	if err != nil {
		fmt.Println("Fehler beim Erstellen der XML-Anfrage:", err)
		return
	}
	req.Header.Set("Content-Type", "application/xml")

	resp, err := client.Do(req)
	if err != nil {
		fmt.Println("Fehler beim Senden der XML-Anfrage:", err)
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {

		}
	}(resp.Body)
}

func writeCSV(results [][2]int64, totalTime int64, fileSize string) {
	file, err := os.Create("output/" + fileSize + "_GoResults.csv")
	if err != nil {
		fmt.Println("Error creating file:", err)
		return
	}
	defer file.Close()

	writer := csv.NewWriter(file)
	header := []string{"JsonRes", "XmlRes", "TotalTime"}
	err = writer.Write(header)
	if err != nil {
		fmt.Println("Error writing record to CSV:", err)
		return
	}
	record := []string{"NaN", "NaN", strconv.FormatInt(totalTime, 10)}

	if err := writer.Write(record); err != nil {
		fmt.Println("Error writing record to CSV:", err)
		return
	}

	for _, tuple := range results {
		record = []string{strconv.FormatInt(tuple[0], 10), strconv.FormatInt(tuple[1], 10), "NaN"}
		if err := writer.Write(record); err != nil {
			fmt.Println("Error writing record to CSV:", err)
			return
		}
	}

	writer.Flush()
	fmt.Println("Data saved as CSV!")
}
