# Phase 1 „Spielplatz“: Bericht

Leitfaden V3, Abschnitt 7: eine freie Sandbox mit Fels, Sand, Erde, Wasser, Holz, Feuer, Rauch, Dampf, Stein und Lava, dazu Wärme, Pause, Undo und Pinsel. Sie ist über die bestehende CI auf dem Handy installierbar. **Hier stoppt die Arbeit, bis der Testspieler freigibt.**

## Zusammenfassung

- **Neuer Branch** `claude/react-v3`. Die Raster-Version bleibt in Commit `a7fc414` erhalten (Tag `grid-legacy`, siehe Abweichungen), außerdem im Branch `claude/charming-goodall-6m55iu` und im Release `beta-51`.
- **Entfernt:** Raster-Engine, alle Level, Level-, Karten- und Weltauswahl-Bildschirm, das alte Entdeckungsbuch mit Rezeptinhalt.
- **Erhalten:**
  - App-Rahmen: Start, Einstellungen, Fortschrittsspeicher;
  - Musik und Geräusche;
  - CI.
- **Neues Modul `sim`** in reinem Kotlin, getestet auf der JVM (Details im README):
  - zellulärer Automat mit 200 × 350 Zellen in flachen Arrays;
  - Update von unten nach oben mit wechselnder Laufrichtung und Bewegt-Stempel;
  - Chunks zu 32 × 32, ruhende Chunks schlafen;
  - Wärmeleitung nach beiden Leitfähigkeiten;
  - Phasenwechsel nur über Temperaturschwellen, mit latenter Wärme;
  - Verbrennung braucht Luft;
  - eigener Simulations-Thread mit 60 Schritten pro Sekunde, deterministisch.
- **materials.json** mit allen Feldern aus 4.3:
  - für die zehn Materialien der Phase 1,
  - für die drei Stoffe, die aus ihnen entstehen: Asche, Holzkohle, Glas.
- **Spielplatz in der App:**
  - Startlandschaft mit Hügel, Holzstapel, Teich mit Brücke, Düne und Lavahöhle;
  - Materialleiste mit Farbe, Vorrat und Kosten;
  - ein Finger malt bzw. schüttet, vier Pinselgrößen;
  - zwei Finger zoomen und verschieben;
  - Pause, Normal und Schnell;
  - Rückgängig pro Strich, Neu beginnen, Alles leeren;
  - Leistungsanzeige und „Leistung messen“.

### Was sich aus den Eigenschaften ergibt

Alle Punkte sind in `sim/src/test/.../BehaviorTest.kt` als Test hinterlegt. Bilder davon schreiben die Tests nach `sim/build/pictures`; eine Auswahl liegt in `docs/phase1/`.

| Verhalten (Leitfaden 4.4) | Woraus es folgt | Bild |
|---|---|---|
| Sand rieselt, bildet flache Hänge, sinkt in Wasser | Pulver, Reibung 0.2, Dichte 1600 > 1000 | – |
| Erde hält steilere Hänge als Sand | Reibung 0.92 | – |
| Wasser fließt und wird eben | Flüssigkeit, `spread` 5 | – |
| Holz brennt an der Luft ab, wenig Asche, viel Rauch | Zündpunkt 280 °C, `burn_heat`, `residue` 0.2 | `wood_burning.png` |
| Feuer im geschlossenen Raum erlischt, Rauch füllt ihn, das Holz verkohlt | Brennen braucht Luft, Rauch verdrängt sie, heißes Holz ohne Luft verkohlt | `closed_fire.png` |
| Wasser löscht brennendes Holz, es wird Holzkohle | Kühlen und Luft abschneiden, `quench_product` | `wood_doused.png` |
| Holzkohle glüht lange ohne Flamme | `flame` 0, `burn_time` 25 s, wenig `burn_heat` | – |
| Lava trifft Wasser: Stein und Dampf | Wärmeleitung, Siedepunkt, Erstarrungspunkt | `lava_water.png` |
| Lava zündet Holz an und erstarrt zu Stein | Wärmeleitung, Zündpunkt | – |
| Dampf steigt auf und kondensiert zu Wasser | Dichte 0.6, Kondensation unter 90 °C | `steam_condenses.png` |
| Sand in Lava wird Glas | Schmelzpunkt 1000 °C | `sand_glass.png` |
| Erde und Sand saugen Wasser teilweise auf und werden nass | `absorbs` | – |
| Wasser spült einen Erdwall durch | `erodes_in_water`, Mitnahme von Körnern | `dam.png` |
| Rauch steigt auf und sammelt sich unter Überhängen | Dichte 1.0 < Luft 1.2 | – |
| Fels ist unzerstörbar | `hardness` 1, keine Schwellen | – |

### Determinismus

`DeterminismTest` prüft:
- Dieselben Aktionen ergeben denselben Zustand. Geprüft wird ein Fingerabdruck über alle Zellen, den Zufallsgenerator und den Schritt.
- Das Abspielen des Aktionsprotokolls ergibt denselben Zustand.
- Rückgängig führt exakt zum Zustand vor der Aktion zurück.
- Nach einem Rückgängig geht die Welt weiter, als hätte es die Aktion nie gegeben.
- Neu beginnen führt zum Start zurück und lässt sich rückgängig machen.
- Bilder zu erzeugen (auch nach jedem Schritt) verändert nichts.
- Der Simulations-Thread kommt zum selben Ergebnis wie Schritte von Hand.

## Leistung

### Gemessen

Die Welt hat 200 × 350 Zellen. Gemessen wurden je 600 Schritte nach 60 Schritten Aufwärmen.

| Welt | Desktop (Linux, Java 21, 4 Kerne) | CI-Rechner (GitHub, JVM) | Handy |
|---|---|---|---|
| Leere Welt (alle Chunks schlafen) | über 3 Mio. Schritte/s | CI_EMPTY | **steht aus** |
| Leere Welt, alle Chunks wach | 920 Schritte/s (1,09 ms) | CI_EMPTY_AWAKE | **steht aus** |
| Voll aktive Welt, alle 77 Chunks wach, alles in Bewegung | **221 Schritte/s (4,5 ms)** | CI_FULL | **steht aus** |
| Spielplatz mit Feuer, Sand und Wasser (33 von 77 Chunks wach) | 1150 Schritte/s (0,87 ms) | CI_SANDBOX | **steht aus** |
| Bild erzeugen (Pixel, ohne Dunst und Schein) | 0,38 ms | CI_RENDER | **steht aus** |

Die „voll aktive Welt“ zwingt alle Chunks wach und ist zu einem Drittel aus Sand, einem Drittel aus Wasser und sonst aus Lava, Holz, Feuer und Dampf gemischt. Es ist der ungünstigste Fall: jede Zelle wird in jedem Schritt angefasst.

### Auf dem Handy

**Messwerte auf dem Gerät kann ich nicht selbst erheben.** Hier gibt es kein Handy und keinen Emulator. Deshalb misst die App selbst, mit demselben Code wie die Tests:

1. APK aus dem neuesten Release installieren.
2. **Spielplatz** öffnen, oben rechts das **Zahnrad**, dann **„Leistung messen“** (dauert einige Sekunden).
3. **„Kopieren“** und den Text zurückschicken.

Dazu zeigt **„Leistungsanzeige an“** im selben Menü während des Spielens laufend:
- Schritte pro Sekunde,
- ms pro Schritt,
- ms pro Bild,
- wache Chunks.

Die Beta-APK ist dafür nicht mehr debuggable. ART übersetzt sie dann voll, und die Messung fällt nicht zu schlecht aus. Paket und Schlüssel bleiben gleich, die Installation als Update geht weiter.

**Schätzung, nicht gemessen:** Ein Mittelklasse-Handy ist bei einem Kern etwa zwei- bis viermal langsamer als dieser Desktop. Die voll aktive Welt läge dann bei 55 bis 110 Schritten pro Sekunde, also über den geforderten 30. Ein langsames Einsteigergerät könnte nahe an 30 liegen.

### Entscheidung Kotlin oder NDK

Vorerst bleibt es bei **Kotlin**. Nach dem Leitfaden heißt es „erst messen, dann entscheiden“, und die Desktop-Messung liegt um das Siebenfache über dem Ziel. Wenn das Handy unter 30 Schritten pro Sekunde bleibt, gibt es zwei Schritte in dieser Reihenfolge:

1. **Wärme nur in Chunks rechnen, in denen sich Temperaturen unterscheiden.** Laut Profil macht die Wärmeleitung 40 % eines Schritts aus. Fließendes Wasser bei Raumtemperatur bräuchte sie nicht.
2. **Simulationskern in C++ über das NDK.** Die Arrays sind schon flach und lassen sich ohne Umbau übergeben.

## Abweichungen mit Begründung

1. **Tag `grid-legacy` ist nur lokal gesetzt.**
   - Er zeigt auf `a7fc414`, den letzten Stand der Raster-Version. Das Hochladen des Tags bricht in dieser Umgebung jedes Mal mit „unexpected disconnect“ ab, auch nach mehreren Versuchen; Branches lassen sich hochladen.
   - Der Stand bleibt trotzdem wiederherstellbar: über den Branch `claude/charming-goodall-6m55iu` (gleicher Commit) und das Release `beta-51`.
   - Zum Nachholen: `git tag grid-legacy a7fc414 && git push origin grid-legacy`.
2. **Es gibt keinen `main`-Branch auf GitHub.** Der einzige Branch war `claude/charming-goodall-6m55iu`. Als „aktuellen Stand von main“ habe ich dessen letzten Commit genommen, den Stand, der im Release beta-51 läuft.
3. **Drei zusätzliche Materialien: Asche, Holzkohle, Glas.**
   - Sie entstehen zwangsläufig aus den Materialien der Phase 1: Holz brennt ab, wird gelöscht, Sand trifft Lava. Ohne sie bräuchte es Sonderregeln.
   - In der Leiste stehen sie nicht. Sie kommen mit Phase 2 dazu.
4. **Zusätzliche Felder in materials.json.** Der Leitfaden nennt die Mindestfelder. Für das Verhalten brauchte es außerdem:
   - Brenntemperatur und Brennwärme;
   - Flammen- und Rauchrate, Rückstandsanteil;
   - latente Wärme, Lebensdauer;
   - Reibung, Fließweite, Zähigkeit, Deckkraft.

   `absorbs` ist eine Rate statt ja/nein, `wets` kennzeichnet Wasser. Alle Einheiten stehen in der Datei.
5. **Zwei allgemeine Regeln zum Feuer**, keine Materialpaar-Regeln:
   - Heißes Brennbares ohne Luft verkohlt zu seinem Löschprodukt, wie im Kohlenmeiler. Ohne diese Regel bliebe Holz unter Lava ewig Holz.
   - Rauch verweht zwar mit der Zeit (60 s), verlässt die Welt aber vor allem nach oben. Sonst würde Rauch im geschlossenen Raum wieder zu frischer Luft, und Feuer könnte dort nie ersticken.
6. **Stein ist Geröll statt starrer Fläche.**
   - Er fällt senkrecht, rutscht aber nicht, sodass Säulen stehen bleiben. Als starres Material blieb aus Lava entstandener Stein frei im Wasser schweben.
   - Fels ist die feste, unzerstörbare Fläche zum Bauen.
7. **Holz ist in Phase 1 starr.** Es schwimmt deshalb noch nicht. Schwimmende Stämme gehören zu den zusammenhängenden Körpern (4.5, Phase 4). Asche und Holzkohle schwimmen schon, weil sie Pulver sind.
8. **Radierer und „Alles leeren“** gibt es nur im Spielplatz, zum freien Herumprobieren. In Leveln darf man nichts entfernen (4.7.3).
9. **Das Entdeckungsbuch ist in Phase 1 ausgeblendet.**
   - Sein alter Inhalt bestand aus Rezepten, und die verbietet der Leitfaden.
   - Der neue Inhalt (Eigenschaften) gehört laut Abschnitt 7 zu Phase 2. Der Bildschirm kommt dann mit neuem Inhalt zurück.
   - Der Fortschrittsspeicher bleibt im Code und wird ab Phase 3 mit den Leveln benutzt.
10. **Licht ist noch einfach.**
    - Es gibt einen warmen Schein um Feuer, Glut und Lava, und heiße Stoffe glühen.
    - Echte Beleuchtung mit dunklen Höhlen gehört laut Abschnitt 7 zu Phase 2.

## Bilder

Diese Bilder zeigen, was der Renderer macht. Sie stammen aus den Simulationstests auf dem Rechner, Dunst und Schein werden dabei wie in der App überlagert:
- `docs/phase1/sandbox_start.png`: die Startlandschaft;
- `docs/phase1/sandbox_fire.png`: Feuer am Holzstapel und an der Brücke;
- `docs/phase1/sandbox_lava.png`: dazu Lava auf der Brücke und Wasser am Hügel;
- `docs/phase1/sandbox_later.png`: 10 Sekunden später.

Screenshots der echten App erzeugt jeder CI-Lauf; sie hängen an jedem Release:
- Titel, Spielplatz, Feuer;
- Pause mit Lava und Wasser, Lava trifft Wasser;
- Zoom, Messung, Einstellungen.
