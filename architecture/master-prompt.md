\# Master-Prompt: Entwicklung einer vollständigen Fitness-, Ernährungs- und Gamification-App



Du arbeitest als autonomer Senior-Softwarearchitekt, Android-Entwickler, Backend-Entwickler, UX-Designer, Datenbankentwickler, Security Engineer, QA Engineer und technischer Dokumentationsautor.



Deine Aufgabe ist es, eine vollständige, wartbare und testbare Fitness-Tracking-App mit Android-Client, Backend, lokaler Offline-Datenbank, Synchronisation, Fitnessdaten-Integrationen, Gamification, Community-Funktionen und vorbereitetem Monetarisierungssystem zu planen und schrittweise zu implementieren.



Die Anwendung ist zunächst eine kostenlose Testversion. Alle nachfolgend als Pflichtumfang beschriebenen Funktionen müssen implementiert werden. Sie dürfen nicht lediglich in eine Future-Features-Liste verschoben werden.



Das separate Dokument `FUTURE\_FEATURES.md` darf ausschließlich zusätzliche Erweiterungen enthalten, die über den hier definierten Pflichtumfang hinausgehen.



\---



\# 1. Arbeitsweise



Arbeite weitgehend autonom.



Stelle keine unnötigen Rückfragen. Wenn eine technische Entscheidung nicht eindeutig vorgegeben ist:



1\. recherchiere aktuelle offizielle Dokumentationen,

2\. dokumentiere die getroffene Entscheidung,

3\. wähle die wartbarste, sicherste und realistisch umsetzbare Lösung,

4\. implementiere sie,

5\. ergänze Tests und Dokumentation.



Gehe iterativ vor, aber hinterlasse nach jeder Phase einen lauffähigen Stand.



Keine Funktion darf nur durch statische UI-Mockups vorgetäuscht werden. Noch nicht extern freigeschaltete Integrationen müssen über funktionsfähige Adapter, Mock-Provider und klar dokumentierte Austauschpunkte testbar sein.



\---



\# 2. Produktziel



Entwickle eine Android-Fitness-App, die folgende Bereiche kombiniert:



\* Krafttraining und Trainingsplanung

\* Ausdauer- und Aktivitätstracking

\* Schrittzählung

\* Ernährung und Kalorien

\* Körpergewicht und Körperzusammensetzung

\* Garmin-, YAZIO-, RENPHO- und Health-Connect-Integration

\* Statistiken und Fortschrittsanalysen

\* Freunde, Trainingsgruppen und soziale Motivation

\* XP, Level, Quests, Streaks und Achievements

\* globale Community-Bosse

\* Gruppenligen und Gruppenturniere

\* In-App-Währung und kosmetische Belohnungen

\* freiwillige Werbung

\* vorbereitete spätere Käufe und Abonnements

\* optionaler KI-Helfer

\* Offline-Nutzung

\* Datenschutz und Anti-Cheat



Die Anwendung soll langfristig über Google Play veröffentlicht werden können.



\---



\# 3. Verbindlicher Technologie-Stack



Verwende einen modernen, wartbaren und klar modularisierten Stack.



\## 3.1 Android-App



Bevorzugte Technologien:



\* Kotlin

\* Jetpack Compose

\* Material 3

\* Clean Architecture

\* MVVM oder MVI mit klar dokumentierter Entscheidung

\* Kotlin Coroutines

\* Flow

\* Room

\* WorkManager

\* Android Credential Manager

\* Health Connect SDK

\* Retrofit oder Ktor Client

\* Hilt oder Koin

\* DataStore

\* Navigation Compose

\* Android Keystore

\* Play Integrity API

\* Google Play Billing als vorbereitete Monetarisierungsschicht

\* Firebase Cloud Messaging oder austauschbarer Push-Adapter



Die App muss mindestens Android 9 unterstützen, sofern keine zwingende aktuelle Abhängigkeit einen höheren Mindestwert erfordert. Health Connect muss je nach Android-Version korrekt behandelt werden.



\## 3.2 Backend



Bevorzugte Technologien:



\* Python

\* FastAPI

\* Pydantic

\* SQLAlchemy

\* Alembic

\* PostgreSQL

\* Redis für Caching, Rate Limits, Leaderboards und Jobs

\* Celery, Dramatiq oder eine vergleichbare Job-Queue

\* Docker

\* Docker Compose

\* OpenAPI

\* strukturierte Logs

\* pytest

\* asynchrone Verarbeitung, wo sinnvoll



Alternativtechnologien dürfen nur verwendet werden, wenn sie objektiv besser geeignet sind und die Entscheidung in einem Architecture Decision Record dokumentiert wird.



\## 3.3 Infrastruktur



Bereite folgende Umgebungen vor:



\* lokale Entwicklung

\* automatisierte Tests

\* Staging

\* Production



Keine geheimen Schlüssel oder Zugangsdaten dürfen im Repository gespeichert werden.



Verwende:



\* `.env.example`

\* Secret-Abstraktion

\* unterschiedliche Konfigurationen je Umgebung

\* reproduzierbare Docker-Umgebung

\* CI-Pipeline

\* Linting

\* statische Analyse

\* automatisierte Tests



\---



\# 4. Repository-Struktur



Erstelle ein übersichtliches Monorepository, beispielsweise:



```text

fitness-platform/

├── android/

├── backend/

├── admin/

├── shared/

├── data/

│   ├── exercises/

│   ├── muscles/

│   ├── equipment/

│   ├── nutrition/

│   └── licenses/

├── docs/

├── infrastructure/

├── scripts/

├── tests/

├── docker-compose.yml

├── README.md

├── ARCHITECTURE.md

├── API.md

├── DATA\_MODEL.md

├── SECURITY.md

├── PRIVACY.md

├── INTEGRATIONS.md

├── GAMIFICATION.md

├── TESTING.md

├── CONTRIBUTING.md

├── FUTURE\_FEATURES.md

└── CHANGELOG.md

```



Die tatsächliche Struktur darf verbessert werden, muss aber modular und dokumentiert bleiben.



\---



\# 5. Verbindliche Architekturprinzipien



\## 5.1 Modularer Monolith



Beginne beim Backend mit einem modularen Monolithen.



Trenne mindestens folgende Domänen:



\* Identity

\* User Profile

\* Onboarding

\* Exercise Catalog

\* Equipment

\* Training Locations

\* Workout Planning

\* Workout Execution

\* Activity Tracking

\* Step Tracking

\* Nutrition

\* Body Measurements

\* Health Data

\* Integrations

\* Analytics

\* Gamification

\* Quests

\* Streaks

\* Boss Events

\* Groups

\* Tournaments

\* Social Posts

\* Moderation

\* Notifications

\* Commerce

\* Advertising Rewards

\* Administration

\* AI Helper

\* Security and Fraud Detection



Verhindere zyklische Abhängigkeiten.



\## 5.2 Ports-and-Adapters



Alle externen Anbieter müssen über Interfaces beziehungsweise Ports angebunden werden.



Beispiele:



```text

HealthDataProvider

NutritionProvider

ActivityProvider

BodyMeasurementProvider

AuthenticationProvider

PaymentProvider

AdvertisementProvider

AiProvider

NotificationProvider

IntegrityProvider

```



Implementierungen:



```text

HealthConnectProvider

GarminProvider

YazioBridgeProvider

RenphoBridgeProvider

FitbitProvider

StravaProvider

MockHealthProvider

MockNutritionProvider

MockGarminProvider

```



Die Kernlogik darf nicht direkt von einem bestimmten Anbieter abhängig sein.



\## 5.3 Ereignisbasierte Domänenlogik



Wichtige Aktionen erzeugen Domain Events:



\* WorkoutStarted

\* WorkoutCompleted

\* ExerciseSetCompleted

\* PersonalRecordReached

\* StepGoalReached

\* NutritionImported

\* BodyMeasurementImported

\* QuestCompleted

\* AchievementUnlocked

\* StreakExtended

\* StreakFrozen

\* BossDamageDealt

\* BossDefeated

\* GroupTournamentUpdated

\* RewardGranted

\* PurchaseVerified

\* SocialPostCreated



Events müssen idempotent verarbeitet werden.



XP, Währung und Belohnungen dürfen bei Wiederholung einer Anfrage niemals doppelt vergeben werden.



\---



\# 6. Anmeldung, Gastmodus und Konten



Implementiere folgende Anmeldearten:



\## 6.1 Gastmodus



Ein Nutzer kann die App ohne Registrierung ausprobieren.



Der Gastmodus muss unterstützen:



\* Trainings durchführen

\* Trainingspläne erstellen

\* eigene Übungen erstellen

\* Gewicht und Fortschritt lokal speichern

\* XP, Quests und Level lokal testen

\* grundlegende Statistiken ansehen

\* Demo-Integrationsdaten verwenden

\* App ohne vollständiges Onboarding verwenden



Gastdaten werden zunächst lokal gespeichert.



Es muss eine klare Möglichkeit geben, einen Gastaccount später in ein dauerhaftes Konto umzuwandeln.



Dabei müssen alle lokalen Daten übernommen werden:



\* Trainingspläne

\* Workouts

\* eigene Übungen

\* Statistiken

\* Quests

\* XP

\* Level

\* Einstellungen

\* Gewichtsdaten



\## 6.2 Google-Login



Verwende Android Credential Manager mit Sign in with Google.



Der Backendserver muss das erhaltene Identitätstoken validieren.



\## 6.3 E-Mail-Login



Bereite zusätzlich vor:



\* Registrierung mit E-Mail und Passwort

\* E-Mail-Verifizierung

\* Passwort zurücksetzen

\* sichere Passwort-Hashing-Verfahren

\* Rate Limits

\* Schutz vor Account Enumeration



\## 6.4 Kontoverknüpfung



Ein Gastaccount muss mit Google oder E-Mail verknüpft werden können.



Doppelte Konten müssen verhindert beziehungsweise kontrolliert zusammengeführt werden.



\---



\# 7. Optionales Onboarding



Das Onboarding darf nicht verpflichtend sein.



Beim ersten Start erhält der Nutzer folgende Optionen:



\* „Profil einrichten“

\* „Später“

\* „Als Gast testen“



Ein übersprungenes Onboarding darf jederzeit über das Profil fortgesetzt werden.



Die App muss auch mit unvollständigem Profil funktionieren. Berechnungen, die fehlende Daten benötigen, müssen:



\* die fehlenden Angaben erklären,

\* eine neutrale Fallback-Annahme verwenden oder

\* die Berechnung deaktivieren.



Es darf kein Zwang entstehen, sensible Körperdaten anzugeben.



\## 7.1 Optionale Angaben



\* Alter

\* Größe

\* Gewicht

\* Aktivitätsniveau

\* Trainingsziel

\* Erfahrung

\* verfügbare Wochentage

\* bevorzugte Trainingsdauer

\* Trainingsort

\* vorhandene Geräte

\* körperliche Einschränkungen

\* Einheitensystem

\* Geschlecht oder physiologisches Berechnungsprofil



Jede Angabe muss später editierbar und löschbar sein.



\---



\# 8. Trainingsorte und Geräte



Nutzer können mehrere Trainingsorte anlegen:



\* Fitnesscenter

\* Zuhause

\* Outdoor

\* Hotel

\* Universität

\* benutzerdefinierter Ort



Pro Ort können verfügbare Geräte ausgewählt werden.



Unterstütze mindestens:



\* keine Geräte

\* Matte

\* Widerstandsbänder

\* Kurzhanteln

\* verstellbare Kurzhanteln

\* Langhantel

\* Gewichtsscheiben

\* Hantelbank

\* Squat Rack

\* Power Rack

\* Multipresse

\* Kabelzug

\* Klimmzugstange

\* Dip-Barren

\* Kettlebell

\* TRX

\* Brustpresse

\* Schulterpresse

\* Latzug

\* Rudermaschine

\* Beinpresse

\* Hackenschmidt

\* Beinstrecker

\* Beinbeuger

\* Wadenmaschine

\* Adduktorenmaschine

\* Abduktorenmaschine

\* Laufband

\* Ergometer

\* Crosstrainer

\* Ruderergometer

\* Stairmaster

\* freie Trainingsfläche



Das System muss Trainingspläne und Übungsalternativen nach vorhandenen Geräten filtern.



\---



\# 9. Übungsbibliothek



Erstelle eine lokal verfügbare, vorbefüllte Übungsdatenbank.



Die Daten müssen bereits beim ersten Start der App nutzbar sein.



\## 9.1 Datenrecherche



Recherchiere Übungen, Anatomie, Muskelgruppen, Bewegungsmuster, Trainingshinweise und Energieverbrauch ausschließlich aus:



\* seriösen wissenschaftlichen Quellen,

\* offiziellen Gesundheits- oder Sportorganisationen,

\* anerkannten Lehrwerken,

\* frei nutzbaren Datensätzen,

\* eindeutig lizenzierten offenen Quellen.



Prüfe jede Quelle auf:



\* Lizenz

\* Urheberrecht

\* Weiterverwendbarkeit

\* Namensnennung

\* Änderungsrecht

\* kommerzielle Nutzbarkeit



Speichere keine fremden urheberrechtlich geschützten Bilder oder Videos ohne passende Lizenz.



Erstelle:



```text

data/licenses/SOURCES.md

data/licenses/LICENSE\_REPORT.md

data/licenses/ATTRIBUTION.md

```



Für jeden importierten Datensatz müssen Quelle, Lizenz, Abrufdatum und Bearbeitungen nachvollziehbar sein.



\## 9.2 Übungsumfang



Liefere eine umfangreiche Startbibliothek mit Übungen für:



\* Fitnesscenter

\* Freihanteln

\* Maschinen

\* Kabelzug

\* Eigengewicht

\* Calisthenics

\* Resistance Bands

\* Kettlebells

\* Mobility

\* Stretching

\* Aufwärmen

\* Ausdauer

\* Home Workouts



\## 9.3 Übungsdaten



Jede Übung enthält mindestens:



\* ID

\* Name

\* alternative Namen

\* Beschreibung

\* Bewegungsablauf

\* primäre Muskeln

\* sekundäre Muskeln

\* Bewegungsmuster

\* notwendige Geräte

\* Schwierigkeitsgrad

\* Trainingsort

\* einseitig oder beidseitig

\* Kraft, Ausdauer oder Mobility

\* empfohlene Wiederholungsbereiche

\* empfohlene Satzbereiche

\* typische Fehler

\* Sicherheitshinweise

\* leichtere Varianten

\* schwerere Varianten

\* alternative Übungen

\* Datenquelle

\* Lizenzinformation



Videos sind für die erste Version nicht erforderlich.



Nutze für die erste Version keine fremden Übungsvideos.



\## 9.4 Anatomische Darstellung



Baue eine einfache Muskelansicht ein:



\* Vorderseite

\* Rückseite

\* primäre Muskelgruppen hervorgehoben

\* sekundäre Muskelgruppen separat markiert



Verwende eigene SVG-Grafiken oder kompatibel lizenzierte Grafiken.



\## 9.5 Eigene Übungen



Nutzer können eigene Übungen anlegen.



Eigene Übungen sind:



\* lokal gespeichert,

\* an den Account gebunden,

\* offline verfügbar,

\* bei Kontoanmeldung synchronisierbar,

\* standardmäßig privat.



Eigene Übung enthält:



\* Name

\* Beschreibung

\* primäre Muskeln

\* sekundäre Muskeln

\* Gerät

\* Trainingsort

\* Satztyp

\* Trackingtyp

\* Notizen

\* optional eigenes Bild



Kein Video-Upload ist zunächst erforderlich.



Eigene Übungen dürfen nicht ohne ausdrückliche Freigabe Teil der öffentlichen Übungsbibliothek werden.



\---



\# 10. Trainingspläne



Implementiere:



\* vorgefertigte Pläne

\* eigene Pläne

\* Bearbeiten

\* Kopieren

\* Löschen

\* Archivieren

\* Wochen duplizieren

\* Trainingstage verschieben

\* Übungen ersetzen

\* Übungen sortieren

\* Supersätze

\* Zirkel

\* Aufwärmblöcke

\* Mobility-Blöcke

\* optionale Übungen

\* Pausenzeiten

\* RPE

\* RIR

\* Satz- und Wiederholungsbereiche

\* Zielgewichte

\* Trainingstempo

\* Progressionsregeln



\## 10.1 Vorgefertigte Pläne



Liefere mindestens:



\* Anfänger-Ganzkörper ohne Geräte

\* Anfänger-Ganzkörper Fitnesscenter

\* 2-Tage-Ganzkörper

\* 3-Tage-Ganzkörper

\* Upper/Lower

\* Push/Pull/Legs

\* Kurzhanteltraining zu Hause

\* Resistance-Band-Training

\* Calisthenics-Anfänger

\* Muskelaufbau

\* allgemeine Kraft

\* Fettabbau und allgemeine Fitness

\* Mobility

\* Büro-Ausgleich

\* Laufanfänger

\* 5-km-Einstieg



Alle Pläne müssen editierbar sein.



\## 10.2 Gerätebasierte Filterung



Beim Auswählen eines Plans:



\* Trainingsort wählen

\* verfügbare Geräte laden

\* inkompatible Übungen erkennen

\* passende Alternativen vorschlagen

\* Plan automatisch als Kopie anpassen



\## 10.3 Zeitbasierte Anpassung



Unterstütze:



\* „Heute nur 15 Minuten“

\* „Heute nur 30 Minuten“

\* „Volles Training“

\* „Leichtes Training“

\* „Gerät belegt“



Die App darf dabei keine essenziellen Sicherheits- oder Aufwärmkomponenten unkommentiert entfernen.



\---



\# 11. Workout-Modus



Der Workout-Modus muss vollständig offline funktionieren.



Implementiere:



\* geplantes Workout starten

\* spontanes Workout starten

\* vergangenes Workout wiederholen

\* Übung hinzufügen

\* Übung ersetzen

\* Sätze hinzufügen

\* Aufwärmsätze

\* Arbeitssätze

\* Drop-Sets

\* Supersätze

\* Zirkel

\* Gewicht

\* Wiederholungen

\* Dauer

\* Distanz

\* Widerstand

\* RPE

\* RIR

\* Pausentimer

\* Notizen

\* Trainingsdauer

\* Trainingsvolumen

\* persönliche Rekorde

\* geschätzter Kalorienverbrauch

\* Workout abbrechen

\* Workout pausieren

\* Workout später fortsetzen



Nach Abschluss:



\* Zusammenfassung anzeigen

\* XP vergeben

\* Quests prüfen

\* Streak aktualisieren

\* Boss-Schaden berechnen

\* persönliche Rekorde anzeigen

\* optionalen Social Post anbieten

\* Daten synchronisieren



\---



\# 12. Aktivitäts- und Schritttracking



Implementiere einen optionalen Schrittzähler.



\## 12.1 Datenquellen



Priorität:



1\. Health Connect

2\. Wearable beziehungsweise verbundene App

3\. Android-Geräteschritte

4\. manueller Eintrag



Der Nutzer kann die bevorzugte Quelle auswählen.



\## 12.2 Optionen



\* Schritttracking vollständig deaktivieren

\* Tagesziel festlegen

\* Verlauf anzeigen

\* Quests mit Schritten

\* Schritt-Streak

\* Schritte aus Rankings ausschließen

\* Datenquelle anzeigen

\* Doppelerfassung verhindern



\## 12.3 Hintergrundverarbeitung



Verwende nur notwendige Berechtigungen.



Die App darf nicht dauerhaft unnötig im Hintergrund laufen.



Synchronisation und Aggregation müssen batterieeffizient erfolgen.



\---



\# 13. Health Connect



Health Connect ist die primäre Android-Datendrehscheibe.



Unterstütze, soweit verfügbar und vom Nutzer freigegeben:



\* Schritte

\* Trainingseinheiten

\* Aktivitätskalorien

\* Gesamtkalorien

\* Herzfrequenz

\* Ruhepuls

\* Schlaf

\* Gewicht

\* Körperfett

\* Körperzusammensetzung

\* Distanz

\* Geschwindigkeit

\* Ernährung

\* Flüssigkeitsaufnahme



Implementiere:



\* granulare Berechtigungen

\* Datenquellenanzeige

\* historische Imports

\* Hintergrundimporte, sofern erlaubt

\* Änderungs- und Löschsynchronisation

\* Duplikaterkennung

\* Testdaten

\* Health-Connect-Testmodus

\* klare Fehlerbehandlung



Fordere nur Berechtigungen an, die für aktivierte Funktionen benötigt werden.



\---



\# 14. YAZIO-Architektur



Baue keine unerlaubte oder reverse-engineerte YAZIO-Anbindung.



Implementiere stattdessen eine austauschbare `NutritionProvider`-Schnittstelle.



\## 14.1 Pflichtimplementierungen



\* `HealthConnectNutritionProvider`

\* `ManualNutritionProvider`

\* `MockYazioNutritionProvider`

\* vorbereiteter `OfficialYazioProvider`



\## 14.2 Verhalten



Die App soll Ernährung aus Health Connect lesen können, wenn YAZIO diese Daten dort bereitstellt.



Unterstütze:



\* Tageskalorien

\* Protein

\* Kohlenhydrate

\* Fett

\* Mahlzeiten, soweit verfügbar

\* Flüssigkeit

\* Datenquelle

\* Importzeitpunkt



Falls eine offizielle YAZIO-Partnerschnittstelle verfügbar wird, muss sie ohne Änderung der Domainlogik ergänzt werden können.



Dokumentiere in `INTEGRATIONS.md`:



\* aktueller YAZIO-Status

\* Health-Connect-Weg

\* Einschränkungen

\* keine Speicherung von YAZIO-Passwörtern

\* keine inoffizielle API als Produktionsgrundlage



\---



\# 15. Garmin-Architektur



Implementiere einen Garmin-Connector als austauschbaren Adapter.



Da produktiver API-Zugang möglicherweise noch nicht vorhanden ist, müssen vorhanden sein:



\* `GarminProvider`-Interface

\* OAuth- beziehungsweise Token-Abstraktion

\* Webhook-Verarbeitung

\* Mock-Garmin-Server

\* realistische Garmin-Testdaten

\* Integrationstests

\* dokumentierter Aktivierungsprozess



Unterstützte Domänen:



\* Schritte

\* Aktivitäten

\* Herzfrequenz

\* Schlaf

\* Stress

\* Body Battery

\* Kalorien

\* Körpergewicht

\* Körperzusammensetzung

\* Intensitätsminuten

\* Trainingsexport

\* Trainingsplanexport



Ohne echte Garmin-Zugangsdaten muss die App vollständig mit Mock-Daten testbar sein.



\---



\# 16. RENPHO und Fitnesswaagen



Baue eine allgemeine Schnittstelle für intelligente Körperwaagen.



```text

BodyMeasurementProvider

```



Implementierungen:



\* HealthConnectBodyMeasurementProvider

\* ManualBodyMeasurementProvider

\* CsvBodyMeasurementProvider

\* MockRenphoProvider

\* vorbereiteter OfficialRenphoProvider



Unterstützte Werte:



\* Gewicht

\* BMI

\* Körperfett

\* Muskelmasse

\* Körperwasser

\* Knochenmasse

\* fettfreie Masse

\* viszerales Fett

\* Grundumsatz

\* Messzeitpunkt

\* Messquelle



Kennzeichne Werte klar als:



\* direkt gemessen

\* von Gerät berechnet

\* von App geschätzt

\* manuell eingegeben



RENPHO-Daten sollen primär über Health Connect importiert werden, sofern sie dort von der RENPHO-App bereitgestellt werden.



Keine unbekannten RENPHO-Endpunkte reverse engineeren.



\---



\# 17. Weitere Anbieter



Bereite Adapter vor für:



\* Fitbit

\* Samsung Health

\* Strava

\* Polar

\* Suunto

\* COROS

\* Withings

\* Oura

\* Apple Health für eine spätere iOS-Version



Mindestens Mock-Adapter und dokumentierte Interfaces müssen vorhanden sein.



\---



\# 18. Ernährung und Kalorien



Implementiere eine Ernährungstagesübersicht.



Anzeigen:



\* Kalorienziel

\* aufgenommene Kalorien

\* Aktivitätskalorien

\* verbleibende Kalorien

\* Protein

\* Kohlenhydrate

\* Fett

\* Ballaststoffe

\* Wasser

\* Datenquelle



\## 18.1 Eigene Erfassung



Unterstütze vorerst:



\* manuellen Tageswert

\* manuelle Mahlzeit

\* wiederkehrende Mahlzeit

\* eigene Lebensmittel

\* Favoriten

\* einfache Rezepte

\* Portionsgrößen



Ein umfassender kommerzieller Lebensmitteldatensatz darf nur bei geklärter Lizenz eingebunden werden.



\## 18.2 Formeln



Recherchiere und dokumentiere geeignete Formeln für:



\* Grundumsatz

\* Gesamtenergieverbrauch

\* Kalorienziel

\* Aktivitätskalorien

\* MET-basierte Aktivitätsschätzung

\* BMI

\* geschätztes 1RM

\* Trainingsvolumen

\* Belastungsscore



Jede Formel muss enthalten:



\* wissenschaftliche oder offizielle Quelle

\* Variablen

\* Einheit

\* Grenzen

\* bekannte Ungenauigkeit

\* Testfälle



Keine Berechnung darf medizinische Genauigkeit suggerieren.



Kalorienverbrauch soll bevorzugt als Bereich dargestellt werden.



\---



\# 19. Statistiksystem



Implementiere Statistiken für:



\## Training



\* Trainings pro Woche

\* Trainingsdauer

\* Sätze

\* Wiederholungen

\* Volumen

\* Volumen pro Muskelgruppe

\* persönliche Rekorde

\* Planerfüllung

\* RPE-Entwicklung

\* Übungsfortschritt

\* Trainingshäufigkeit



\## Aktivität



\* Schritte

\* aktive Minuten

\* Distanz

\* Aktivitätskalorien

\* Aktivitäten pro Sportart



\## Körper



\* Gewicht

\* gleitender Gewichtsdurchschnitt

\* Körperfett

\* Muskelmasse

\* Maße

\* Veränderung pro Woche und Monat



\## Ernährung



\* Kalorienmittel

\* Proteinmittel

\* Makroverteilung

\* Zielerreichung

\* Tracking-Konsistenz



\## Regeneration



\* Schlaf

\* Ruhepuls

\* subjektive Energie

\* Muskelkater

\* Stress

\* Readiness-Schätzung



Zeiträume:



\* Tag

\* Woche

\* Monat

\* Quartal

\* Jahr

\* benutzerdefiniert



\---



\# 20. XP- und Levelsystem



Gamification ist ein zentraler Bestandteil.



\## 20.1 XP-Quellen



XP für:



\* Training abgeschlossen

\* geplantes Training eingehalten

\* Schrittziel erreicht

\* Mobility

\* Regeneration

\* Schlafziel

\* Ernährung dokumentiert

\* Quest abgeschlossen

\* Challenge abgeschlossen

\* persönliche Bestleistung

\* Gruppenbeitrag

\* Bossbeitrag



\## 20.2 Sicherheitsregeln



Kein unbegrenztes XP-Farming.



Verwende:



\* tägliche Caps

\* abnehmende Erträge

\* serverseitige Regeln

\* Plausibilitätskontrollen

\* Mindestqualität einer Aktivität

\* Deduplizierung

\* keine XP-Vergabe nur nach gemeldeten Kalorien



Ruhetage und Regeneration müssen ebenfalls belohnt werden.



\## 20.3 Levelbelohnungen



\* Titel

\* Profilrahmen

\* Themes

\* Dashboard-Widgets

\* Avatar-Elemente

\* In-App-Währung

\* kosmetische Effekte

\* Quest-Reihen



Keine essenziellen Fitnessfunktionen hinter Leveln sperren.



\---



\# 21. Quests



Implementiere:



\* tägliche Quests

\* wöchentliche Quests

\* Langzeitquests

\* Eventquests

\* Gruppenquests

\* Bossquests

\* adaptive Quests



Quests müssen zum Profil passen.



Ein Anfänger darf keine Quest mit gesundheitsgefährdendem Umfang erhalten.



Beispiele:



\* geplantes Training durchführen

\* zehn Minuten Mobility

\* Schrittziel erreichen

\* Ruhetag einhalten

\* drei Trainings in einer Woche

\* eine neue Übung ausprobieren

\* Tages-Check-in durchführen

\* Schlafroutine einhalten



\---



\# 22. Streaks und Langzeitpause



Implementiere:



\* Trainingsstreak

\* Wochenziel-Streak

\* Schritt-Streak

\* Mobility-Streak

\* Ernährungs-Tracking-Streak

\* allgemeinen Aktivitätsstreak



Geplante Ruhetage unterbrechen keinen Trainingsstreak.



\## 22.1 Jährlicher Long-Term Streak Freeze



Einmal pro Kalender- oder Accountjahr erhält jeder Nutzer kostenlos einen Langzeit-Streak-Freeze.



Eigenschaften:



\* Dauer frei wählbar

\* maximal zwei Monate

\* nur einmal jährlich kostenlos

\* Start- und Enddatum sichtbar

\* während der Pause kein Streak-Verlust

\* keine aktive XP-Generierung allein durch den Freeze

\* Pausenstatus im Profil



Vor der kostenlosen Aktivierung muss eine klare Warnung erscheinen:



\* Der kostenlose Freeze ist nur einmal pro Jahr verfügbar.

\* Er kann bis zu zwei Monate dauern.

\* Nach Bestätigung kann er nicht beliebig erneut verwendet werden.

\* Der Nutzer soll Dauer und Datum kontrollieren.

\* Es muss eine zusätzliche Bestätigung geben.



Nach Verbrauch kann ein weiterer Long-Term Freeze für \*\*2,99 Euro\*\* gekauft werden.



Implementiere den Kauf über eine abstrahierte Payment-Schicht und später Google Play Billing.



Der Preis muss serverseitig beziehungsweise über das Store-Produkt bestimmt werden und darf nicht nur hart im Client vertrauenswürdig gespeichert sein.



Käufe müssen verifiziert und wiederherstellbar sein.



Implementiere keine manipulative Countdown-Darstellung.



\---



\# 23. Achievements und Awards



Implementiere Auszeichnungen für:



\* erstes Training

\* 10, 50, 100 und 500 Trainings

\* erste Trainingswoche

\* langfristige Konsistenz

\* persönliche Rekorde

\* unterschiedliche Sportarten

\* Mobility

\* Regeneration

\* Gruppenaktivität

\* Bosskämpfe

\* Turniere

\* Rückkehr nach längerer Pause



Seltenheiten:



\* gewöhnlich

\* selten

\* episch

\* legendär



Unterstütze geheime Achievements.



\---



\# 24. Globale Community-Bosse



Implementiere globale zeitlich begrenzte Boss-Events.



\## 24.1 Grundidee



Alle teilnehmenden Nutzer verursachen durch gesundes Aktivitätsverhalten Schaden an einem globalen Boss.



Boss-Schaden kann entstehen durch:



\* abgeschlossene Workouts

\* aktive Minuten

\* Schritte

\* Mobility

\* Gruppenquests

\* Erholungsziele

\* ausgewogene Aktivitätswochen



\## 24.2 Flat-Damage-Beitrag



Jeder Nutzer erhält einen nachvollziehbaren persönlichen Schadenswert.



Der Beitrag muss:



\* serverseitig berechnet werden,

\* gedeckelt sein,

\* täglich begrenzt sein,

\* pro Aktivitätsart begrenzt sein,

\* gegen Duplikate geschützt sein,

\* nicht durch extremes Training unbegrenzt steigen.



Beispielmodell:



\* erstes qualifiziertes Workout: hoher Basisbeitrag

\* weiteres Workout am selben Tag: stark reduzierter Beitrag

\* Schritte bis zu einem sicheren Tagescap

\* Mobility- oder Regenerationsbonus

\* keine zusätzliche Belohnung über dem gesundheitlich sinnvollen Cap



Die konkreten Caps müssen konfigurierbar sein.



\## 24.3 Rewards



Belohnungen nach:



\* Teilnahme

\* persönlichem Gesamtschaden

\* Beitragsstufen

\* erfolgreichem Bossabschluss

\* Gruppenbeitrag



Kein reines Top-1-Prozent-System.



Belohnungen müssen auch für normale Nutzer erreichbar sein.



Mögliche Belohnungen:



\* XP

\* In-App-Währung

\* Profilrahmen

\* Boss-Abzeichen

\* Titel

\* kosmetische Items

\* Event-Theme



\## 24.4 Bossdarstellung



Zeige:



\* Boss-Lebenspunkte

\* globalen Fortschritt

\* verbleibende Zeit

\* persönlichen Schaden

\* heutiges Cap

\* nächste Belohnungsstufe

\* Gruppenbeitrag

\* Regeln



\---



\# 25. Freunde und Trainingsgruppen



Implementiere:



\* Freunde suchen

\* Freundschaftsanfrage

\* Freundschaft annehmen

\* Freund entfernen

\* Nutzer blockieren

\* private Profile

\* Alias

\* QR- oder Einladungscode

\* Trainingsgruppen

\* Gruppenrollen

\* Gruppenquests

\* Gruppenfortschritt

\* Gruppenchat oder begrenzte Motivationsnachrichten



Körpergewicht, Körperfett, Kalorien und Gesundheitsdaten sind standardmäßig privat.



\---



\# 26. Soziale Trainingsposts



Nutzer können nach einem Training freiwillig einen Beitrag veröffentlichen.



\## 26.1 Stark begrenztes Posting-System



Um schädliche oder unangemessene Inhalte zu reduzieren, verwende zunächst strukturierte Posts statt eines vollständig freien sozialen Netzwerks.



Ein Post kann enthalten:



\* Workout abgeschlossen

\* Trainingsart

\* Dauer

\* ausgewählte Übungen

\* persönliche Bestleistung

\* Stimmung über vordefinierte Auswahl

\* vordefinierte Nachricht

\* kurzer optionaler Freitext

\* optionales Bild erst in einer später kontrollierten Ausbaustufe



\## 26.2 Begrenzungen



\* begrenzte Zeichenanzahl

\* keine externen Links

\* keine Kontaktinformationen

\* keine HTML-Inhalte

\* Rate Limit

\* Spamfilter

\* Wortfilter

\* Meldesystem

\* Blockierfunktion

\* automatische Moderationswarteschlange bei Auffälligkeiten

\* keine öffentlichen Angaben zu exakten Standorten

\* keine Posts mit Essstörungs-, Selbstverletzungs- oder gefährlichen Trainingsinhalten



\## 26.3 Reaktionen



Erlaubte Reaktionen:



\* Stark

\* Glückwunsch

\* Weiter so

\* Respekt

\* Teamwork

\* Erholt euch gut



Keine negativen Bewertungen oder Downvotes.



\---



\# 27. Gruppenturniere und Gruppenkriege



Implementiere faire, zeitlich begrenzte Wettbewerbe zwischen Gruppen.



\## 27.1 Ligen



Gruppen werden nach:



\* Gruppengröße

\* Aktivitätsniveau

\* bisheriger Leistung

\* Erfahrungsstufe

\* Region nur optional

\* Teilnahmequote



in passende Ligen eingeteilt.



Beispiele:



\* Bronze

\* Silber

\* Gold

\* Platin

\* Diamant



\## 27.2 Faire Wertung



Die Wertung darf nicht einfach Gesamtkalorien oder Gesamttrainingszeit summieren.



Verwende normalisierte Punkte aus:



\* Anteil aktiver Gruppenmitglieder

\* Zielerfüllung

\* individuelle Verbesserung

\* Konsistenz

\* qualifizierte Workouts

\* Mobility

\* Regeneration

\* Gruppenquests



Begrenze:



\* Punkte pro Tag

\* Punkte pro Nutzer

\* Punkte pro Aktivitätsart

\* Einfluss sehr großer Gruppen

\* Einfluss einzelner Extremnutzer



Verwende beispielsweise den Durchschnitt oder Median der besten normalisierten Beiträge mit Teilnahmefaktor.



\## 27.3 Turnierfunktionen



\* Matchmaking

\* Saison

\* Matchdauer

\* Live-Fortschritt

\* Ergebnis

\* Belohnungen

\* Auf- und Abstieg

\* Matchhistorie

\* Anti-Cheat-Prüfung

\* Einspruchsmöglichkeit



\---



\# 28. In-App-Währung und Shop



Implementiere eine verdienbare In-App-Währung.



Verdienst durch:



\* Quests

\* Events

\* Bosskämpfe

\* Gruppenwettbewerbe

\* Achievements

\* freiwillige Werbung

\* Levelaufstiege



Shop-Inhalte:



\* Themes

\* Profilrahmen

\* Titel

\* Avatar-Elemente

\* Trainingsabschlussanimationen

\* Emotes

\* kosmetische Begleiter

\* Dashboard-Designs

\* begrenzte Streak-Items



Kein Pay-to-win.



Gekaufte Items dürfen keine Rankingvorteile geben.



Alle Währungstransaktionen werden serverseitig und revisionssicher gespeichert.



\---



\# 29. Monetarisierung



Die erste Testversion ist kostenlos.



Es gibt zunächst kein verpflichtendes Premium-Abonnement.



Die Architektur muss jedoch spätere Modelle unterstützen.



\## 29.1 Commerce-Abstraktion



Implementiere:



\* Product Catalog

\* Entitlements

\* One-Time Purchases

\* Subscriptions

\* Lifetime Entitlement

\* Supporter Pack

\* Promotional Entitlement

\* Tester Lifetime Grant

\* Purchase Verification

\* Restore Purchases

\* Refund Handling



\## 29.2 Spätere Optionen



Vorbereiten:



\* monatliches Premium

\* jährliches Premium

\* Lifetime-Kauf

\* Creator Support Pack

\* kosmetische Packs

\* zusätzlicher Long-Term Streak Freeze



\## 29.3 Tester-Lifetime-Zugang



Administratoren müssen ausgewählten Testern kostenlosen Lifetime-Zugang vergeben können.



Besonders berücksichtigt werden sollen Tester, die konstruktives Feedback gegeben haben.



Implementiere:



\* Testerrolle

\* Feedbackdatensätze

\* Adminentscheidung

\* Lifetime-Entitlement

\* Begründung

\* Vergabezeitpunkt

\* Audit-Log

\* Widerruf nur mit dokumentiertem Grund



Kein automatisches Scoring privater Nachrichten.



\---



\# 30. Freiwillige Werbung



Werbung ist freiwillig.



Keine erzwungenen Vollbildanzeigen.



Der Nutzer kann freiwillig ein Rewarded Ad ansehen, um die Entwicklung zu unterstützen.



Mögliche Belohnung:



\* kleine Menge In-App-Währung

\* kosmetischer Fortschritt

\* Quest-Reroll

\* begrenzter XP-Bonus



Regeln:



\* tägliches Limit

\* kein Ad-Spam

\* keine Werbung während des Trainings

\* keine Werbung in sensiblen Gesundheitsansichten

\* Belohnung erst nach serverseitig beziehungsweise SDK-verifiziertem Abschluss

\* Ad-Provider abstrahieren

\* Test-Provider bereitstellen

\* Consent-Management beachten



\---



\# 31. Optionaler KI-Helfer



Die KI ist ein kleiner optionaler Assistent und keine zentrale Abhängigkeit.



\## 31.1 Funktionen



\* Übungsalternativen erklären

\* Trainingswoche zusammenfassen

\* Plan an verfügbare Zeit anpassen

\* Statistiken verständlich erklären

\* Trainingsnotizen strukturieren

\* einfache Motivation

\* Fragen zur App beantworten



\## 31.2 Vollständig deaktivierbar



In den Einstellungen muss ein deutlich sichtbarer Schalter existieren:



> KI-Funktionen vollständig deaktivieren



Bei Deaktivierung:



\* keine KI-Anfragen

\* keine Datenübertragung an KI-Anbieter

\* keine KI-Vorschläge

\* keine versteckten Hintergrundaufrufe



\## 31.3 Datenschutz



Vor erstmaliger Nutzung:



\* Anbieter anzeigen

\* Datenarten anzeigen

\* Einwilligung einholen

\* Kosten beziehungsweise Limits erklären

\* keine Gesundheitsdaten ohne ausdrückliche Freigabe senden

\* Daten minimieren

\* Inhalte pseudonymisieren



\## 31.4 Kostenkontrolle



Implementiere:



\* lokales Regelwerk vor KI

\* Antwort-Caching, soweit datenschutzrechtlich vertretbar

\* Tokenlimits

\* monatliches Budget

\* Rate Limits

\* günstigen Modelladapter

\* Mock-AI

\* vollständig lokale Fallback-Antworten



\## 31.5 Sicherheitsgrenzen



Die KI darf keine:



\* Diagnosen

\* Medikamentenempfehlungen

\* extremen Diäten

\* gesundheitsgefährdenden Trainingspläne

\* sicheren Verletzungsbeurteilungen



ausgeben.



\---



\# 32. Anti-Cheat und Schutz gegen modifizierte APKs



Plane explizit Schutz gegen:



\* HappyMod

\* modifizierte APKs

\* gepatchte Clients

\* manipulierte lokale Datenbanken

\* gefälschte API-Anfragen

\* Replay-Angriffe

\* manipulierte Werbebelohnungen

\* manipulierte Käufe

\* XP-Farming

\* gefälschte Schritte

\* mehrfach importierte Aktivitäten

\* Emulator- und Bot-Farming



\## 32.1 Serverautoritatives Modell



Folgende Werte dürfen niemals ausschließlich vom Client festgelegt werden:



\* XP

\* Level

\* Währung

\* Questabschluss

\* Boss-Schaden

\* Turnierpunkte

\* Leaderboard-Punkte

\* gekaufte Produkte

\* Lifetime-Zugang

\* Werbebelohnungen

\* Streak-Freeze-Käufe



Der Client sendet Ereignisse und Nachweise. Der Server validiert und berechnet die Belohnung.



\## 32.2 Play Integrity



Integriere Play Integrity für sensible Aktionen:



\* Anmeldung

\* Kauf

\* Rewarded-Ad-Belohnung

\* Bossbeitrag

\* Turnierbeitrag

\* ungewöhnlich hohe XP-Ereignisse

\* Kontoübertragung



Verwende:



\* serverseitige Tokenprüfung

\* Request Hash beziehungsweise Content Binding

\* Nonces, wo vorgesehen

\* Replay-Schutz

\* Zeitfenster

\* Anfragen-ID

\* Risikostufen

\* sanfte Reaktion statt sofortiger Sperre



\## 32.3 Weitere Maßnahmen



\* TLS

\* Certificate Pinning mit sicherem Rotationskonzept

\* Rate Limiting

\* signierte Serverantworten für sensible Vorgänge

\* idempotente Transaktionen

\* Datenbankconstraints

\* Audit Logs

\* Device Risk Score

\* Root- und Hooking-Indikatoren nur als Risikosignal

\* keine alleinige Sperre wegen Root

\* Erkennung unmöglicher Aktivitätsmuster

\* Deduplizierung externer Aktivitäten

\* serverseitige Kaufverifikation

\* serverseitige Werbeverifikation

\* R8/ProGuard für Release

\* Secrets nicht im APK

\* Feature Flags

\* Kill Switch für missbrauchte Belohnungswege



\## 32.4 Reaktion auf Verdacht



Stufenmodell:



1\. Ereignis markieren

2\. Belohnung vorläufig zurückhalten

3\. zusätzliche Validierung

4\. Nutzer informieren

5\. Einspruch ermöglichen

6\. erst bei starker Evidenz Einschränkungen anwenden



Keine automatischen permanenten Sperren auf Basis eines einzelnen Signals.



\---



\# 33. Datenschutz



Gesundheits- und Fitnessdaten sind sensibel.



Implementiere Privacy by Design.



\## Pflichtfunktionen



\* granulare Einwilligung

\* Datenexport

\* Kontolöschung

\* Widerruf einzelner Integrationen

\* Löschung importierter Daten

\* Anzeige der Datenquelle

\* private Standardwerte

\* keine Gesundheitsdaten für personalisierte Werbung

\* keine Weitergabe ohne Zustimmung

\* Datenaufbewahrungsregeln

\* Auditierbare Einwilligungen

\* verschlüsselte Kommunikation

\* sichere Tokenablage

\* minimale Datenerfassung



Erstelle:



\* `PRIVACY.md`

\* Datenflussdiagramm

\* Berechtigungsmatrix

\* Löschkonzept

\* Exportkonzept

\* DSGVO-Checkliste



Die Dokumente ersetzen keine abschließende Rechtsberatung, müssen aber eine solide technische Grundlage bilden.



\---



\# 34. Moderation und Community-Sicherheit



Implementiere:



\* Nutzer blockieren

\* Nutzer melden

\* Post melden

\* Gruppe melden

\* Inhalte löschen

\* Moderationsstatus

\* Spamfilter

\* Rate Limits

\* verbotene Begriffe

\* Admin-Warteschlange

\* Audit-Log

\* Einspruch

\* Schutz Minderjähriger

\* Privatsphäre-Einstellungen



Verwende strukturierte Social-Funktionen statt eines uneingeschränkten öffentlichen Forums.



\---



\# 35. Benachrichtigungen



Implementiere konfigurierbare Benachrichtigungen für:



\* Training

\* Quest

\* Streak

\* Gruppenmatch

\* Boss-Event

\* Freundschaftsanfrage

\* Gruppenmotivation

\* Wochenbericht

\* Synchronisationsfehler

\* Körpermessung

\* Regeneration



Unterstütze:



\* Ruhezeiten

\* tägliches Limit

\* Kategorien

\* vollständiges Abschalten

\* lokales Scheduling

\* Push

\* Deep Links



Keine manipulativen oder beschämenden Nachrichten.



\---



\# 36. Administration



Erstelle ein einfaches Admin-Portal oder eine klar dokumentierte Admin-API.



Administratoren können:



\* Übungen verwalten

\* Trainingspläne verwalten

\* Quests verwalten

\* Boss-Events starten

\* Rewards festlegen

\* Shop-Items verwalten

\* Gruppenmatches prüfen

\* gemeldete Inhalte moderieren

\* Integrationen überwachen

\* Tester markieren

\* Lifetime-Zugang vergeben

\* Feature Flags setzen

\* Gamification-Caps ändern

\* verdächtige Ereignisse prüfen

\* Nutzer nicht ohne Audit-Log verändern



Rollen:



\* Super Admin

\* Content Editor

\* Moderator

\* Support

\* Fitness Expert

\* Tester Manager



\---



\# 37. Demo- und Testdaten



Die App muss direkt testbar sein.



Erstelle Seed-Daten für:



\* Übungen

\* Muskelgruppen

\* Geräte

\* Trainingsorte

\* Trainingspläne

\* Demo-Nutzer

\* Demo-Gruppen

\* Freunde

\* Workouts

\* Aktivitäten

\* Schritte

\* Ernährung

\* Körpermessungen

\* Quests

\* Achievements

\* Boss-Event

\* Gruppenturnier

\* Shop-Items

\* Testkäufe

\* Testwerbung

\* Garmin-Mockdaten

\* RENPHO-Mockdaten

\* YAZIO-/Health-Connect-Mockdaten



Die Testdaten müssen eindeutig als Demo-Daten gekennzeichnet sein.



\---



\# 38. Offline-First und Synchronisation



Die wichtigsten Funktionen müssen offline laufen:



\* Workout

\* Trainingspläne

\* Übungen

\* eigene Übungen

\* Notizen

\* Gewichtseinträge

\* einfache Statistiken

\* Questfortschritt als vorläufiger Stand



Verwende eine Outbox- beziehungsweise Sync-Queue.



Status:



\* lokal

\* ausstehend

\* synchronisiert

\* Konflikt

\* fehlgeschlagen



Konfliktregeln dokumentieren.



Serverbelohnungen bleiben bis zur Validierung vorläufig.



\---



\# 39. Tests



Erstelle umfassende Tests.



\## Android



\* Unit Tests

\* ViewModel Tests

\* Repository Tests

\* Room Tests

\* Compose UI Tests

\* Navigation Tests

\* Offline-Sync-Tests

\* Health-Connect-Adaptertests

\* Gastkonto-Migration

\* Accessibility Tests



\## Backend



\* Unit Tests

\* API Tests

\* Integration Tests

\* Datenbanktests

\* Eventtests

\* Idempotenztests

\* Security Tests

\* Rate-Limit-Tests

\* Kaufverifikation

\* XP- und Währungstests

\* Boss-Cap-Tests

\* Turnier-Balancing-Tests

\* Anti-Cheat-Tests

\* Moderationstests



\## End-to-End



Mindestens folgende Abläufe:



1\. Gast startet Workout und registriert sich später mit Google.

2\. Eigene Übung bleibt nach Kontoumwandlung erhalten.

3\. Health-Connect-Schritte werden importiert.

4\. Doppelte Aktivität erzeugt keine doppelten XP.

5\. YAZIO-Mockdaten werden über NutritionProvider importiert.

6\. RENPHO-Mockgewicht wird korrekt zugeordnet.

7\. Garmin-Mockaktivität wird importiert.

8\. Questabschluss vergibt genau einmal XP.

9\. Boss-Schaden erreicht das Tagescap.

10\. Manipulierter Clientwert wird ignoriert.

11\. Kostenloser Jahres-Freeze zeigt Warnung.

12\. Zweiter Freeze benötigt verifizierten Kauf.

13\. Gruppenmatch bleibt trotz unterschiedlicher Gruppengröße fair.

14\. KI-Schalter verhindert jeden KI-Netzwerkaufruf.

15\. gemeldeter Post erscheint in der Moderation.



\---



\# 40. Dokumentation



Erstelle mindestens:



\## `README.md`



\* Projektübersicht

\* Screenshots oder Mockups

\* Voraussetzungen

\* Schnellstart

\* lokale Demo

\* Testzugänge

\* Befehle



\## `ARCHITECTURE.md`



\* Module

\* Abhängigkeiten

\* Datenflüsse

\* Diagramme

\* Domain Events

\* Offline-Sync



\## `INTEGRATIONS.md`



\* Health Connect

\* Garmin

\* YAZIO

\* RENPHO

\* Google Login

\* Werbeprovider

\* Payment Provider

\* AI Provider

\* Mock-Modi

\* Aktivierungsschritte



\## `DATA\_MODEL.md`



\* Entitäten

\* Beziehungen

\* Constraints

\* Indizes

\* Löschregeln



\## `GAMIFICATION.md`



\* XP

\* Level

\* Quests

\* Caps

\* Boss-System

\* Gruppenligen

\* Streaks

\* Währung

\* Exploit-Schutz



\## `SECURITY.md`



\* Threat Model

\* HappyMod-Szenarien

\* Play Integrity

\* Tokenhandling

\* serverseitige Autorität

\* Rate Limits

\* Incident Response



\## `TESTING.md`



\* Teststrategie

\* lokale Tests

\* Integrationstests

\* E2E

\* Testdaten

\* Coverage



\## `FUTURE\_FEATURES.md`



Nur zusätzliche Funktionen, nicht die in diesem Prompt geforderten Pflichtfunktionen.



\---



\# 41. Future-Features-Dokument



Erstelle `FUTURE\_FEATURES.md` mit mindestens:



\* Zusammenarbeit mit Fitnesstrainern

\* Zusammenarbeit mit Physiotherapeuten

\* Zusammenarbeit mit Ernährungsfachkräften

\* fachliche Prüfung der Trainingspläne

\* Trainerportal

\* individuelle Planfreigaben durch Experten

\* Trainingsplan-Marktplatz

\* Videoanalyse der Übungsausführung

\* Wear-OS-App

\* Garmin-Connect-IQ-App

\* iOS-App

\* Apple Health

\* Live-Kurse

\* Fitnesscenter-Partnerschaften

\* QR-Codes an Geräten

\* automatische Geräteerkennung

\* smarte Satz- und Wiederholungserkennung

\* Computer-Vision-Formcheck

\* erweiterte Ernährungsdatenbank

\* Barcode-Scanner

\* Rezeptscanner

\* regionale Challenges

\* Vereinsfunktionen

\* Universitätsligen

\* wissenschaftliche Studienmodule

\* anonymisierte Opt-in-Forschung

\* weitere KI-Modelle

\* vollständig lokaler KI-Assistent



Kennzeichne Features nach:



\* Idee

\* Recherche

\* geplant

\* blockiert

\* benötigt Partner

\* benötigt rechtliche Prüfung



Noch einmal: Keine Pflichtfunktion dieses Master-Prompts darf in dieses Dokument verschoben werden.



\---



\# 42. UI- und UX-Anforderungen



Verwende modernes Material-3-Design.



Hauptnavigation:



\* Heute

\* Training

\* Fortschritt

\* Community

\* Profil



Zusätzliche Bereiche:



\* Ernährung

\* Übungen

\* Trainingspläne

\* Quests

\* Boss-Events

\* Gruppen

\* Shop

\* Integrationen

\* Einstellungen



Anforderungen:



\* Dark Mode

\* Light Mode

\* dynamische Farben optional

\* große Touch-Ziele

\* Screenreader

\* skalierbare Schrift

\* farbenblind-freundliche Grafiken

\* reduzierte Animationen

\* gute Einhandbedienung

\* verständliche Fehler

\* Skeleton Loading

\* Offline-Anzeige

\* Sync-Status

\* keine überladene Oberfläche



\---



\# 43. Akzeptanzkriterien



Die erste vollständige Testversion gilt nur dann als fertig, wenn:



\* sie lokal reproduzierbar gebaut werden kann,

\* Android-App und Backend starten,

\* Gastmodus funktioniert,

\* Google-Login vorbereitet oder funktionsfähig ist,

\* Gastdaten übernommen werden,

\* Onboarding übersprungen werden kann,

\* Übungen vorbefüllt sind,

\* eigene Übungen lokal und accountgebunden funktionieren,

\* Trainingspläne erstellt und bearbeitet werden können,

\* Gerätelisten Trainingspläne filtern,

\* Workouts offline funktionieren,

\* Schritttracking funktioniert oder über Mock und Health Connect testbar ist,

\* Health Connect angebunden ist,

\* Garmin-, YAZIO- und RENPHO-Adapter vorhanden sind,

\* Mock-Integrationen vollständig funktionieren,

\* Statistiken angezeigt werden,

\* XP, Level und Quests funktionieren,

\* Streaks funktionieren,

\* Long-Term Freeze funktioniert,

\* Boss-Events funktionieren,

\* Freunde und Gruppen funktionieren,

\* strukturierte Trainingsposts funktionieren,

\* Gruppenturniere funktionieren,

\* In-App-Währung funktioniert,

\* Commerce vorbereitet ist,

\* freiwillige Werbung abstrahiert ist,

\* KI vollständig deaktivierbar ist,

\* serverseitiger Anti-Cheat vorhanden ist,

\* Play-Integrity-Integration vorbereitet oder aktiv ist,

\* Tests erfolgreich laufen,

\* Dokumentation vollständig ist,

\* keine Secrets im Repository liegen,

\* Linting und CI erfolgreich sind.



\---



\# 44. Umsetzungsphasen



Arbeite in dieser Reihenfolge:



\## Phase 1: Fundament



\* Repository

\* Architektur

\* Android-Grundprojekt

\* Backend

\* Datenbank

\* CI

\* Docker

\* Auth-Grundlagen

\* Gastmodus

\* Dokumentation



\## Phase 2: Fitness-Kern



\* Übungen

\* Muskeln

\* Geräte

\* Trainingsorte

\* Trainingspläne

\* Workout-Modus

\* Offline-Datenbank

\* Synchronisation



\## Phase 3: Gesundheit



\* Schritte

\* Health Connect

\* Ernährung

\* Körpermessungen

\* Kalorienmodelle

\* Statistiken



\## Phase 4: Integrationen



\* Provider-Interfaces

\* Garmin Mock

\* YAZIO Health-Connect Bridge

\* RENPHO Health-Connect Bridge

\* weitere Adaptergrundlagen



\## Phase 5: Gamification



\* XP

\* Level

\* Quests

\* Achievements

\* Streaks

\* Long-Term Freeze

\* Währung

\* Shop



\## Phase 6: Social



\* Freunde

\* Gruppen

\* Posts

\* Moderation

\* Boss-Events

\* Gruppenturniere



\## Phase 7: Monetarisierung und KI



\* Commerce-Abstraktion

\* Supporter Pack

\* Tester Lifetime Grant

\* Rewarded Ads

\* KI-Helfer

\* vollständiger KI-Off-Schalter



\## Phase 8: Security und Release



\* Play Integrity

\* Anti-Cheat

\* Threat Model

\* Penetration-orientierte Tests

\* Datenschutz

\* Performance

\* Accessibility

\* Release Build

\* Dokumentationsprüfung



\---



\# 45. Abschlussarbeiten



Am Ende:



1\. führe alle Tests aus,

2\. behebe alle reproduzierbaren Fehler,

3\. entferne tote Dateien und ungenutzte Abhängigkeiten,

4\. prüfe Abhängigkeiten auf bekannte Sicherheitsprobleme,

5\. prüfe alle Lizenzen,

6\. validiere alle Datenmigrationen,

7\. teste Neuinstallation und Upgrade,

8\. teste Gast-zu-Konto-Migration,

9\. teste Offline- und Konfliktfälle,

10\. teste Anti-Cheat-Szenarien,

11\. aktualisiere sämtliche Dokumentation,

12\. erstelle eine vollständige `CHANGELOG.md`,

13\. erstelle eine Liste verbleibender echter externer Blocker,

14\. liefere keine vorgetäuschten Integrationen als produktiv aus,

15\. kennzeichne Mock- und Sandbox-Modi deutlich.



Erstelle abschließend einen Bericht mit:



\* implementierten Funktionen

\* Architekturentscheidungen

\* ausgeführten Tests

\* Testresultaten

\* bekannten Einschränkungen

\* extern benötigten API-Zugängen

\* Sicherheitsstatus

\* Lizenzstatus der Seed-Daten

\* Anweisungen zum Starten

\* Anweisungen zum Erstellen einer Android-Testversion

\* nächsten empfohlenen Entwicklungsschritten



