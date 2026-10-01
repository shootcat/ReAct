# REACT

> „Du lernst nicht die Lösungen. Du lernst die Welt.“

REACT ist ein deterministisches 2D-Logik-Puzzle für Android. Die Welt besteht aus Objekten, Zuständen und festen Regeln; der Spieler baut einen Versuch auf, startet die Simulation und beobachtet, was passiert.

**Stand: Beta-Meilenstein (Phase 1).** Enthalten sind die Core Engine und genau drei Level (0–2) der Welt 1 „Materie“. Laut Spezifikation stoppt die Content-Entwicklung hier bis zum Beta-Feedback.

## APK herunterladen

Jeder Push baut automatisch eine APK und veröffentlicht sie als Release:

**[Neueste APK herunterladen](https://github.com/shootcat/ReAct/releases/latest/download/REACT-beta.apk)** · [alle Builds](https://github.com/shootcat/ReAct/releases)

Alle Builds sind mit demselben Schlüssel signiert und installieren sich als Update übereinander.

## Spielen

1. Startbildschirm → **Spielen** → auf der Weltkarte ein Level wählen.
2. Markierte Objekte ziehen – oder antippen und dann ein freies Feld antippen.
3. **▶** startet die Simulation, sie läuft Schritt für Schritt.
4. Mit der Zeitleiste vor- und zurückspulen; Symbole zeigen, welche Reaktion in welcher Phase passiert ist.
5. **Bearbeiten** ändert den Aufbau, **↺** stellt den Levelanfang wieder her.

Die Oberfläche arbeitet bewusst mit Symbolen statt Text. Das **Discovery Log** (Reaktions-Matrix) sammelt jede beobachtete Reaktion; unbekannte erscheinen als Silhouette. Beim Abschluss zeigt das Spiel, welche Lösungsklassen gefunden wurden (Standard-Weg, Minimal-Weg, System-Override).

**Einstellungen:** Simulationstempo, Reaktions-Vorschau, Markierungen, Schritt-Symbole, Level-Texte (standardmäßig aus), Vibration und Fortschritt zurücksetzen.

## Projektstruktur

```
engine/                     Reine Kotlin-Rule-Engine (ohne Android, voll getestet)
  model/                    GameObject, Rule, LevelData, GameState …
  RuleEngine.kt             Phasenbasierte Simulation + Kaskaden-Schutz
  Physics.kt                Phase 2: Schwerkraft und Fließen
  Simulator.kt              Komplette Zeitleiste (vor-/zurückspulbar)
  LevelLoader.kt            JSON-Loader mit Validierung
  Reactions.kt              Texte für Discovery Log und Schritt-Log
  SolutionClassifier.kt     Standard / Minimal / System-Override
app/                        Android-App (Kotlin, Jetpack Compose, MVVM/UDF)
  src/main/assets/levels/   world_01.json (Typen + Weltregeln) und level_00–02.json
```

## Engine-Regeln

Jeder Simulationsschritt läuft in festen Phasen:

1. **Zustand** – `TOUCH`- und `LOAD`-Regeln (z. B. Feuer + Eis → Wasser, Feuer + Wasser → Dampf, Gewicht auf Druckplatte)
2. **Physik** – Wasser fällt, Dampf steigt. Ist der direkte Weg blockiert, rutschen Flüssigkeiten und Dampf diagonal auf ein freies Nachbarfeld (links zuerst), sonst fließen sie zur nächsten Öffnung in ihrer Reihe (bei Gleichstand nach links)
3. **Signal** – `SIGNAL`-Regeln breiten sich aus, bis das Netz stabil ist

Pro Schritt sind höchstens **100 Regel-Transformationen** erlaubt; darüber bricht die Simulation kontrolliert mit „Kurzschluss“ ab. Gleicher Aufbau ergibt immer exakt dieselbe Zeitleiste.

Es gibt keine Level-Sonderfälle im Code: Alles kommt aus den JSON-Dateien. Neue Level brauchen nur eine neue Datei und einen Eintrag in `world_01.json`.

## Bauen und testen

```bash
./gradlew test           # Rule-Engine-Tests
./gradlew assembleDebug  # Debug-APK -> app/build/outputs/apk/debug/
./gradlew installDebug   # auf Gerät/Emulator installieren
```

Benötigt JDK 17+ und ein Android SDK (compileSdk 35). Die GitHub-Action `.github/workflows/build-apk.yml` führt Tests und Build bei jedem Push aus.
