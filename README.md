# Arbeitszeiterfassung App

Diese schlichte Android-App dient zur Erfassung von Arbeitszeiten und Pausen, die nahtlos mit dem [Backend](https://github.com/flobaerde/timetracker-backend) synchronisiert werden.

## 🚀 Funktionen

- **Arbeitszeiterfassung**: Starten, Stoppen und Bearbeiten von Arbeitsschichten.
- **Pausenmanagement**: Einfaches Erfassen von Pausen während der Arbeitszeit.
- **Standortbezogen**: Zuordnung von Arbeitszeiten zu verschiedenen Standorten.
- **Offline-First**: Alle Daten werden lokal gespeichert und bei bestehender Internetverbindung automatisch synchronisiert.
- **Hintergrund-Synchronisation**: Ein dedizierter Worker sorgt für den Datenaustausch mit dem Backend (alle 15 Minuten oder bei Bedarf).
- **Sichere Authentifizierung**: Token-basierte Anmeldung und Kommunikation.

## 🛠 Technischer Stack

- **Programmiersprache**: [Kotlin](https://kotlinlang.org/)
- **UI-Framework**: [Jetpack Compose](https://developer.android.com/jetcompose) für ein modernes, deklaratives UI.
- **Architektur**: MVVM (Model-View-ViewModel) mit Repository-Pattern.
- **Datenbank**: [Room](https://developer.android.com/training/data-storage/room) für die lokale Datenhaltung.
- **Netzwerk**: [Retrofit](https://square.github.io/retrofit/) & OkHttp für die API-Kommunikation.
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) für saubere Entkopplung.
- **Hintergrundaufgaben**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) für die Datensynchronisation.
- **Reaktive Programmierung**: Kotlin Coroutines und Flow.

## 🔄 Zusammenspiel mit dem Backend

Die App ist so konzipiert, dass sie auch ohne permanente Internetverbindung voll funktionsfähig bleibt.

1.  **Lokale Speicherung**: Jede Aktion (Arbeitszeit starten, Pause hinzufügen etc.) wird sofort in der lokalen Room-Datenbank gespeichert.
2.  **Synchronisations-Status**: Einträge erhalten einen Status (`SyncStatus`), der angibt, ob sie bereits mit dem Server abgeglichen wurden.
3.  **Automatischer Sync**: Der `SyncWorker` prüft regelmäßig (alle 15 Minuten) auf nicht synchronisierte Daten und überträgt diese an das Backend.
4.  **Konfliktlösung**: Das Repository verwaltet den Abgleich zwischen lokalen Änderungen und Remote-Daten, um Konsistenz zu gewährleisten.

### API-Schnittstellen (Auszug)
Die Kommunikation erfolgt über eine REST-API:
- `GET /worktimes`: Abrufen vorhandener Arbeitszeiten.
- `POST /worktimes`: Starten einer neuen Schicht.
- `PUT /worktimes/{id}`: Stoppen oder Aktualisieren einer Schicht.
- `POST /pauses`: Erfassen von Pausenzeiten.


## 📄 Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE) (oder siehe LICENSE-Datei im Root-Verzeichnis).
