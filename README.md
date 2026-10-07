# REACT

> „Du lernst nicht die Lösungen. Du lernst die Welt.“

REACT ist ein deterministisches 2D-Logik-Puzzle für Android über die Kräfte der Natur. Jedes Level ist ein Schnitt durch eine Landschaft: Feuer, Wasser, Dampf, Wolken, Erde und Stein folgen festen Regeln. Jede Bewegung des Spielers setzt die Welt sofort in Gang – man sieht live, wie Eis schmilzt, Wasser in Mulden läuft, Dampf aufsteigt und als Regen wiederkommt.

**Stand:** Live-Simulation mit Rückgängig/Wiederholen, Ziehen-und-Verschmelzen, Aufgaben mit Sternen, Wasserkreislauf, Geräusche und Musik, Ablagefelder, vollständige Suche für die Schwierigkeit und Zoom. Nach Leitfaden V2 wird REACT eine **Rätsel-Sandbox aus echten Materialien**: Das Ergebnis einer Reaktion ist das Werkzeug für die nächste (brennendes Holz rechtzeitig gelöscht wird Holzkohle, Holzkohle wird Glut, Glut schmilzt Sand zu Glas). Die Welt **„Test“** prüft das Prinzip mit drei Leveln; die früheren Level (Welten Wald bis Frost, Prüfung p_01 … p_10) sind entfernt, ebenso der Frostkristall.

## Welt „Test“

Drei Messpunkte, jeder aus einem anderen Gebiet. Der Spieler kennt alle Elemente und Reaktionen darin schon; die Schwierigkeit kommt nur aus dem Kombinieren. Deshalb führt das Entdeckungsbuch in dieser Welt alle Reaktionen von Anfang an (`"reactions_known": true` in `world_01.json`).

| Level | Gebiet | Ziel | Einsicht | Stand |
|---|---|---|---|---|
| t_01 „Kochstelle“ | Wald, leicht | Wasser im Becken kocht, der Wald brennt nicht | Glut erhitzt, ohne zu zünden | fertig, 4 Züge |
| t_02 „Süßwasser“ | Küste, Mitte | Becken auf der Klippe voll Süßwasser, die Hütte brennt nicht | Verdunsten entsalzt | fertig, 5 Züge |
| t_03 „Tiefe Schmelze“ | Mine, Obergrenze | Glas herstellen, der Wald brennt nicht | Metall leitet Hitze durch die Wand; es gibt nur eine Flamme | fertig, 9 Züge |

## APK herunterladen

Jeder Push baut automatisch eine APK und veröffentlicht sie als Release:

**[Neueste APK herunterladen](https://github.com/shootcat/ReAct/releases/latest/download/REACT-beta.apk)** · [alle Builds](https://github.com/shootcat/ReAct/releases)

Alle Builds sind mit demselben Schlüssel signiert und installieren sich als Update übereinander. Jedes Release enthält auch Screenshots aller Bildschirme.

## Spielen

1. Startbildschirm → **Spielen** → Welt „Test“ → ein Level wählen.
2. Bewegliche Dinge liegen auf einem weichen Schatten in einem hellen Rahmen. Man zieht sie – oder tippt sie an und dann das Zielfeld. Man muss nicht genau treffen.
3. **Ziehen und Verschmelzen:** eine Flamme auf Holzkohle wird Glut, Erz auf Holzkohle wird Schmelzgut, Erde und Wasser werden Schlamm, Wasser auf Wasser wird tiefes Wasser. Ein Stein auf einem Baum ist ein Werkzeug: der Baum wird zu Holz, der Stein bleibt liegen. Was nicht zusammenpasst, prallt ab – oder reagiert, wenn es eine Regel dafür gibt (eine Flamme neben Eis landet daneben und schmilzt es). Beim Ziehen zeigt das Zielfeld, was passieren wird.
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

**Zoom:** Mit zwei Fingern lässt sich das Spielfeld bis auf das Dreifache vergrößern und verschieben, für breite Level. Dabei bleibt kein leerer Rand, und Zusammenziehen zeigt wieder das ganze Feld. Ein Finger zieht und tippt wie immer; kommt ein zweiter dazu, bricht das Ziehen ab.

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
  src/main/assets/levels/   elements.json (Elemente, Regeln, Verschmelzungen), world_01.json (Welt „Test“), t_01 … t_03.json
docs/LEVELDESIGN.md         Entwurf jedes Levels: Einsicht, Falle, Lösung, Hebel, Kennzahlen, Abweichungen, Startbild
```

## Engine-Regeln

Jeder Simulationsschritt läuft in festen Phasen:

1. **Physik** – Feststoffe fallen und sinken (schwere verdrängen Wasser), leichte steigen auf (Holz, Eis, Bimsstein, Schnee). Flüssigkeiten sind Volumen (bis 8 Einheiten pro Feld): sie fallen, füllen Mulden von unten und schichten sich nach Dichte (Lava unter Meer unter Süßwasser). Dampf steigt wie umgekehrtes Wasser; sammelt sich genug davon unter einem Hindernis, wird er zur **Wolke**, die nahen Dampf aufnimmt, mit dem **Wind** treibt und voll geworden **regnet**. Wärme reicht bei großem Feuer und Lava zwei Felder weit durch die Luft. Metall leitet Hitze weiter und gibt sie, in einem Wandschlitz steckend, durch eine Wand hindurch ab; ohne Hitzequelle kühlt es ab. Holz, Bäume und die Hütte brennen eine feste Zahl **Züge** (Holz 2, Baum und Hütte 3, der Zug des Entzündens zählt mit) und zerfallen dann zu Asche; bis dahin lassen sie sich löschen. Kleine Glutpunkte am brennenden Ding zeigen, wie viele Züge bleiben.
2. **Regeln** aus `elements.json` – z. B. Flamme + Holz → brennt, Wasser + brennendes Holz → Holzkohle, Glut kocht Wasser und Salzwasser (der Dampf regnet als Süßwasser ab), Glut oder heißes Metall + Sand → Glas, Glut + Schmelzgut → Metall, Hitze + Schnee → Wasser, Salz + Schnee → Wasser, Salz + Wasser → Meerwasser. Nur eine offene Flamme entzündet; Glut und heißes Metall sind heiß, zünden aber nichts an.

Pro Schritt sind höchstens **100 Regel-Transformationen** erlaubt; darüber bricht die Simulation kontrolliert ab. Gleiche Züge ergeben immer exakt denselben Ablauf.

Es gibt keine Level-Sonderfälle im Code: alles kommt aus den JSON-Dateien. Elemente werden über Eigenschaften beschrieben (`gravity`, `liquid`, `gas`, `density`, `heat`, `heat_radius`, `conducts`, `heat_through_wall`, `burn_moves`, `granular`, `condense`, `cloud` …). Level sind ASCII-Landschaften mit Legende: `#` Erde, `%` Fels, `.` frei, `:` frei, aber nicht bebaubar, `+` Ablagefeld; Großbuchstaben sind bewegliche Dinge (`isMovable`), Kleinbuchstaben gehören zur Landschaft. Ziele: `fill` (mit `min` oder `"full": true`; eine fremde Flüssigkeit im Bereich verdirbt es), `produce` (mindestens `min` Dinge vom Typ `element`), `heat` (eine Flüssigkeit im Bereich kocht), `extinguish`, `rain`, `state`, `preserve` (ein Ding oder alle Dinge der `types` in einem Bereich), `clear` und `max_moves`, jeweils optional als Stern.

Die Musterlösungen aller Level stehen in `engine/src/test/resources/walkthroughs.txt` (`t_01 min=4`, `t_01 [] fire_0_4@5,4 …` – in Klammern die erreichten optionalen Aufgaben). Der Test spielt jede Lösung durch, prüft die Sterne und beweist per Breitensuche, dass es keine kürzere Lösung gibt (vollständig bei Leveln mit Ablagefeldern, sonst bis zwei Züge tief).

## Schwierigkeit

`DifficultyReportTest` misst die Testlevel nach Leitfaden V2 (5.2) mit `LevelAnalysis`. Jeder Zug wird gefolgt vom Einschwingen der Welt; Züge mit gleichem Ergebnis zählen einmal.

1. **Mindestzüge**, bewiesen durch Breitensuche. Level 1 und 2 werden vollständig durchsucht. Bei Level 3 wächst die Zahl der Welten pro Zug etwa um den Faktor 15 bis 20; vollständig durchsucht wird bis 4 Züge (im Test, einige Minuten). Die Musterlösung mit 9 Zügen zeigt, dass es lösbar ist.
2. **Lösungen**: Anzahl verschiedener Lösungen in Mindestlänge; Reihenfolge-Varianten zählen einzeln.
3. **Sackgassen**: Anteil der möglichen ersten Züge, nach denen das Level nicht mehr lösbar ist (Suche bis Mindestzüge plus zwei).
4. **Naheliegender Zug**: ein bewegliches Objekt auf das Ablagefeld, das dem Hauptziel am nächsten liegt. Er darf keine Mindestlösung beginnen.
5. **Köder**: bewegliche Objekte, die in keiner Mindestlösung vorkommen.
6. **Ablagefelder pro Zug**: Level 1 und 2 mindestens 2, Level 3 mindestens 1,5.
7. **Zufallsspiele** (nur Level 3): 3000 Spiele aus zufälligen erlaubten Zügen, je Mindestzüge plus drei. Höchstens 5 % davon dürfen das Level lösen.

Die Suche verwirft Welten, in denen ein Hauptziel nicht mehr erreichbar ist, etwa wenn der Wald brennt. Welten, die sich nur in den Nummern selbst entstandener Dinge unterscheiden (Holzkohle, Dampf, Schmelzwasser), zählt sie als eine. Die Tabelle schreibt der Test nach `engine/build/difficulty.md`.

`WalkthroughTest` beweist die Mindestzugzahl vollständig, wenn sie höchstens 7 beträgt. `TrapsTest` spielt jede Falle aus dem Leitfaden nach und zeigt, dass das Level danach nicht mehr lösbar ist:
- bei Level 1 und 2 durch vollständige Suche über 6 weitere Züge;
- bei Level 3 durch die Materialbilanz (was verbraucht ist, kommt nicht zurück) und eine vollständige Suche über 3 weitere Züge.

## Bauen und testen

```bash
./gradlew :engine:test                # Engine-Tests (Physik, Regeln, alle Level und Lösungen)
./gradlew :app:testDebugUnitTest      # App-Smoke-Test mit Robolectric, Screenshots in app/build/screenshots
./gradlew assembleDebug               # Debug-APK -> app/build/outputs/apk/debug/
./gradlew installDebug                # auf Gerät/Emulator installieren
```

Benötigt JDK 17+ und ein Android SDK (compileSdk 35). Die GitHub-Action `.github/workflows/build-apk.yml` führt Tests und Build bei jedem Push aus und veröffentlicht die APK.
