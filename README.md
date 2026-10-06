# REACT

> „Du lernst nicht die Lösungen. Du lernst die Welt.“

REACT ist ein deterministisches 2D-Logik-Puzzle für Android über die Kräfte der Natur. Jedes Level ist ein Schnitt durch eine Landschaft: Feuer, Wasser, Dampf, Wolken, Erde und Stein folgen festen Regeln. Jede Bewegung des Spielers setzt die Welt sofort in Gang – man sieht live, wie Eis schmilzt, Wasser in Mulden läuft, Dampf aufsteigt und als Regen wiederkommt.

**Stand:** Live-Simulation mit Rückgängig/Wiederholen, Ziehen-und-Verschmelzen, Aufgaben mit Sternen, Wasserkreislauf, Geräusche und Musik. Die alten 84 Level sind ersetzt durch die Welt **„Prüfung“**: zehn neu entworfene Level (`p_01` … `p_10`) mit **Ablagefeldern**. Jedes beruht auf einer Einsicht, und jedes hat eine bewiesene Mindestzugzahl. Seine Schwierigkeit misst die vollständige Suche: Lösungen, Sackgassen, naheliegender Zug und Köder. Entwurf und Kennzahlen jedes Levels stehen in [`docs/LEVELDESIGN.md`](docs/LEVELDESIGN.md).

## Welt „Prüfung“

| Level | Einsicht | Züge |
|---|---|---|
| p_01 „Umweg“ | Dampf steigt auf, wird unter der Decke zur Wolke und regnet dort, wohin kein Wasser fließt | 2 |
| p_02 „Zu wenig“ | Wasser erst sammeln, dann reicht es | 3 |
| p_03 „Verdrängung“ | Ein Stein hebt Wasser über den Rand | 3 |
| p_04 „Tragbares Wasser“ | Am Frost gefrorenes Wasser ist ein Block, den man tragen kann | 4 |
| p_05 „Salz“ | Salz löst sich in dem, was es zuerst berührt: aus Schnee wird Süßwasser, aus Wasser Meerwasser | 4 |
| p_06 „Dampfmaschine“ | Der Dampf eines gelöschten Feuers taut das Eis, an das kein Feld heranreicht | 5 |
| p_07 „Damm aus Sand“ | Erst nasser Sand hält dicht | 5 |
| p_08 „Opfer“ | Das Feuer schmilzt selbst den Schnee, den man zum Löschen braucht | 6 |
| p_09 „Fernregen“ | Wolken tragen Wasser; Steine im Windkanal bestimmen, wo sie regnen | 6 |
| p_10 „Meisterstück“ | Eine Kette aus vier Ideen: vereinen, schmelzen, frieren, tauen | 7 |

Alle Elemente der früheren Welten (Feuer, Wasser, Dampf, Wolke, Eis, Stein, Holz, Samen, Meerwasser, Sand, Lava, Glutfels, Frost, Schnee, Salz …) stehen weiter in `elements.json`; die Prüfung nutzt sie gemischt. Ab Level 3 führt der naheliegende Zug nicht zur Lösung, ab Level 5 liegt ein Köder bereit. Das letzte Level hat einen Stern für die kürzeste Lösung.

## APK herunterladen

Jeder Push baut automatisch eine APK und veröffentlicht sie als Release:

**[Neueste APK herunterladen](https://github.com/shootcat/ReAct/releases/latest/download/REACT-beta.apk)** · [alle Builds](https://github.com/shootcat/ReAct/releases)

Alle Builds sind mit demselben Schlüssel signiert und installieren sich als Update übereinander. Jedes Release enthält auch Screenshots aller Bildschirme.

## Spielen

1. Startbildschirm → **Spielen** → Welt „Prüfung“ → ein Level wählen.
2. Bewegliche Dinge liegen auf einem weichen Schatten in einem hellen Rahmen. Man zieht sie – oder tippt sie an und dann das Zielfeld. Man muss nicht genau treffen.
3. **Ziehen und Verschmelzen:** zwei Flammen werden ein großes Feuer, zwei Hölzer ein Holzstapel, Wasser auf Wasser wird tiefes Wasser. Was nicht zusammenpasst, prallt ab – oder reagiert, wenn es eine Regel dafür gibt (eine Flamme neben Eis landet daneben und schmilzt es). Beim Ziehen zeigt das Zielfeld, was passieren wird.
4. Die Welt reagiert **sofort** und läuft Schritt für Schritt, bis alles ruht.
5. Oben stehen die **Aufgaben** (Mulde füllen, Feuer löschen, Regen auslösen, Samen keimen lassen …) mit Häkchen und Sternen. Sobald alle Hauptaufgaben erfüllt sind, erscheint **„Aufgabe erfüllt“**.
6. **↶ Rückgängig** und **↷ Wiederholen** gehen durch die eigenen Züge (jeder Zug speichert den Zustand davor), **↺** startet das Level neu – auch das lässt sich rückgängig machen.

**Ablagefelder:** In Leveln mit `"placement": "marked"` darf man nur auf die markierten Ablagefelder (`+` in der Karte) etwas ablegen; sie leuchten auf, sobald man ein Objekt aufnimmt. Genau gilt:

- **Ablegen** (auf ein freies Feld, eines nur mit Dampf, oder einen Stein in Wasser werfen) geht nur auf ein Ablagefeld.
- **Verschmelzen** geht mit einem Objekt, das selbst auf einem Ablagefeld oder direkt (waagerecht oder senkrecht) neben einem liegt.
- **Reagieren** (z. B. eine Flamme auf Eis ziehen) geht nur, wenn direkt neben dem Partner ein freies Ablagefeld liegt; dort landet das Objekt.
- Aufnehmen darf man jedes bewegliche Objekt, wo immer es liegt. Abgelegtes unterliegt weiter der Physik: ein Stein auf einem hohen Ablagefeld fällt, wenn darunter nichts ist.

Ohne `"placement"` gilt das alte Verhalten: jedes freie, bebaubare Feld.

Das Spielfeld skaliert auf jede Bildschirmgröße und hält ringsum mindestens 20 dp Abstand (mehr, wo die Zurück-Geste des Systems weiter hereinreicht). Überschüssige Höhe wird zu mehr Himmel und mehr Erde.

**Zoom:** Mit zwei Fingern lässt sich das Spielfeld bis auf das Dreifache vergrößern und verschieben, für die breiten Level p_09 (15 Felder) und p_10 (13 Felder). Dabei bleibt kein leerer Rand, und Zusammenziehen zeigt wieder das ganze Feld. Ein Finger zieht und tippt wie immer; kommt ein zweiter dazu, bricht das Ziehen ab.

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
  src/main/assets/levels/   elements.json (Elemente, Regeln, Verschmelzungen), world_01.json, p_01 … p_10.json
docs/LEVELDESIGN.md         Entwurf jedes Levels: Einsicht, Falle, Lösung, Hebel, Kennzahlen, Abweichungen, Startbild
```

## Engine-Regeln

Jeder Simulationsschritt läuft in festen Phasen:

1. **Physik** – Feststoffe fallen und sinken (schwere verdrängen Wasser), leichte steigen auf (Holz, Eis, Bimsstein, Schnee). Flüssigkeiten sind Volumen (bis 8 Einheiten pro Feld): sie fallen, füllen Mulden von unten und schichten sich nach Dichte (Lava unter Meer unter Süßwasser). Dampf steigt wie umgekehrtes Wasser; sammelt sich genug davon unter einem Hindernis, wird er zur **Wolke**, die nahen Dampf aufnimmt, mit dem **Wind** treibt und voll geworden **regnet**. Wärme reicht bei großem Feuer und Lava zwei Felder weit durch die Luft; Holz und Bäume brennen eine Weile und zerfallen.
2. **Regeln** aus `elements.json` – z. B. Hitze + Eis → Wasser, Wasser + Flamme → Dampf, Wasser + Samen → Keimling, Meerwasser + Samen → versalzen, Wasser + Lava → Stein, Glutfels kocht Wasser, Frost + volles Feld Süßwasser → Eisblock, Salz + Eis → Wasser, Salz + Wasser → Meerwasser.

Pro Schritt sind höchstens **100 Regel-Transformationen** erlaubt; darüber bricht die Simulation kontrolliert ab. Gleiche Züge ergeben immer exakt denselben Ablauf.

Es gibt keine Level-Sonderfälle im Code: alles kommt aus den JSON-Dateien. Elemente werden über Eigenschaften beschrieben (`gravity`, `liquid`, `gas`, `density`, `heat`, `heat_radius`, `fuel`, `granular`, `condense`, `cloud` …). Level sind ASCII-Landschaften mit Legende: `#` Erde, `%` Fels, `.` frei, `:` frei, aber nicht bebaubar, `+` Ablagefeld; Großbuchstaben sind bewegliche Dinge (`isMovable`), Kleinbuchstaben gehören zur Landschaft. Ziele: `fill`, `extinguish`, `rain`, `state`, `preserve`, `clear` und `max_moves`, jeweils optional als Stern.

Die Musterlösungen aller Level stehen in `engine/src/test/resources/walkthroughs.txt` (`p_02 min=3`, `p_10 [2] fire_1_5@8,6 …` – in Klammern die erreichten optionalen Aufgaben). Der Test spielt jede Lösung durch, prüft die Sterne und beweist per Breitensuche, dass es keine kürzere Lösung gibt (vollständig bei Leveln mit Ablagefeldern, sonst bis zwei Züge tief).

## Schwierigkeit

Für Level mit Ablagefeldern misst `DifficultyReportTest` per vollständiger Suche (`LevelAnalysis`, jeder Zug gefolgt vom Einschwingen der Welt, Züge mit gleichem Ergebnis zählen einmal):

1. **Mindestzüge**, bewiesen durch Breitensuche.
2. **Lösungen**: Anzahl verschiedener Lösungen in Mindestlänge; Reihenfolge-Varianten zählen einzeln.
3. **Sackgassen**: Anteil der möglichen ersten Züge, nach denen das Level nicht mehr lösbar ist (Suche bis Mindestzüge plus zwei).
4. **Naheliegender Zug**: ein bewegliches Objekt auf das Ablagefeld, das dem Hauptziel am nächsten liegt. Ab Level 3 darf er keine Mindestlösung beginnen.
5. **Köder**: bewegliche Objekte, die in keiner Mindestlösung vorkommen, oder solche, die die Lösung für etwas anderes braucht als ihren offensichtlichen Zweck. Letztere stehen mit Begründung in `REPURPOSED` im Test, etwa der Tropfen in p_08, der nicht löscht, sondern auffängt. Ab Level 5 Pflicht.

Die Suche verwirft Welten, in denen ein Hauptziel nicht mehr erreichbar ist, etwa wenn der Samen verbrannt ist. Welten, die sich nur in den Nummern selbst entstandener Dinge unterscheiden (Schmelzwasser, Dampf, Eis), zählt sie als eine.

Aktueller Stand (`engine/build/difficulty.md`):

| Level | Mindestzüge | Lösungen | Sackgassen | Naheliegender Zug | Köder |
|---|---|---|---|---|---|
| p_01 | 2 | 3 | 44 % (4/9) | führt nicht zur Lösung | – |
| p_02 | 3 | 2 | 75 % (6/8) | führt nicht zur Lösung | – |
| p_03 | 3 | 8 | 33 % (4/12) | führt nicht zur Lösung | – |
| p_04 | 4 | 4 | 33 % (4/12) | führt nicht zur Lösung | – |
| p_05 | 4 | 8 | 50 % (12/24) | führt nicht zur Lösung | fire_9_5, water_7_2 |
| p_06 | 5 | 32 | 45 % (9/20) | führt nicht zur Lösung | salt_2_1 |
| p_07 | 5 | 4 | 50 % (3/6) | führt nicht zur Lösung | water_1_2 |
| p_08 | 6 | 9 | 50 % (6/12) | führt nicht zur Lösung | water_3_5 |
| p_09 | 6 | 10 | 83 % (10/12) | führt nicht zur Lösung | stone_7_9 |
| p_10 | 7 | 12 | 52 % (10/19) | führt nicht zur Lösung | water_10_6 |

Der Test prüft die Kurve je Levelnummer (1–2: 2–3 Züge; 3–4: 3–4 Züge, mindestens 30 % Sackgassen; 5–7: 4–6 Züge, 40 %; 8–10: 6–8 Züge, 50 %) und schreibt die Tabelle nach `engine/build/difficulty.md`. `WalkthroughTest` beweist die Mindestzugzahl jedes Levels vollständig.

## Bauen und testen

```bash
./gradlew :engine:test                # Engine-Tests (Physik, Regeln, alle Level und Lösungen)
./gradlew :app:testDebugUnitTest      # App-Smoke-Test mit Robolectric, Screenshots in app/build/screenshots
./gradlew assembleDebug               # Debug-APK -> app/build/outputs/apk/debug/
./gradlew installDebug                # auf Gerät/Emulator installieren
```

Benötigt JDK 17+ und ein Android SDK (compileSdk 35). Die GitHub-Action `.github/workflows/build-apk.yml` führt Tests und Build bei jedem Push aus und veröffentlicht die APK.
