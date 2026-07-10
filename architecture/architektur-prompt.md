\# Master-Prompt: Architektur und funktionaler Basis-Scaffold einer Fitness-Plattform



Du arbeitest als autonomer Senior-Softwarearchitekt, Android-Entwickler, Backend-Entwickler, DevOps Engineer, Datenbankentwickler, Security Engineer und QA Engineer.



Deine Aufgabe ist es, ein neues Monorepository für eine langfristig erweiterbare Fitness-, Ernährungs- und Gamification-Plattform anzulegen.



Wichtig:



\* Implementiere noch nicht die vollständige Anwendung.

\* Erstelle eine saubere, funktionierende Basisarchitektur.

\* Alle angelegten Komponenten müssen real ausführbar und testbar sein.

\* Keine statischen Scheinimplementierungen, die lediglich so aussehen, als würden sie funktionieren.

\* Mock-Implementierungen sind erlaubt, müssen aber klar als Mock gekennzeichnet sein.

\* Hinterlasse einen reproduzierbar startbaren Projektstand.

\* Stelle keine unnötigen Rückfragen.

\* Triff sinnvolle technische Entscheidungen selbstständig und dokumentiere sie.

\* Verwende ausschließlich aktuelle, stabile und offiziell dokumentierte Technologien.

\* Wenn eine Technologieentscheidung nicht eindeutig ist, recherchiere die aktuelle offizielle Dokumentation und dokumentiere die Entscheidung in einem Architecture Decision Record.



\---



\# 1. Ziel dieses Auftrags



Erstelle die technische Grundlage für eine spätere Fitness-App mit folgenden langfristigen Bereichen:



\* Krafttraining

\* Trainingspläne

\* Workout-Tracking

\* Übungsbibliothek

\* Ernährung

\* Körpermessungen

\* Health Connect

\* Garmin-, YAZIO- und RENPHO-Adapter

\* Statistiken

\* XP, Level, Quests und Streaks

\* Community-Funktionen

\* Gruppen und Boss-Events

\* Commerce und freiwillige Werbung

\* optionaler KI-Helfer

\* Offline-First-Synchronisation

\* Datenschutz

\* Anti-Cheat



Diese Bereiche müssen in der Architektur berücksichtigt werden, werden in diesem Auftrag aber noch nicht vollständig implementiert.



Das Ergebnis dieses Auftrags ist:



1\. ein funktionierendes Monorepository,

2\. eine dokumentierte Architektur,

3\. ein startbares Android-Grundprojekt,

4\. ein startbares Backend,

5\. eine lokale Entwicklungsinfrastruktur,

6\. ein leicht erweiterbares Testsystem,

7\. ein kleiner funktionaler End-to-End-Slice,

8\. eine klare Roadmap für die nächsten Implementierungsphasen.



\---



\# 2. Verbindlicher Technologie-Stack



\## 2.1 Android



Verwende:



\* Kotlin

\* Jetpack Compose

\* Material 3

\* Navigation Compose

\* Coroutines

\* Flow

\* Room

\* DataStore

\* WorkManager

\* Hilt

\* Retrofit mit Kotlin Serialization oder Moshi

\* Android Credential Manager als vorbereitete Authentifizierungsschicht

\* Clean Architecture

\* MVVM mit unidirektionalem UI-State

\* Gradle Kotlin DSL

\* Version Catalog

\* modulare Android-Projektstruktur



Die App soll mindestens Android 9 unterstützen, sofern aktuelle zwingende Abhängigkeiten keinen höheren Mindestwert erfordern.



Dokumentiere die tatsächliche `minSdk`-Entscheidung.



\## 2.2 Backend



Verwende:



\* Python

\* FastAPI

\* Pydantic

\* SQLAlchemy 2

\* Alembic

\* PostgreSQL

\* Redis

\* pytest

\* httpx für API-Tests

\* Ruff

\* mypy

\* strukturierte Logs

\* Docker

\* Docker Compose



Verwende einen modularen Monolithen.



Asynchrone Datenbankzugriffe dürfen verwendet werden, wenn sie konsistent und gut testbar umgesetzt werden.



\## 2.3 Infrastruktur



Erstelle:



\* lokale Entwicklungsumgebung

\* Testkonfiguration

\* Docker Compose

\* PostgreSQL-Service

\* Redis-Service

\* Backend-Service

\* `.env.example`

\* Secret-Abstraktion

\* Health Checks

\* CI-Pipeline

\* reproduzierbare Build- und Testbefehle



Keine Secrets dürfen im Repository gespeichert werden.



\---



\# 3. Repository-Struktur



Erstelle ein Monorepository mit einer sinnvollen Struktur ähnlich:



```text

fitness-platform/

├── android/

├── backend/

├── admin/

├── shared/

├── data/

│   ├── exercises/

│   ├── equipment/

│   ├── muscles/

│   └── licenses/

├── docs/

│   ├── adr/

│   ├── architecture/

│   ├── diagrams/

│   └── specifications/

├── infrastructure/

├── scripts/

├── tests/

├── .github/

│   └── workflows/

├── .editorconfig

├── .env.example

├── .gitignore

├── docker-compose.yml

├── Makefile

├── README.md

├── ARCHITECTURE.md

├── API.md

├── DATA\_MODEL.md

├── SECURITY.md

├── PRIVACY.md

├── INTEGRATIONS.md

├── TESTING.md

├── CONTRIBUTING.md

├── IMPLEMENTATION\_PLAN.md

├── FUTURE\_FEATURES.md

└── CHANGELOG.md

```



Die tatsächliche Struktur darf verbessert werden.



Dokumentiere jede relevante Abweichung.



\---



\# 4. Architekturprinzipien



\## 4.1 Modularer Monolith



Das Backend startet als modularer Monolith.



Bereite mindestens folgende Domänenmodule strukturell vor:



\* identity

\* user\_profile

\* onboarding

\* exercises

\* equipment

\* training\_locations

\* workout\_planning

\* workout\_execution

\* activity\_tracking

\* step\_tracking

\* nutrition

\* body\_measurements

\* health\_data

\* integrations

\* analytics

\* gamification

\* quests

\* streaks

\* boss\_events

\* groups

\* tournaments

\* social

\* moderation

\* notifications

\* commerce

\* advertising

\* administration

\* ai\_helper

\* security



Nicht alle Module müssen bereits vollständigen Code enthalten.



Für nicht implementierte Module genügen:



\* definierte Modulgrenzen,

\* eine kurze Modulbeschreibung,

\* öffentliche Interfaces,

\* geplante Abhängigkeiten,

\* keine leeren, bedeutungslosen Platzhalterdateien.



Verhindere zyklische Abhängigkeiten.



\## 4.2 Ports-and-Adapters



Externe Anbieter müssen später über Ports beziehungsweise Interfaces angebunden werden.



Bereite mindestens folgende Ports vor:



```text

HealthDataProvider

NutritionProvider

ActivityProvider

BodyMeasurementProvider

AuthenticationProvider

PaymentProvider

AdvertisementProvider

NotificationProvider

IntegrityProvider

AiProvider

```



Erstelle für die Basisversion kleine Mock-Implementierungen für mindestens:



```text

MockHealthDataProvider

MockNutritionProvider

MockActivityProvider

MockBodyMeasurementProvider

MockAuthenticationProvider

```



Mocks müssen deterministisch und für automatisierte Tests geeignet sein.



Die Domainlogik darf nicht direkt von konkreten externen Anbietern abhängen.



\## 4.3 Domain Events



Bereite eine einfache interne Domain-Event-Infrastruktur vor.



Unterstütze zunächst mindestens:



```text

GuestProfileCreated

WorkoutCreated

WorkoutStarted

WorkoutCompleted

ExerciseCreated

SyncOperationQueued

SyncOperationCompleted

```



Die Infrastruktur muss:



\* typisiert sein,

\* erweiterbar sein,

\* testbar sein,

\* eine Event-ID besitzen,

\* einen Zeitstempel besitzen,

\* Idempotenz vorbereiten,

\* Eventhandler registrieren können.



Es ist noch kein komplexer externer Message Broker erforderlich.



Verwende zunächst einen In-Process Event Dispatcher mit klarer Austauschmöglichkeit.



\## 4.4 Offline-First



Die Android-App muss grundsätzlich offline-first aufgebaut werden.



Room ist die lokale Source of Truth für die in diesem Auftrag implementierten lokalen Daten.



Bereite folgende Konzepte vor:



\* lokale IDs als UUIDs

\* Sync-Status

\* Outbox-Tabelle

\* Retry-Zähler

\* Zeitstempel

\* Fehlerstatus

\* Server-ID optional

\* Conflict-Version optional

\* WorkManager-Synchronisationsjob

\* austauschbarer Sync-Client



Unterstützte Sync-Status:



```text

LOCAL\_ONLY

PENDING

SYNCING

SYNCED

FAILED

CONFLICT

```



Es ist noch keine vollständige Konfliktauflösung erforderlich.



Dokumentiere aber die geplante Konfliktstrategie.



\---



\# 5. Android-Modularisierung



Erstelle eine klare modulare Android-Struktur.



Beispiel:



```text

android/

├── app/

├── build-logic/

├── core/

│   ├── common/

│   ├── model/

│   ├── database/

│   ├── datastore/

│   ├── network/

│   ├── designsystem/

│   ├── testing/

│   └── sync/

├── domain/

│   ├── identity/

│   ├── exercises/

│   └── workouts/

├── data/

│   ├── identity/

│   ├── exercises/

│   └── workouts/

└── feature/

&#x20;   ├── home/

&#x20;   ├── guest/

&#x20;   ├── exercises/

&#x20;   ├── workout/

&#x20;   └── settings/

```



Die genaue Modulanzahl darf reduziert werden, wenn die Gradle-Struktur sonst unnötig komplex wird.



Wichtig ist eine sinnvolle Balance:



\* keine riesige monolithische `app`-Komponente,

\* aber auch keine übertriebene Modulfragmentierung.



Dokumentiere die gewählte Modulstrategie.



\---



\# 6. Backend-Schichten



Jedes tatsächlich implementierte Backend-Modul soll klar getrennt werden in:



```text

domain/

application/

infrastructure/

presentation/

```



Alternativ darf eine ähnlich klare Struktur verwendet werden.



Regeln:



\* Domainmodelle kennen FastAPI nicht.

\* Domainmodelle kennen SQLAlchemy nicht direkt, sofern eine saubere Trennung realistisch möglich ist.

\* API-Endpunkte enthalten keine komplexe Geschäftslogik.

\* Repositories werden über Interfaces abstrahiert.

\* Transaktionen werden kontrolliert verwaltet.

\* Pydantic-Schemas und Datenbankmodelle sind nicht automatisch dieselben Objekte.

\* Abhängigkeiten werden explizit injiziert.



\---



\# 7. Funktionaler Basis-Slice



Implementiere genau einen kleinen, aber vollständigen vertikalen Slice.



\## 7.1 Gastprofil



Beim ersten Start kann lokal ein Gastprofil angelegt werden.



Das Gastprofil enthält mindestens:



\* UUID

\* Anzeigename

\* Erstellungszeitpunkt

\* Einheitensystem

\* Onboarding-Status

\* Sync-Status



Das Profil muss:



\* lokal gespeichert werden,

\* nach einem App-Neustart weiterhin vorhanden sein,

\* bearbeitet werden können,

\* ohne Backend funktionieren.



\## 7.2 Eigene Übung



Ein Gastnutzer kann eine eigene Übung anlegen.



Eine eigene Übung enthält mindestens:



\* UUID

\* Name

\* Beschreibung

\* primäre Muskelgruppe

\* benötigtes Gerät

\* Trackingtyp

\* Notizen

\* Erstellungszeitpunkt

\* Änderungszeitpunkt

\* Sync-Status



Unterstütze:



\* erstellen

\* anzeigen

\* bearbeiten

\* löschen

\* lokale Persistenz

\* Validierung

\* Fehlerzustände

\* Compose-UI

\* ViewModel

\* Use Case

\* Repository

\* Room-DAO

\* Tests



\## 7.3 Einfaches Workout



Ein Nutzer kann ein minimales Workout anlegen.



Ein Workout enthält:



\* UUID

\* Titel

\* Status

\* Startzeit

\* Endzeit optional

\* Notizen

\* enthaltene Übungen

\* Sync-Status



Unterstütze:



\* Workout erstellen

\* Workout starten

\* Workout abschließen

\* Workout-Liste anzeigen

\* Daten lokal speichern



Beim Abschluss wird lokal das Domain Event `WorkoutCompleted` erzeugt.



Es ist noch keine XP-Vergabe erforderlich.



\## 7.4 Backend-Synchronisationsgrundlage



Erstelle passende Backend-Endpunkte für:



\* Health Check

\* Gastprofil registrieren oder übernehmen

\* eigene Übungen synchronisieren

\* Workouts synchronisieren



Die Sync-Logik muss einfach gehalten werden.



Für diesen Auftrag genügt:



\* Push lokaler Datensätze,

\* Upsert über UUID,

\* idempotente Requests,

\* serverseitige Zeitstempel,

\* klare API-Fehler,

\* Authentifizierung für Gastmodus über ein einfaches temporäres Gast-Token.



Das Gast-Token-System ist ausdrücklich nur eine Entwicklungsgrundlage.



Kennzeichne es klar als nicht produktionsfertig.



\---



\# 8. API-Grundlagen



Verwende versionierte Routen:



```text

/api/v1/

```



Implementiere mindestens:



```text

GET  /health

POST /api/v1/guest-sessions

GET  /api/v1/profile

PUT  /api/v1/profile

GET  /api/v1/exercises

POST /api/v1/exercises

PUT  /api/v1/exercises/{exercise\_id}

DELETE /api/v1/exercises/{exercise\_id}

GET  /api/v1/workouts

POST /api/v1/workouts

PUT  /api/v1/workouts/{workout\_id}

POST /api/v1/sync/push

```



Erstelle:



\* OpenAPI-Dokumentation

\* Request- und Response-Schemas

\* Fehlerformat

\* Request-ID

\* strukturierte Validierungsfehler

\* Pagination-Grundlage

\* API-Versionierung

\* Idempotency-Key-Unterstützung für relevante Schreiboperationen



Definiere ein einheitliches Fehlerformat, zum Beispiel:



```json

{

&#x20; "error": {

&#x20;   "code": "VALIDATION\_ERROR",

&#x20;   "message": "The request contains invalid data.",

&#x20;   "details": \[],

&#x20;   "request\_id": "..."

&#x20; }

}

```



\---



\# 9. Datenbankgrundlage



Erstelle PostgreSQL-Modelle und Alembic-Migrationen für mindestens:



\* users

\* guest\_sessions

\* profiles

\* exercises

\* workouts

\* workout\_exercises

\* outbox\_events

\* idempotency\_records



Verwende:



\* UUIDs

\* UTC-Zeitstempel

\* sinnvolle Constraints

\* Foreign Keys

\* Indizes

\* Soft Delete nur dort, wo sinnvoll

\* eindeutige Idempotency-Constraints



Dokumentiere das Datenmodell in `DATA\_MODEL.md`.



Erstelle ein Mermaid-ER-Diagramm.



\---



\# 10. Testing-System



Das Testsystem ist ein zentraler Bestandteil dieses Auftrags.



Es muss leicht erweiterbar sein und klare Konventionen besitzen.



\## 10.1 Backend-Tests



Richte ein:



\* Unit Tests

\* Application-Service-Tests

\* Repository-Tests

\* API-Tests

\* Datenbankintegrationstests

\* Migrationstests

\* Domain-Event-Tests

\* Idempotenztests

\* Contract-Tests für Provider-Interfaces



Verwende:



\* pytest

\* pytest-asyncio, falls erforderlich

\* httpx

\* Test Fixtures

\* Factory-Funktionen

\* Testdaten-Builder

\* isolierte Testdatenbank

\* transaktionale Tests oder kurzlebige Testcontainer

\* Coverage-Bericht



Erstelle mindestens Tests für:



\* Gastprofil anlegen

\* doppelte Gastprofilanfrage

\* Übung erstellen

\* ungültige Übung ablehnen

\* Übung aktualisieren

\* Übung löschen

\* Workout erstellen

\* Workout abschließen

\* `WorkoutCompleted` wird genau einmal ausgelöst

\* wiederholter Idempotency-Key erzeugt keine Duplikate

\* Datenbankmigrationen können auf leerer Datenbank ausgeführt werden



\## 10.2 Android-Tests



Richte ein:



\* Unit Tests

\* ViewModel Tests

\* Use-Case-Tests

\* Repository-Tests

\* Room-Tests

\* Compose UI Tests

\* Navigationstests

\* Sync-Queue-Tests



Erstelle ein eigenes Test-Utility-Modul oder Paket mit:



\* Fake Repositories

\* Fake Clock

\* Fake UUID Provider

\* Test Dispatchers

\* Testdaten-Builder

\* deterministischen Provider-Mocks



Erstelle mindestens Tests für:



\* Gastprofil wird gespeichert

\* Gastprofil bleibt nach Neustart vorhanden

\* eigene Übung kann erstellt werden

\* leere Übungsnamen werden abgelehnt

\* Übung kann bearbeitet werden

\* Übung kann gelöscht werden

\* Workout kann gestartet werden

\* Workout kann abgeschlossen werden

\* Sync-Outbox-Eintrag wird erzeugt

\* ViewModel zeigt Lade-, Erfolgs- und Fehlerzustände

\* grundlegende Compose-Navigation funktioniert



\## 10.3 End-to-End-Testgrundlage



Lege eine E2E-Struktur an.



Implementiere mindestens einen automatisierten oder klar ausführbaren Integrationsablauf:



```text

Gastprofil lokal erstellen

→ eigene Übung anlegen

→ Workout erstellen

→ Workout abschließen

→ Sync an Backend senden

→ Datensatz im Backend prüfen

```



Falls ein vollständiger Android-E2E-Test in der aktuellen Umgebung nicht zuverlässig ausführbar ist, implementiere:



\* Android-Integrationstests bis zur Netzwerkgrenze,

\* Backend-E2E-Tests,

\* ein dokumentiertes manuelles Smoke-Test-Skript.



\## 10.4 Testkonventionen



Dokumentiere:



\* Namenskonventionen

\* Testpyramide

\* Fixture-Strategie

\* Mock-vs.-Fake-Regeln

\* Provider-Contract-Tests

\* Umgang mit Zeit

\* Umgang mit UUIDs

\* Umgang mit Coroutines

\* Datenbankisolation

\* Mindest-Coverage-Ziele

\* CI-Ausführung



Zielwerte:



\* hohe Coverage für Domain- und Application-Code,

\* keine künstliche Coverage-Optimierung für triviale Konfigurationsdateien,

\* kritische Geschäftslogik möglichst vollständig testen.



\---



\# 11. CI und Qualitätskontrolle



Erstelle eine CI-Pipeline, die mindestens ausführt:



\## Backend



```text

ruff format --check

ruff check

mypy

pytest

Alembic migration check

```



\## Android



```text

Gradle dependency validation

ktlint oder Spotless

Detekt

Unit Tests

Android Lint

Debug Build

```



\## Repository



```text

Secret Scan

Dependency Scan

Docker Build

Dokumentationsprüfung

```



Die Pipeline darf sinnvoll auf mehrere Jobs aufgeteilt werden.



Nutze Caching.



CI muss bei Fehlern korrekt fehlschlagen.



\---



\# 12. Entwicklungsbefehle



Erstelle einen einfachen Einstieg über `Makefile`, Skripte oder eine vergleichbare Lösung.



Mindestens:



```bash

make setup

make dev

make backend

make backend-test

make backend-lint

make android-test

make android-build

make test

make lint

make migrate

make seed

make clean

```



Unter Windows muss die Nutzung dokumentiert werden.



Falls `make` unter Windows nicht vorausgesetzt werden soll, stelle PowerShell-Alternativen oder plattformunabhängige Skripte bereit.



\---



\# 13. Seed- und Demo-Daten



Erstelle kleine, eindeutig gekennzeichnete Demo-Daten:



\* Gastprofil

\* Muskelgruppen

\* Geräte

\* 10 bis 20 einfache Beispielübungen

\* zwei Beispielworkouts



Die Übungsdaten dürfen für diesen Scaffold selbst formuliert sein.



Keine fremden Bilder oder Videos verwenden.



Kennzeichne sie als technische Demo-Daten und nicht als fachlich geprüfte Trainingsbibliothek.



\---



\# 14. Security-Grundlage



Implementiere bereits:



\* keine Secrets im Repository

\* sichere Konfiguration

\* Request IDs

\* strukturierte Logs

\* Eingabevalidierung

\* sichere Fehlerausgaben

\* Rate-Limit-Grundlage

\* Idempotency-Key-Grundlage

\* sichere Passwortarchitektur vorbereiten, aber noch keinen vollständigen E-Mail-Login

\* klare Trennung zwischen Gast- und zukünftiger Benutzer-Authentifizierung

\* keine sensiblen Daten in Logs

\* CORS-Konfiguration

\* sichere Standardwerte

\* Dependency Scanning



Erstelle `SECURITY.md` mit einem ersten Threat Model.



Berücksichtige mindestens:



\* manipulierte API-Anfragen

\* Replay-Angriffe

\* doppelte Sync-Anfragen

\* manipulierte lokale Daten

\* gestohlene Gast-Tokens

\* Massenzugriffe

\* Injection

\* unsichere Logs

\* geleakte Secrets



Die vollständige Play-Integrity- und Anti-Cheat-Implementierung ist nicht Teil dieses Auftrags.



Bereite dafür lediglich klare Schnittstellen und Erweiterungspunkte vor.



\---



\# 15. Dokumentation



Erstelle mindestens:



\## README.md



Enthält:



\* Projektziel

\* aktueller Implementierungsstand

\* Architekturüberblick

\* Voraussetzungen

\* Schnellstart

\* Docker-Start

\* Backend-Start

\* Android-Start

\* Testbefehle

\* Demo-Ablauf

\* bekannte Einschränkungen

\* nächste Schritte



\## ARCHITECTURE.md



Enthält:



\* Systemkontext

\* Container

\* Android-Architektur

\* Backend-Architektur

\* Modulgrenzen

\* Datenflüsse

\* Offline-First-Strategie

\* Sync-Outbox

\* Domain Events

\* Ports-and-Adapters

\* Mermaid-Diagramme

\* geplante Erweiterungen



\## API.md



Enthält:



\* API-Konventionen

\* Auth-Grundlage

\* Fehlerformat

\* Versionierung

\* Idempotenz

\* Endpunktübersicht



\## DATA\_MODEL.md



Enthält:



\* Entitäten

\* Beziehungen

\* Constraints

\* Indizes

\* Löschregeln

\* Mermaid-ER-Diagramm



\## TESTING.md



Enthält:



\* Teststrategie

\* Testpyramide

\* Backend-Tests

\* Android-Tests

\* E2E

\* Fixtures

\* Fakes

\* Contract-Tests

\* Coverage

\* CI

\* neue Tests hinzufügen



\## INTEGRATIONS.md



Enthält vorbereitete Ports für:



\* Health Connect

\* Garmin

\* YAZIO

\* RENPHO

\* Payment

\* Ads

\* Notifications

\* AI



Kennzeichne klar:



\* noch nicht implementiert,

\* Mock vorhanden,

\* späterer offizieller Provider,

\* keine inoffiziellen APIs.



\## IMPLEMENTATION\_PLAN.md



Erstelle eine realistische Roadmap:



```text

Phase 1: Basisarchitektur

Phase 2: Übungsbibliothek und Trainingspläne

Phase 3: vollständiger Workout-Modus

Phase 4: Offline-Synchronisation und Konten

Phase 5: Health Connect und Körperdaten

Phase 6: Ernährung und Statistiken

Phase 7: Gamification

Phase 8: Social, Gruppen und Boss-Events

Phase 9: Commerce, Ads und KI

Phase 10: Security, Datenschutz und Release

```



Teile die Phasen in kleine vertikale Slices mit Definition of Done.



\## Architecture Decision Records



Erstelle mindestens:



```text

ADR-001 Monorepository

ADR-002 Modularer Monolith

ADR-003 Android MVVM und unidirektionaler State

ADR-004 Room als lokale Source of Truth

ADR-005 Outbox-Synchronisation

ADR-006 FastAPI und SQLAlchemy

ADR-007 Provider-Ports

ADR-008 Teststrategie

ADR-009 UUID- und Zeitstrategie

ADR-010 Gast-Authentifizierungsgrundlage

```



\---



\# 16. Nicht Teil dieses Auftrags



Folgende Funktionen sollen noch nicht vollständig implementiert werden:



\* vollständige Google-Anmeldung

\* E-Mail-Registrierung

\* Health Connect Produktionseinbindung

\* echte Garmin-Integration

\* echte YAZIO-Integration

\* echte RENPHO-Integration

\* vollständige Ernährungsdatenbank

\* vollständige Trainingsplanlogik

\* XP- und Levelsystem

\* Quests

\* Streaks

\* Long-Term Streak Freeze

\* Achievements

\* Boss-Events

\* Freunde

\* Gruppen

\* Social Posts

\* Moderation

\* Gruppenturniere

\* In-App-Shop

\* Google Play Billing

\* Rewarded Ads

\* KI-Helfer

\* Play Integrity

\* vollständiges Admin-Portal



Für diese Funktionen sollen nur:



\* Modulgrenzen,

\* Ports,

\* dokumentierte Erweiterungspunkte,

\* gegebenenfalls minimale Contract-Mocks



angelegt werden.



Keine dieser Funktionen darf als „fertig“ bezeichnet werden.



\---



\# 17. Definition of Done



Der Auftrag ist erst abgeschlossen, wenn:



\* das Repository vollständig angelegt wurde,

\* Android-Projekt synchronisiert und gebaut werden kann,

\* Android-Unit-Tests erfolgreich laufen,

\* mindestens grundlegende Compose-UI-Tests vorhanden sind,

\* Backend lokal gestartet werden kann,

\* Docker Compose funktioniert,

\* PostgreSQL und Redis erreichbar sind,

\* Alembic-Migrationen funktionieren,

\* Backend-Tests erfolgreich laufen,

\* Linting erfolgreich läuft,

\* Typprüfung erfolgreich läuft,

\* Gastprofil lokal funktioniert,

\* eigene Übungen lokal funktionieren,

\* einfaches Workout lokal funktioniert,

\* Backend-Sync-Endpunkte funktionieren,

\* mindestens ein Sync-Ablauf getestet wurde,

\* Domain Events getestet wurden,

\* Idempotenz getestet wurde,

\* CI eingerichtet wurde,

\* keine Secrets im Repository liegen,

\* Dokumentation vollständig ist,

\* alle Mock-Komponenten klar gekennzeichnet sind,

\* bekannte Einschränkungen dokumentiert sind.



\---



\# 18. Arbeitsweise



Arbeite in dieser Reihenfolge:



\## Schritt 1: Planung



\* Analysiere die Anforderungen.

\* Lege die Architektur fest.

\* Erstelle ADRs.

\* Definiere Modulgrenzen.

\* Erstelle eine kurze interne Umsetzungsreihenfolge.



\## Schritt 2: Repository und Tooling



\* Monorepository

\* Git-Konfiguration

\* EditorConfig

\* Gradle

\* Python-Projekt

\* Docker Compose

\* CI

\* Linting

\* Testgrundlage



\## Schritt 3: Backend-Basis



\* FastAPI

\* Konfiguration

\* Logging

\* Datenbank

\* Migrationen

\* Redis

\* Health Check

\* Fehlerformat

\* Gast-Session

\* Profile

\* Übungen

\* Workouts

\* Sync



\## Schritt 4: Android-Basis



\* Gradle-Module

\* Compose

\* Navigation

\* Designsystem

\* Room

\* DataStore

\* Retrofit

\* Hilt

\* Gastprofil

\* Übungen

\* Workout

\* Sync-Outbox



\## Schritt 5: Tests



\* Backend Unit Tests

\* Backend Integration Tests

\* Android Unit Tests

\* Room Tests

\* ViewModel Tests

\* Compose Tests

\* Contract Tests

\* E2E-Grundlage



\## Schritt 6: Dokumentation und Prüfung



\* alle Builds ausführen

\* alle Tests ausführen

\* Linter ausführen

\* Migrationen prüfen

\* Docker-Start prüfen

\* Dokumentation aktualisieren

\* tote Dateien entfernen

\* bekannte Einschränkungen dokumentieren



\---



\# 19. Abschlussbericht



Erstelle am Ende eine Datei:



```text

docs/IMPLEMENTATION\_REPORT.md

```



Diese enthält:



\* umgesetzte Bestandteile

\* bewusst nicht umgesetzte Bestandteile

\* Architekturentscheidungen

\* Repository-Struktur

\* verwendete Technologien

\* ausgeführte Befehle

\* Testergebnisse

\* Build-Ergebnisse

\* Migrationsstatus

\* Coverage-Ergebnisse

\* bekannte Einschränkungen

\* Sicherheitsstatus

\* Mock-Provider

\* nächste empfohlene Schritte

\* konkrete Codex-Aufträge für die nächsten drei vertikalen Slices



Gib keine erfolgreichen Tests oder Builds an, die nicht tatsächlich ausgeführt wurden.



Wenn etwas in der Umgebung nicht ausgeführt werden konnte:



\* erkläre den Grund,

\* dokumentiere den erwarteten Befehl,

\* liefere keine erfundenen Ergebnisse.



\---



\# 20. Ausgabeanforderung



Erstelle sämtliche Projektdateien direkt.



Liefere keine bloße theoretische Beschreibung.



Das Endergebnis soll als ZIP-Datei mit dem vollständigen Repository bereitgestellt werden.



Das ZIP muss nach dem Entpacken direkt als Git-Repository initialisierbar sein.



Füge keine generierten Build-Artefakte, virtuellen Umgebungen, lokalen Datenbanken oder Secrets in das ZIP ein.



Das Repository muss nach Möglichkeit mit folgenden Schritten startbar sein:



```bash

cp .env.example .env

docker compose up --build

```



und:



```bash

make test

```



Dokumentiere alternative Befehle für Windows PowerShell.



