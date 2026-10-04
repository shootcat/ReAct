# REACT

> „Du lernst nicht die Lösungen. Du lernst die Welt.“

REACT ist ein deterministisches 2D-Logik-Puzzle für Android über die Kräfte der Natur. Jedes Level ist ein Schnitt durch eine Landschaft: Feuer, Wasser, Dampf, Wolken, Erde und Stein folgen festen Regeln. Jede Bewegung des Spielers setzt die Welt sofort in Gang – man sieht live, wie Eis schmilzt, Wasser in Mulden läuft, Dampf aufsteigt und als Regen wiederkommt.

**Stand:** Live-Simulation mit Rückgängig/Wiederholen, Ziehen-und-Verschmelzen, Aufgaben mit Sternen, Wasserkreislauf, Geräusche und Musik – und **vier Welten mit je 20 Leveln plus Bonuslevel**. Alle 84 Level haben eine bewiesene Mindestzugzahl.

## Welten

| Welt | Neue Elemente | Kniff |
|---|---|---|
| 1 · Wald | Flamme, großes Feuer, Wasser, Dampf, Wolke, Eis, Stein, Holz, Holzstapel, Samen, Baum | Wärme schmilzt Eis, Wasser lässt Samen keimen und löscht Feuer, Dampf wird zur Wolke, Wind treibt sie, Regen fällt |
| 2 · Küste | Meerwasser, Sand | Meerwasser ist schwerer als Süßwasser und lässt nichts wachsen, nasser Sand hält Wasser auf, Steine bringen das Meer zum Überlaufen |
| 3 · Vulkan | Lava, Glutfels, Bimsstein | Lava fließt und erstarrt an Wasser zu Stein, der Glutfels kocht jedes Wasser, Bimsstein schwimmt |
| 4 · Frost | Frostkristall, Schnee, Salz | Wasser gefriert am Frost zu einem **losen Eisblock**, den man tragen kann; Salz taut Eis und Schnee, macht Wasser aber salzig – und sinkt im Meer wie ein Stein |

Die Schwierigkeit steigt innerhalb jeder Welt von einem Zug bis zu sechs oder sieben Zügen in der richtigen Reihenfolge, mit geteilten Ressourcen und Ketten aus mehreren Reaktionen. Jede Welt knüpft an die vorige an: das zweite Level einer Welt ist etwa so schwer wie das zehnte der vorigen. Höhere Level haben **optionale Aufgaben** (Sterne), z. B. einen Baum nicht zu verbrennen oder mit wenigen Zügen auszukommen. Das **Bonuslevel** jeder Welt öffnet sich, wenn alle 20 Level gelöst sind; die nächste Welt öffnet sich mit dem letzten Level der vorigen.

## APK herunterladen

Jeder Push baut automatisch eine APK und veröffentlicht sie als Release:

**[Neueste APK herunterladen](https://github.com/shootcat/ReAct/releases/latest/download/REACT-beta.apk)** · [alle Builds](https://github.com/shootcat/ReAct/releases)

Alle Builds sind mit demselben Schlüssel signiert und installieren sich als Update übereinander. Jedes Release enthält auch Screenshots aller Bildschirme.

## Spielen

1. Startbildschirm → **Spielen** → auf der Weltkarte (Wald unten, Frost oben) eine Welt wählen → ein Level wählen.
2. Bewegliche Dinge liegen auf einem weichen Schatten in einem hellen Rahmen. Man zieht sie – oder tippt sie an und dann das Zielfeld. Man muss nicht genau treffen.
3. **Ziehen und Verschmelzen:** zwei Flammen werden ein großes Feuer, zwei Hölzer ein Holzstapel, Wasser auf Wasser wird tiefes Wasser. Was nicht zusammenpasst, prallt ab – oder reagiert, wenn es eine Regel dafür gibt (eine Flamme neben Eis landet daneben und schmilzt es). Beim Ziehen zeigt das Zielfeld, was passieren wird.
4. Die Welt reagiert **sofort** und läuft Schritt für Schritt, bis alles ruht.
5. Oben stehen die **Aufgaben** (Mulde füllen, Feuer löschen, Regen auslösen, Samen keimen lassen …) mit Häkchen und Sternen. Sobald alle Hauptaufgaben erfüllt sind, erscheint **„Aufgabe erfüllt“**.
6. **↶ Rückgängig** und **↷ Wiederholen** gehen durch die eigenen Züge (jeder Zug speichert den Zustand davor), **↺** startet das Level neu – auch das lässt sich rückgängig machen.

Das Spielfeld skaliert auf jede Bildschirmgröße und hält ringsum mindestens 20 dp Abstand (mehr, wo die Zurück-Geste des Systems weiter hereinreicht). Überschüssige Höhe wird zu mehr Himmel und mehr Erde.

Das **Entdeckungsbuch** sammelt jede beobachtete Reaktion; unbekannte erscheinen als Silhouette.

**Einstellungen:** Tempo, Vorschau beim Ziehen, Rahmen um Bewegliches, Level-Texte, Geräusche, Musik, Vibration, Fortschritt zurücksetzen.

**Klang:** kurze Effekte für das, was in der Welt passiert (Zischen, Plätschern, Knistern, Gefrieren, Regen …) und für jede Welt eine ruhige, meditative Melodie (`SoundManager`).

## Projektstruktur

```
engine/                     Reine Kotlin-Engine (ohne Android, voll getestet)
  model/                    GameObject, Rule, LevelData, GameState, Ziele …
  RuleEngine.kt             Ein Schritt: Physik, dann Regeln (Hitze, Löschen, Gefrieren …), Kaskaden-Schutz
  Physics.kt                Fallen, Auftrieb, Flüssigkeiten, Dampf, Wolken, Wind, Regen, Wärme, Verbrennen
  Drops.kt                  Ziehen und Ablegen: platzieren, verschmelzen, reagieren oder abprallen
  LiveSimulation.kt         Live-Modus: Zug → Welt reagiert bis zur Ruhe (unveränderliche Runs für Undo)
  Simulator.kt              Ganze Zeitleiste am Stück (Tests, Determinismus)
  LevelLoader.kt            JSON-Loader mit Validierung
  Reactions.kt              Texte fürs Entdeckungsbuch
app/                        Android-App (Kotlin, Jetpack Compose, MVVM/UDF)
  ui/level/                 Spielfeld, Landschaft, Wasserdarstellung, Aufgaben, „Aufgabe erfüllt“
  audio/SoundManager.kt     Effekte (SoundPool) und Musik je Welt (MediaPlayer)
  data/                     Fortschritt, Einstellungen, Inhalte
  src/main/assets/levels/   elements.json (Elemente, Regeln, Verschmelzungen), world_01–04.json, w1_01 … w4_bonus.json
```

## Engine-Regeln

Jeder Simulationsschritt läuft in festen Phasen:

1. **Physik** – Feststoffe fallen und sinken (schwere verdrängen Wasser), leichte steigen auf (Holz, Eis, Bimsstein, Schnee). Flüssigkeiten sind Volumen (bis 8 Einheiten pro Feld): sie fallen, füllen Mulden von unten und schichten sich nach Dichte (Lava unter Meer unter Süßwasser). Dampf steigt wie umgekehrtes Wasser; sammelt sich genug davon unter einem Hindernis, wird er zur **Wolke**, die nahen Dampf aufnimmt, mit dem **Wind** treibt und voll geworden **regnet**. Wärme reicht bei großem Feuer und Lava zwei Felder weit durch die Luft; Holz und Bäume brennen eine Weile und zerfallen.
2. **Regeln** aus `elements.json` – z. B. Hitze + Eis → Wasser, Wasser + Flamme → Dampf, Wasser + Samen → Keimling, Meerwasser + Samen → versalzen, Wasser + Lava → Stein, Glutfels kocht Wasser, Frost + volles Feld Süßwasser → Eisblock, Salz + Eis → Wasser, Salz + Wasser → Meerwasser.

Pro Schritt sind höchstens **100 Regel-Transformationen** erlaubt; darüber bricht die Simulation kontrolliert ab. Gleiche Züge ergeben immer exakt denselben Ablauf.

Es gibt keine Level-Sonderfälle im Code: alles kommt aus den JSON-Dateien. Elemente werden über Eigenschaften beschrieben (`gravity`, `liquid`, `gas`, `density`, `heat`, `heat_radius`, `fuel`, `granular`, `condense`, `cloud` …). Level sind ASCII-Landschaften mit Legende: `#` Erde, `%` Fels, `.` frei, `:` frei, aber nicht bebaubar; Großbuchstaben sind bewegliche Dinge (`isMovable`), Kleinbuchstaben gehören zur Landschaft. Ziele: `fill`, `extinguish`, `rain`, `state`, `preserve`, `clear` und `max_moves`, jeweils optional als Stern.

Die Musterlösungen aller Level stehen in `engine/src/test/resources/walkthroughs.txt` (`w4_02 min=2`, `w1_01 [1] fire_1_11@7,9` – in Klammern die erreichten optionalen Aufgaben). Der Test spielt jede Lösung durch, prüft die Sterne und beweist per Breitensuche, dass es keine kürzere Lösung gibt.

## Bauen und testen

```bash
./gradlew :engine:test                # Engine-Tests (Physik, Regeln, alle Level und Lösungen)
./gradlew :app:testDebugUnitTest      # App-Smoke-Test mit Robolectric, Screenshots in app/build/screenshots
./gradlew assembleDebug               # Debug-APK -> app/build/outputs/apk/debug/
./gradlew installDebug                # auf Gerät/Emulator installieren
```

Benötigt JDK 17+ und ein Android SDK (compileSdk 35). Die GitHub-Action `.github/workflows/build-apk.yml` führt Tests und Build bei jedem Push aus und veröffentlicht die APK.
