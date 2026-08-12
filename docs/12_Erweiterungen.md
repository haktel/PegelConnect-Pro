# 12 - Erweiterungen

PegelConnect Pro enthält bewusst Funktionen über eine minimale Pegelanzeige hinaus.

## Bereits umgesetzt

- Open-Meteo Wetterdaten
- Kartenansicht mit Leaflet / OpenStreetMap
- Stadtwappen
- historische Pegelkurven 24h / 7d / 30d
- statistische Reports
- CSV Export
- JSON Export
- PDF Report
- Event Log im Dashboard
- externe Laufzeitkonfiguration
- NGINX Reverse Proxy
- Test- und Recovery-Szenarien

## Sinnvolle nächste Verbesserungen

### Deduplication

In-Memory-History sollte identische `station + timestamp` Messungen nur einmal speichern.

### Persistente Logs

Systemereignisse aus dem Browser könnten zusätzlich serverseitig gespeichert werden.

### Automatisierte Test-Skripte

Die manuell ausgeführten T01-T14 können als PowerShell-/Shell-Test-Suite automatisiert werden.

### Unit Tests

JUnit-Tests für Config Parsing, Trendlogik, Validierung und Fehlerfälle.

### CI

GitHub Actions für Maven Build und Tests.

Diese Punkte sind Verbesserungen, keine Voraussetzung für den aktuellen funktionsfähigen Stand.
