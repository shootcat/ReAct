# REACT

> „Du lernst nicht die Lösungen. Du lernst die Welt.“

REACT wird ein Physik-Puzzle mit fein simulierter Materie, im Stil von Noita oder The Powder Toy (Leitfaden V3). Die Welt besteht aus vielen kleinen Partikeln:
- Sand rieselt, Wasser fließt, Holz brennt, Lava erstarrt, Dampf steigt auf.
- Was passiert, ergibt sich aus den **Eigenschaften** der Materialien (Dichte, Zündpunkt, Wärmeleitung …), nicht aus Rezepten.

**Stand: Phase 1 „Spielplatz“ ist freigegeben; fertig sind Phase 1b, 2a (Bauen, Materialien), 2b (Pflanzen, Tiere, Licht) und 2c (Beschaffen, Entdeckungsbuch). Phase 2 wartet auf die Freigabe** (Leitfaden V3, aktualisierte Fassung). Berichte: [`docs/PHASE1.md`](docs/PHASE1.md), [`docs/PHASE2.md`](docs/PHASE2.md).

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

1. Startbildschirm, dann **Spielplatz**. Die Welt füllt die Höhe des Bildschirms und ist zwei bis drei Bildschirmbreiten breit; zwei Finger verschieben und zoomen. Hoch- und Querformat werden unterstützt; im Querformat steht die Bedienung als Spalte neben der Welt.
   - **Neue Welt …** (Menü): eine Vorlage in einer Größe (Klein 320 × 240, Mittel 480 × 300, Groß 600 × 340). Die Wahl wird gemerkt.
   - Vorlagen:
     - **Wiese mit Teich**: Hügel mit Bäumen, Holzstapel und Vögeln, ein Teich mit Fischen und Holzbrücke, Dünen, eine dunkle Grotte mit Moos und Glühwürmchen, eine Lavahöhle im Fels;
     - **Leeres Feld**;
     - **Insel**;
     - **Küste** mit Steilküste und Strand;
     - **Berg mit Schnee** in kalter Luft, mit gefrorenem Teich;
     - **Vulkan** mit Krater, Schlot, Magmakammer und Meer;
     - **Höhlensystem** mit Gängen, Kammern, Teichen, einer Gasspalte voll Grubengas und einer versiegelten Lavatasche.
2. **Material wählen** in der Leiste unten. Ausgeklappt hat sie zwei Zeilen: oben die Kategorien, darunter die Materialien oder Werkzeuge. Der Pfeil klappt sie auf eine schmale Zeile ein. Jede Karte zeigt die Körnung des Materials, den Vorrat (hier ∞) und die Kosten pro Einheit.
   - **Lose:** Sand, Erde, Kies, Lehm, Salz, Schnee, Geröll, Erzgeröll, Glasscherben, Holzkohle, Asche.
   - **Flüssig:** Wasser, Salzwasser, Lava, flüssiges Metall, Dampf, Rauch, Grubengas.
   - **Bauen:** Holz, Ziegel, Stein, Basalt, Glas, Metall, Eis.
   - **Hitze:** Feuer, Glut, Lava.
   - **Leben:** Samen, Gras, Moos, Ranke, Fisch, Glühwürmchen, Wurm, Vogel. Tiere werden einzeln gesetzt: ein Tippen ein Tier, ein Strich alle 12 Zellen eines, in ihren Lebensraum (Fisch ins Wasser, Wurm in die Erde).
   - Dazu der Radierer, den es nur im Spielplatz gibt. Fels ist Gelände und nicht in der Hand des Spielers.
3. **Malen und Schütten** mit einem Finger. Material landet nur dort, wo Luft ist, nie in Fels oder anderem Material. Ein liegen gelassener Finger schüttet weiter. Lose Stoffe fallen gestreut, feste Stoffe werden als geschlossene Form gezeichnet. Fünf Pinselgrößen, die kleinste setzt genau ein Partikel.
   - **Bauwerkzeuge** (Pinselform in der Ecke): Pinsel, Linie, Rechteck und Rahmen. Beim Ziehen zeigt eine halbtransparente Vorschau, was entsteht; Loslassen baut es. Das geht auch in der Pause.
   - **Werkzeug:**
     - Eimer: schöpft eine Sorte loses oder flüssiges Material, gießt es mit seiner Temperatur wieder aus, auch in Rauch oder Dampf, nicht in Feuer.
     - Hand: trägt lose Objekte, nicht zu schwer, nicht zu heiß, nicht in Fels verankert; legt sie in Luft, Rauch oder Dampf ab.
     - „Zu Brettern“ / „Ins Lager“: ein Stamm ergibt 3 Bretter pro Zelle.
   - **Modus „nur Beschaffen“** (Menü): keine unendliche Leiste mehr, nur Eimer, Hand und das Lager. Gebaut wird mit dem, was beschafft wurde.
4. **Zwei Finger** zoomen (heraus bis zur ganzen Welt, hinein bis sechsfach) und verschieben. Kommt der zweite Finger in den ersten 70 ms, entsteht kein Klecks.
5. **Pause, Normal, Schnell** und die **Pinselform** (Pinsel, Linie, Rechteck, Rahmen, fünf Größen) sitzen klein in der rechten unteren Ecke der Welt. Normal sind 60 Schritte pro Sekunde, Schnell bis zu 180. In der Pause lassen sich mehrere Dinge setzen, die dann gemeinsam starten.
6. **↶ Rückgängig** nimmt den letzten Strich zurück, bis zu 25 Striche. **↺ Neu beginnen** stellt die Startlandschaft wieder her und lässt sich ebenfalls rückgängig machen.
7. Im **Menü** (Zahnrad) gibt es:
   - **Neue Welt …**: Vorlage und Größe,
   - **Modus**: unendlicher Vorrat oder nur Beschaffen,
   - **Entdeckungsbuch**: jede zum ersten Mal beobachtete Eigenschaft („Neu: Sand behält Wärme“), mit kleiner Animation,
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
  - Pflanzen und Gebautes sind getrennte Strukturen; Laub und Ranken tragen nichts Schweres.
  - Liegt der Schwerpunkt einer losen Struktur neben ihrer Auflage, rutscht sie über die Kante ab.
- **Druck und Explosionen:** Jeder geschlossene Gasbereich hat einen Druck (Gasmenge mal Temperatur, Dampf sechsfach). Übersteigt er die Festigkeit der schwächsten Wand (Glas 2 bis Metall 14, Fels nie), birst sie. Eine Explosion zerbricht Schwaches, entzündet Brennbares, löst Schnee und schleudert Pulver und Flüssigkeit fort. Wasser an Lava verdampft schlagartig.
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
| Erde | Pulver | hält steile Hänge, nass rutscht sie, wird weggespült; ein Pfropf, dünn wie ein Korn, gibt dem Wasser nach |
| Wasser | flüssig | fließt, kocht bei 100 °C (mit Verdampfungswärme), löscht |
| Holz | fest (Struktur) | zündet bei 280 °C an der Luft, brennt mit Flammen über Glut, gelöscht zu Holzkohle, abgebrannt zu etwas Asche; als Floß bleibt es auf dem Wasser |
| Feuer | Gas | brennende Flamme, steigt, heizt stark, braucht Luft |
| Rauch | Gas | steigt langsam, verdrängt Luft, verweht nach oben |
| Dampf | Gas | steigt schnell, kondensiert unter 90 °C zu Wasser |
| Lava | flüssig | zäh, 1250 °C, erstarrt unter 850 °C (mit Erstarrungswärme) zu Basalt |
| Basalt | fest (Struktur) | dunkel und porös; erkaltete Lava; schmilzt zu Lava, zerbricht zu Geröll |
| Glas | fest (Struktur) | durchsichtig; heiß mit Wasser, Eis oder Schnee abgeschreckt springt es zu Glasscherben, gibt unter Druck als Erstes nach |
| Glasscherben | Pulver | lassen Wasser durch, schmelzen bei 1000 °C wieder zu Glas |
| Kies | Pulver | lässt Wasser durch (durch Dämme, in Höhlen darunter); Sand bleibt darauf liegen |
| Lehm | Pulver | wasserdicht, nass weich, wird in großer Hitze zu Ziegel |
| Schnee | Pulver | isoliert, schmilzt langsam, wird unter Last zu Eis, rutscht am Hang als Lawine ab, wenn er warm oder erschüttert wird |
| Grubengas | Gas | leichter als Luft, sammelt sich unter Decken, kaum sichtbar, zündet als ganze Wolke an Feuer oder Glut |
| Gasspalte | Gelände | gibt in Minen ab und zu Grubengas ab |
| Quelle | Gelände | gibt Wasser ab, solange sie frei liegt; unter Wasser oder Erde ruht sie |
| Magma | Gelände | die Hitze aus der Tiefe: hält ihre 1250 °C, Lava darüber bleibt flüssig |
| Glut | entsteht | glimmt auf Asche minutenlang weiter und zündet, was darauf liegt; Wasser löscht sie zu Holzkohle |
| Asche, Holzkohle | entstehen | schwimmen; Holzkohle glüht lange ohne Flamme |
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
  Discoveries.kt            Entdeckungsbuch: Einträge (discoveries.json), Beobachtung, Szenen
  Session.kt                Pinsel, Bauformen, Eimer, Hand, Lager, Undo, Reset, Aktionsprotokoll, Wiedergabe
  SimRunner.kt              eigener Thread, feste Schrittweite, fertige Bilder, Leistungswerte
  Renderer.kt               Pixelbild, Dunst- und Glühschicht, Hintergrund
  Terrain.kt                Wiese mit Teich (Startlandschaft), Rauschen nur aus Ganzzahl-Hashes
  Templates.kt              Weltgrößen und die übrigen Vorlagen: Leeres Feld, Insel, Küste, Berg, Vulkan, Höhlen
  Benchmark.kt              Schritte pro Sekunde: leere, voll aktive und belebte Welt, größte Welt
level/                      Level (Phase 3), reines Kotlin auf sim/
  Png.kt                    PNG-Leser ohne Android und AWT
  LevelSpec.kt, Level.kt    level.json, Boden aus terrain.png (Farbe = Material), Anker aus anchor.png
  Goals.kt                  Ziele: Material im Bereich, Bereich füllen, Temperatur, Entzünden, Löschen, Licht, Wachstum, Schützen
  Play.kt                   ein laufendes Level: Werkzeuge, Ziele, Handgriffe, Zeit, Medaille
  Actions.kt                Aktionslisten für die Lösungstests
  resources/levels/         index.json und ein Ordner je Level
tools/levels/               Python-Skripte, die den Boden der Level malen (PIL)
app/                        Android-App (Kotlin, Jetpack Compose)
  ui/sandbox/               Spielplatz: Weltansicht, Gesten, Leiste, Tempo, Messung
  ui/title/                 Startbildschirm mit kleiner echter Simulation
  ui/book/                  Entdeckungsbuch
  ui/settings/              Einstellungen
  audio/SoundManager.kt     Effekte (SoundPool) und Musik (MediaPlayer)
  data/                     Einstellungen, Fortschritt
docs/PHASE1.md              Bericht zu Phase 1: Zusammenfassung, Abweichungen, Messwerte
docs/PHASE2.md              Bericht zu Phase 1b und 2 (bis 2d): Zusammenfassung, Abweichungen, Messwerte
docs/ELEMENTE.md            Vorschläge, die noch nicht umgesetzt sind (Schwarzpulver)
docs/v2/                    Unterlagen der Raster-Version (Leitfaden V2)
```

## Bauen und testen

```bash
./gradlew :sim:test                   # Verhalten, Determinismus, Materialdaten, Leistung; Bilder in sim/build/pictures
./gradlew :level:test                 # Levelformat, Ziele, Bewertung, alle Lösungswege der Level; Bilder in level/build/pictures
./gradlew :app:testDebugUnitTest      # App-Smoke-Test mit Robolectric, Screenshots in app/build/screenshots
./gradlew assembleDebug               # APK -> app/build/outputs/apk/debug/
```

Benötigt werden JDK 17+ und ein Android SDK (compileSdk 35). Die GitHub-Action `.github/workflows/build-apk.yml` läuft bei jedem Push: Sie führt die Tests aus, baut die APK und veröffentlicht sie mit den Messwerten des CI-Rechners.
