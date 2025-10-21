package main

import (
	"encoding/json"
	"encoding/xml"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"

	"github.com/gorilla/mux"
)

const (
	numWorkers = 100 // Anzahl der Worker-Threads
)

type TestData struct {
	XMLName xml.Name `xml:"Ableton"`
}

var jsonJobs = make(chan []byte, 100)
var xmlJobs = make(chan []byte, 100)

func main() {
	r := mux.NewRouter()
	r.HandleFunc("/json", jsonHandler).Methods("POST")
	r.HandleFunc("/xml", xmlHandler).Methods("POST")

	// Worker-Pool starten
	for i := 0; i < numWorkers; i++ {
		go jsonWorker(i)
		go xmlWorker(i)
	}

	addr := "0.0.0.0:3000"
	fmt.Println("Server läuft unter http://", addr)

	srv := &http.Server{
		Handler:      r,
		Addr:         addr,
		WriteTimeout: 15 * time.Second,
		ReadTimeout:  15 * time.Second,
	}

	log.Fatal(srv.ListenAndServe())
}

func jsonHandler(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Fehler beim Lesen des JSON-Requests", http.StatusBadRequest)
		return
	}
	r.Body.Close()

	select {
	case jsonJobs <- body: // Jetzt wird nur der gelesene Body in den Channel gepackt
		w.WriteHeader(http.StatusAccepted)
	default:
		http.Error(w, "Server überlastet", http.StatusServiceUnavailable)
	}
}

// Worker für JSON-Verarbeitung
func jsonWorker(id int) {
	for body := range jsonJobs {
		handleJSON(body)
	}
}

// JSON-Logik
func handleJSON(body []byte) {
	var payload map[string]interface{}
	if err := json.Unmarshal(body, &payload); err != nil {
		fmt.Println("Fehler beim Parsen von JSON:", err)
		return
	}
	//fmt.Println("JSON erfolgreich verarbeitet:")
}

// Handler fügt Anfrage in die XML-Warteschlange
func xmlHandler(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Fehler beim Lesen des XML-Requests", http.StatusBadRequest)
		return
	}
	r.Body.Close()

	select {
	case xmlJobs <- body: // Anfrage wird in den Worker-Pool gegeben
		w.WriteHeader(http.StatusAccepted)
	default:
		http.Error(w, "Server überlastet", http.StatusServiceUnavailable)
	}
}

// Worker für XML-Verarbeitung
func xmlWorker(id int) {
	for body := range xmlJobs {
		handleXML(body)
	}
}

// XML-Logik
func handleXML(body []byte) {
	var payload TestData
	if len(body) == 0 {
		fmt.Println("Fehler: XML-Body ist leer")
		return
	}
	if err := xml.Unmarshal(body, &payload); err != nil {
		fmt.Println("Fehler beim Parsen von XML:", err)
		return
	}
	//fmt.Println("XML erfolgreich verarbeitet:")
}
