# REACT

> „Du lernst nicht die Lösungen. Du lernst die Welt.“

REACT wird ein Physik-Puzzle mit fein simulierter Materie, im Stil von Noita oder The Powder Toy (Leitfaden V3). Die Welt besteht aus vielen kleinen Partikeln:
- Sand rieselt, Wasser fließt, Holz brennt, Lava erstarrt, Dampf steigt auf.
- Was passiert, ergibt sich aus den **Eigenschaften** der Materialien (Dichte, Zündpunkt, Wärmeleitung …), nicht aus Rezepten.

**Stand: Phase 1 „Spielplatz“ ist freigegeben; fertig sind Phase 1b, 2a (Bauen, Materialien) und 2b (Pflanzen, Tiere, Licht), in Arbeit ist 2c** (Leitfaden V3, aktualisierte Fassung). Berichte: [`docs/PHASE1.md`](docs/PHASE1.md), [`docs/PHASE2.md`](docs/PHASE2.md).

Die Raster-Version mit Leveln (Leitfaden V1 und V2) ist nicht mehr Teil dieses Branches. Sie liegt hier:
- Commit `a7fc414` (Tag `grid-legacy`),
- Branch `claude/charming-goodall-6m55iu`,
- Release `beta-51`.

## APK herunterladen

Jeder Push baut automatisch eine APK und veröffentlicht sie als Release:

**[Alle Builds](https://github.com/shootcat/ReAct/releases)**: die APK des neuesten Builds von `claude/react-v3` installieren.

Alle Builds sind mit demselben Schlüssel signiert und installieren sich als Update übereinander. Jedes Release enthält außerdem:
- Screenshots,
- die Messwerte des CI-Rechners.

## Spielplatz

1. Startbildschirm, dann **Spielplatz**. Die Startlandschaft ist 200 × 350 Zellen groß:
   - ein grasbewachsener Hügel mit zwei Bäumen, einem Busch, einem Holzstapel und Vögeln;
   - ein Teich mit Fischen und einer Holzbrücke, eine Ranke wächst zu ihm hin;
   - eine Sanddüne;
   - unter dem Hügel eine dunkle Grotte mit Wasser, Moos und Glühwürmchen, tief im Fels eine Höhle mit Lava.
2. **Material wählen** in der Leiste unten, in Kategorien. Jede Karte zeigt die Körnung des Materials, den Vorrat (hier ∞) und die Kosten pro Einheit.
   - **Lose:** Sand, Erde, Kies, Lehm, Salz, Schnee, Geröll, Erzgeröll, Holzkohle, Asche.
   - **Flüssig:** Wasser, Salzwasser, Lava, flüssiges Metall, Dampf, Rauch.
   - **Bauen:** Holz, Ziegel, Stein, Glas, Metall, Eis.
   - **Hitze:** Feuer, Glut, Lava.
   - **Leben:** Samen, Gras, Moos, Ranke, Fisch, Glühwürmchen, Wurm, Vogel.
   - Dazu der Radierer, den es nur im Spielplatz gibt. Fels ist Gelände und nicht in der Hand des Spielers.
3. **Malen und Schütten** mit einem Finger. Material landet nur dort, wo Luft ist, nie in Fels oder anderem Material. Ein liegen gelassener Finger schüttet weiter. Lose Stoffe fallen gestreut, feste Stoffe werden als geschlossene Form gezeichnet. Fünf Pinselgrößen, die kleinste setzt genau ein Partikel.
   - **Bauwerkzeuge:** Pinsel, Linie, Rechteck und Rahmen. Beim Ziehen zeigt eine halbtransparente Vorschau, was entsteht; Loslassen baut es. Das geht auch in der Pause.
4. **Zwei Finger** zoomen (bis sechsfach) und verschieben. Kommt der zweite Finger in den ersten 70 ms, entsteht kein Klecks.
5. **Pause, Normal, Schnell.** Normal sind 60 Schritte pro Sekunde, Schnell bis zu 180. In der Pause lassen sich mehrere Dinge setzen, die dann gemeinsam starten.
6. **↶ Rückgängig** nimmt den letzten Strich zurück, bis zu 25 Striche. **↺ Neu beginnen** stellt die Startlandschaft wieder her und lässt sich ebenfalls rückgängig machen.
7. Im **Menü** (Zahnrad) gibt es:
   - **Leistung messen** (Ergebnis zum Kopieren),
   - **Leistungsanzeige**: Schritte pro Sekunde, ms pro Schritt, ms pro Bild, wache Chunks,
   - **Alles leeren**,
   - **Einstellungen**: Geräusche, Musik, Vibration, Leistungsanzeige.

Die Welt erzeugt Geräusche: Zischen beim Löschen, Brodeln beim Kochen, Knistern beim Brennen, Fauchen beim Entzünden.

## Simulation (`sim/`)

Das Modul ist reines Kotlin ohne Android. Es läuft identisch in den Tests auf dem Rechner und in der App.

- **Zellulärer Automat**, ein Partikel pro Zelle, gespeichert in flachen Arrays. Es gibt keine Objekte pro Partikel. Die Arrays sind:
  - `ByteArray` für Material, Farbvariante und Flags (brennt, nass, Fallgeschwindigkeit),
  - `FloatArray` für die Temperatur,
  - `ShortArray` für die Lebensdauer (Brenndauer, Rauch, gespeicherte Verdampfungswärme).
- **Reihenfolge:** Die Zeilen werden von unten nach oben abgearbeitet, jede Zeile in der Gegenrichtung der vorigen. Ein Stempel verhindert, dass ein Partikel im selben Schritt zweimal bewegt wird.
- **Chunks zu 32 × 32:** Nur Chunks, in denen im Schritt davor etwas passiert ist, werden gerechnet; ruhende Bereiche schlafen.
- **Wärme:** Jede Zelle tauscht pro Schritt Wärme mit ihren Nachbarn. Die Rate ist das geometrische Mittel beider Leitfähigkeiten, verteilt nach Wärmekapazität. Dabei bleibt die Wärmemenge erhalten, und der Austausch ist immer stabil. Offene Luft kühlt langsam auf die Umgebungstemperatur ab.
- **Phasenwechsel nur über Temperaturschwellen:**
  - Entzünden (`ignite_temp`), Schmelzen (`melt_temp`), Sieden (`boil_temp`), Erstarren, Gefrieren und Kondensieren (`freeze_temp`).
  - Sieden und Erstarren brauchen zusätzlich latente Wärme. Wasser kocht deshalb nicht in einem einzigen Schritt weg, und Lava bleibt eine Weile flüssig.
- **Verbrennung braucht Luft:**
  - Brennendes gibt pro Schritt Wärme ab, schickt Flammen und Rauch in die Luft daneben und zerfällt am Ende zu Asche, zum größten Teil aber zu Luft.
  - Ohne Luft geht es aus. Heißes Brennbares ohne Luft verkohlt, wie im Kohlenmeiler. Rauch verdrängt die Luft, deshalb erstickt Feuer im geschlossenen Raum.
  - Wasser löscht, weil es kühlt und die Luft abschneidet.
- **Wasser:**
  - Es wird von Erde und Sand teilweise aufgesogen; nasse Erde rutscht, nasser Sand hält.
  - Fließendes Wasser trägt lose Körner mit.
  - Dampf kondensiert beim Abkühlen zu Wasser.
- **Gase** steigen nach ihrer Dichte auf und verlassen die Welt nach oben. Unter Überhängen sammeln sie sich.
- **Strukturen:** Zusammenhängende feste Zellen bilden eine Struktur. Berührt sie verankertes Gelände (Fels), steht sie, sonst fällt sie als Ganzes.
- **Licht** (`Light.kt`): ein Lichtfeld in Blöcken zu 2 × 2 Zellen, alle 8 Schritte neu.
  - Tageslicht fällt von oben ein und streut, Feuer, Glut, Lava, Glühendes und Glühwürmchen leuchten selbst.
  - Luft, Wasser, Glas und Laub lassen Licht durch, feste Stoffe nicht.
  - Höhlen ohne Lichtquelle sind dunkel.
- **Pflanzen und Tiere** (`Life.kt`) sind Zellen wie alles andere:
  - Pflanzen wachsen über Zufallsticks (jede Zelle etwa einmal pro Sekunde), wenn sie Licht und Wasser haben, und sterben an Hitze und Salz.
  - Bäume und Ranken sind Strukturen.
  - Tiere bewegen sich in jedem Schritt nach einfachen Regeln.
- **Deterministisch:**
  - fester Seed pro Welt und ein einziger Zufallsgenerator in fester Reihenfolge;
  - im Schritt nur Addition und Multiplikation;
  - Eingaben wirken immer zwischen zwei Schritten.

  Dieselben Aktionen ergeben auf jedem Gerät und bei jeder Bildrate dieselbe Welt. `Session` protokolliert jede Eingabe mit ihrem Schritt und kann das Protokoll wieder abspielen.
- **Undo** über Schnappschüsse des ganzen Zustands: 9 Byte pro Zelle und das Lichtfeld, also etwa 700 kB pro Schnappschuss.
- **SimRunner** rechnet auf einem eigenen Thread mit fester Schrittweite. Er zeichnet das fertige Bild in einen Puffer, und die Oberfläche holt nur fertige Bilder ab. Wird die Simulation langsamer als Echtzeit, läuft sie langsamer, statt Schritte aufzustauen.
- **Darstellung:**
  - ein Pixel pro Zelle, ohne Glättung skaliert;
  - jede Zelle mit eigener Farbschattierung;
  - Heißes glüht, Brennendes flackert;
  - darüber zwei weiche Schichten in Viertelauflösung: Rauch und Dampf als Schwaden, Feuer, Glut und Lava als warmer Schein;
  - im Hintergrund Himmel mit Hügeln und eine dunkle Höhlenwand.

### Materialien (`sim/src/main/resources/materials.json`)

Jedes Material hat die Felder aus Leitfaden 4.3, dazu ein paar Spielwerte:
- `burn_temp`, `burn_heat`, `flame`, `smoke`, `residue`,
- `latent_heat`, `lifetime`,
- `friction`, `spread`, `viscosity`, `opacity`.

Die Einheiten stehen in der Datei.

| Material | Art | Verhalten (aus den Werten) |
|---|---|---|
| Fels | Gelände | unzerstörbar, verankert Strukturen, nicht in der Leiste |
| Stein | fest (Struktur) | steht, wenn er Fels berührt, sonst fällt er als Ganzes; schmilzt bei 1150 °C zu Lava |
| Geröll | Pulver | schwer, rutscht kaum, schmilzt bei 1150 °C zu Lava |
| Sand | Pulver | rieselt flach, sinkt in Wasser, saugt etwas Wasser auf, wird bei 1000 °C zu Glas |
| Erde | Pulver | hält steile Hänge, nass rutscht sie, wird weggespült |
| Wasser | flüssig | fließt, kocht bei 100 °C (mit Verdampfungswärme), löscht |
| Holz | fest (Struktur) | zündet bei 280 °C an der Luft, brennt mit Flammen über Glut, gelöscht zu Holzkohle, abgebrannt zu etwas Asche; als Floß bleibt es auf dem Wasser |
| Feuer | Gas | brennende Flamme, steigt, heizt stark, braucht Luft |
| Rauch | Gas | steigt langsam, verdrängt Luft, verweht nach oben |
| Dampf | Gas | steigt schnell, kondensiert unter 90 °C zu Wasser |
| Lava | flüssig | zäh, 1250 °C, erstarrt unter 850 °C (mit Erstarrungswärme) zu Stein |
| Asche, Holzkohle, Glas | entstehen | Asche und Holzkohle schwimmen; Holzkohle glüht lange ohne Flamme; Glas ist fest und durchsichtig |
| Gras | Erde mit Gras | wächst im Licht auf feuchte Erde daneben, hält Erde zusammen, brennt gut, stirbt an Salz und Hitze zu Erde |
| Moos | Leben | wächst in dunklen, feuchten Höhlen an Wänden und Decken |
| Ranke | fest (Struktur) | wächst zum nächsten Wasser, trägt als Brücke oder Leiter |
| Samen | Pulver | keimt auf feuchter Erde im Licht zu Busch oder Baum |
| Stamm, Laub | fest (Struktur) | ein Baum; durchgebrannt oder abgesägt fällt der obere Teil; stirbt an Salzwasser zu Totholz |
| Fisch | Leben | schwimmt in Süßwasser, stirbt in Salzwasser, in Hitze und an Land |
| Glühwürmchen | Leben | schwirrt im Dunkeln und leuchtet schwach |
| Wurm | Leben | lockert Erde, Wasser sickert ein |
| Vogel | Leben | fliegt bei Feuer oder Rauch davon |

## Projektstruktur

```
sim/                        Partikelsimulation, reines Kotlin (ohne Android)
  World.kt                  Zustand und Schritt: Wärme, Feuer, Phasen, Bewegung, Chunks, Schnappschüsse
  Materials.kt              materials.json lesen, flache Tabellen für den Schritt
  Light.kt                  Lichtfeld: Tageslicht, Lichtquellen, Durchlass
  Life.kt                   Pflanzen (Zufallsticks) und Tiere
  Session.kt                Pinsel, Undo, Reset, Aktionsprotokoll, Wiedergabe
  SimRunner.kt              eigener Thread, feste Schrittweite, fertige Bilder, Leistungswerte
  Renderer.kt               Pixelbild, Dunst- und Glühschicht, Hintergrund
  Terrain.kt                Startlandschaft des Spielplatzes (Rauschen nur aus Ganzzahl-Hashes)
  Benchmark.kt              Schritte pro Sekunde: leere, voll aktive und belebte Welt
app/                        Android-App (Kotlin, Jetpack Compose)
  ui/sandbox/               Spielplatz: Weltansicht, Gesten, Leiste, Tempo, Messung
  ui/title/                 Startbildschirm mit kleiner echter Simulation
  ui/settings/              Einstellungen
  audio/SoundManager.kt     Effekte (SoundPool) und Musik (MediaPlayer)
  data/                     Einstellungen, Fortschritt
docs/PHASE1.md              Bericht zu Phase 1: Zusammenfassung, Abweichungen, Messwerte
docs/PHASE2.md              Bericht zu Phase 1b und 2: Zusammenfassung, Abweichungen, Messwerte
docs/v2/                    Unterlagen der Raster-Version (Leitfaden V2)
```

## Bauen und testen

```bash
./gradlew :sim:test                   # Verhalten, Determinismus, Materialdaten, Leistung; Bilder in sim/build/pictures
./gradlew :app:testDebugUnitTest      # App-Smoke-Test mit Robolectric, Screenshots in app/build/screenshots
./gradlew assembleDebug               # APK -> app/build/outputs/apk/debug/
```

Benötigt werden JDK 17+ und ein Android SDK (compileSdk 35). Die GitHub-Action `.github/workflows/build-apk.yml` läuft bei jedem Push: Sie führt die Tests aus, baut die APK und veröffentlicht sie mit den Messwerten des CI-Rechners.
