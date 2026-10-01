# REACT

> „Du lernst nicht die Lösungen. Du lernst die Welt.“

REACT ist ein deterministisches 2D-Logik-Puzzle für Android. Die Welt besteht aus Objekten, Zuständen und festen Regeln. Jede Bewegung des Spielers setzt die Welt sofort in Gang – man sieht live, was passiert.

**Stand:** Live-Simulation mit Rückgängig/Wiederholen, realistische Physik (Wasser, Dampfdruck, Auftrieb, Wärmeleitung, Verbrennen, Strom, Lava, Öl, Sand) und **vier Welten mit je 20 Leveln plus Bonuslevel** – 84 Level, deren Lösungen und Mindestzugzahlen per Solver bewiesen sind.

## Welten

| Welt | Thema | Neue Elemente | Schwerpunkt |
|---|---|---|---|
| 1 · Materie | Feuer, Eis, Wasser, Holz | Feuer, Eis, Wasser, Dampf, Stein, Holz, Schalter, Platte, Klappe, Tür | Grundreaktionen, Fließen, Gewicht, Reihenfolge |
| 2 · Druck | Dampf unter Druck | Metall, Schieber, Kolben, Berstscheibe | Wärmeleitung, Kessel, Kammerdruck, mehrere Türen gleichzeitig |
| 3 · Strom | Elektrizität | Batterie, Kabel, Lampe, Heizspirale, Relais, Turbine | Stromnetze, leitendes Wasser und Metall, Dampf → Strom → Hitze |
| 4 · Vulkan | Lava, Öl, Sand | Lava, Öl, Sand | Schichtung nach Dichte, Lava + Wasser → Stein, brennendes Öl, rutschender und nasser Sand |

Innerhalb jeder Welt steigt die Schwierigkeit: die ersten Level führen je ein Element mit einem Zug ein, danach braucht es zwei, drei und mehr Züge in der richtigen Reihenfolge, gemeinsam genutzte Ressourcen und mehrere Türen, die gleichzeitig offen sein müssen. Das **Bonuslevel** jeder Welt öffnet sich, wenn alle 20 Level gelöst sind; die nächste Welt öffnet sich mit dem letzten Level der vorigen.

## APK herunterladen

Jeder Push baut automatisch eine APK und veröffentlicht sie als Release:

**[Neueste APK herunterladen](https://github.com/shootcat/ReAct/releases/latest/download/REACT-beta.apk)** · [alle Builds](https://github.com/shootcat/ReAct/releases)

Alle Builds sind mit demselben Schlüssel signiert und installieren sich als Update übereinander.

## Spielen

1. Startbildschirm → **Spielen** → Welt wählen → auf der Weltkarte ein Level wählen.
2. Markierte Objekte ziehen – oder antippen und dann ein freies Feld antippen. Man muss nicht genau treffen: das nächste bewegliche Objekt in Fingerreichweite wird gegriffen.
3. Die Welt reagiert **sofort**: Physik und Regeln laufen nach jedem Zug Schritt für Schritt, bis wieder alles ruht. Man darf auch eingreifen, während noch etwas passiert.
4. **↶ Rückgängig** und **↷ Wiederholen** gehen durch die eigenen Züge (jeder Zug speichert den Zustand davor), **↺** startet das Level neu – auch das lässt sich rückgängig machen.

Das Spielfeld nutzt die volle Bildschirmbreite; reine Wandränder werden abgeschnitten.

Die Oberfläche arbeitet bewusst mit Symbolen statt Text. Das **Discovery Log** (Reaktions-Matrix) sammelt jede beobachtete Reaktion; unbekannte erscheinen als Silhouette. Beim Abschluss zeigt das Spiel, welche Lösungsklassen gefunden wurden (Standard-Weg, Minimal-Weg mit möglichst wenigen Zügen, System-Override).

**Einstellungen:** Simulationstempo, Reaktions-Vorschau, Markierungen, Level-Texte (standardmäßig aus), Vibration und Fortschritt zurücksetzen.

## Projektstruktur

```
engine/                     Reine Kotlin-Rule-Engine (ohne Android, voll getestet)
  model/                    GameObject, Rule, LevelData, GameState …
  RuleEngine.kt             Phasenbasierte Simulation + Kaskaden-Schutz
  Physics.kt                Phase 2: Schwerkraft, Fließen, Druck, Wärme, Verbrennen
  LiveSimulation.kt         Live-Modus: Zug → Welt reagiert bis zur Ruhe (unveränderliche Runs für Undo)
  Simulator.kt              Ganze Zeitleiste am Stück (Tests, Determinismus)
  LevelLoader.kt            JSON-Loader mit Validierung
  Reactions.kt              Texte für Discovery Log und Schritt-Log
  SolutionClassifier.kt     Standard / Minimal / System-Override
app/                        Android-App (Kotlin, Jetpack Compose, MVVM/UDF)
  src/main/assets/levels/   elements.json (alle Materialien + Regeln), world_01–04.json, w1_01 … w4_bonus.json
```

## Engine-Regeln

Jeder Simulationsschritt läuft in festen Phasen:

1. **Zustand** – Regeln aus `elements.json`: Hitze schmilzt Eis und entzündet Holz und Öl, Wasser löscht Feuer und brennendes Holz (es entsteht Dampf), Dampf taut Eis, Wärme leitet sich durch Metall, heißes Metall und Heizspiralen bringen Wasser zum Sieden, Dampf hebt Kolben, sprengt Berstscheiben und dreht Turbinen, Strom lässt Lampen leuchten und Spiralen glühen, Wasser kühlt Lava zu Stein und durchnässt Sand, Wasser löst Schalter aus, Gewicht drückt Platten
2. **Physik** – realistisches Verhalten jedes Materials:
   - **Wasser** ist ein Volumen (bis 8 Einheiten pro Zelle): es fällt, füllt Becken von unten, läuft zu nahen Kanten und Gruben ab und verteilt sich sonst zu Pfützen
   - **Dampf** ist ebenfalls ein Volumen – wie umgedrehtes Wasser: er steigt, perlt durch Wasser, läuft unter Decken zu Öffnungen und füllt Kammern von oben. Aus 3 Einheiten Wasser werden 6 Einheiten Dampf
   - **Druck:** Jede zusammenhängende Luftkammer hat einen Druck (Dampf pro Feld). Ein **Schieber** wird von der Seite mit höherem Druck weggedrückt, sobald der Unterschied reicht – und bleibt stehen, wenn sich der Dampf genug ausdehnen kann
   - **Auftrieb und Verdrängung:** leichte Objekte (Holz, Eis) schwimmen und steigen in Wasser nach oben, schwere (Stein, Metall, Feuerschalen) sinken und verdrängen das Wasser
   - **Wärmeleitung:** Metall nimmt die Hitze von Feuer oder brennendem Holz auf und gibt sie weiter – ein Feld pro Schritt, mit jedem Feld ein Grad weniger. Ohne Quelle kühlt es langsam ab
   - **Verbrennen:** Holz und Öl brennen eine Weile und zerfallen dann; gelöschtes Holz bleibt verkohlt zurück
   - **Strom** fließt von Batterien und drehenden Turbinen durch Kabel, Metall, Wasser, nassen Sand und geschlossene Relais
   - **Schichtung:** Flüssigkeiten ordnen sich nach Dichte – Lava sinkt unter Wasser, Öl schwimmt oben. Lava ist zäh und schwer, Öl leicht und brennbar
   - **Sand** rieselt schräg ab und bildet Hänge; nass leitet er Strom
3. **Signal** – Schalter, Platten und Kolben senden Signale; Türen und Klappen reagieren, bis das Netz stabil ist

Pro Schritt sind höchstens **100 Regel-Transformationen** erlaubt; darüber bricht die Simulation kontrolliert mit „Kurzschluss“ ab (dann hilft Rückgängig). Gleiche Züge ergeben immer exakt denselben Ablauf.

Es gibt keine Level-Sonderfälle im Code: Alles kommt aus den JSON-Dateien. Materialien werden über Eigenschaften beschrieben (`gravity`, `liquid`, `gas`, `density`, `weight`, `heat`/`heat_state`, `conducts`, `fuel`/`burnt_state`, `pushable`/`resist` …). Level werden als ASCII-Karte mit Legende beschrieben (`#` Wand, `.` frei, `:` frei aber nicht bebaubar, Buchstaben laut `legend`; eine Legende kann Eigenschaften überschreiben, z. B. `"gravity": false` für fest eingebaute Metallstangen). Neue Level brauchen nur eine neue Datei und einen Eintrag in der passenden `world_0N.json`.

Die Musterlösungen aller Level stehen in `engine/src/test/resources/walkthroughs.txt` (`w2_10 [STANDARD] fire_1_5@4,5 …`, dazu `w2_10 min=2`). Der Test spielt jede Lösung durch, prüft die angegebenen Lösungsklassen und beweist per Breitensuche, dass es keine kürzere Lösung als die angegebene Mindestzugzahl gibt.

## Bauen und testen

```bash
./gradlew test           # Rule-Engine-Tests
./gradlew assembleDebug  # Debug-APK -> app/build/outputs/apk/debug/
./gradlew installDebug   # auf Gerät/Emulator installieren
```

Benötigt JDK 17+ und ein Android SDK (compileSdk 35). Die GitHub-Action `.github/workflows/build-apk.yml` führt Tests und Build bei jedem Push aus.
