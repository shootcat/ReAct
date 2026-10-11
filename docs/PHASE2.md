# Phase 1b und Phase 2: Bericht

Leitfaden V3 in der aktualisierten Fassung, Abschnitte 3 und 4. Reihenfolge nach Abschnitt 8: 1b, 2a, 2b, 2c, je ein Build. Nach 2c wird gestoppt, bis der Testspieler freigibt.

## Phase 1b: Mängel aus dem Spielplatz

### Zusammenfassung

**3.1 Schwerkraft und Strukturen**

- **Neue Materialart `solid`.** Holz, Stein (gemauert) und Glas sind jetzt feste Materialien. Später kommen Ziegel, Metall und Eis dazu.
- **Struktur:** Feste Zellen, die waagerecht oder senkrecht aneinanderstoßen, bilden eine Struktur. Holz auf Stein gehört also mit dazu.
- **Verankerung:** Eine Struktur, die verankertes Gelände berührt, bleibt stehen. Verankert ist Fels (Feld `anchored`) oder jede Zelle, die ein Level als verankert markiert (Flag `ANCHORED`, für die Masken aus Phase 3).
- **Lose Strukturen fallen als Ganzes**, ohne Drehung und mit zunehmender Geschwindigkeit. Sie fallen durch Luft und Gase und durch Flüssigkeiten, die leichter sind als die Struktur im Mittel.
  - Ein Holzfloß bleibt deshalb auf dem Wasser liegen, ein Steinblock sinkt.
  - Was unter der Struktur lag, rückt nach oben.
- **Verbindungsprüfung nur bei Änderungen:** Sie läuft, wenn feste Zellen entstehen, verschwinden, sich umwandeln oder bewegen, also bei Brand, Schmelzen, Abbau oder Bauen. Nicht in jedem Schritt.
  - Lose Strukturen prüfen in jedem Schritt nur, ob unter ihnen Platz ist. So fällt ein Brett, wenn die Erde darunter weggespült wird.
- **Während des Falls hält sich nichts fest:** Ein fallendes Brett bleibt nicht an einer Felswand hängen, an der es vorbeirutscht. Erst wenn es liegt, verankert es sich an berührtem Fels.
- **Gewollte Folge:** Brennt der Pfosten einer Brücke durch, stürzt sie ein (Test und Bilder `bridge_burning.png`, `bridge_fallen.png`).
- **Geröll:** Stein gibt es zusätzlich als **Geröll**, ein schweres Pulver. Gemauerter Stein ist fest, Lava erstarrt zu festem Stein.
- **Fels ist reines Gelände:**
  - Er steht nicht mehr in der Leiste.
  - Der Radierer kann ihn nicht entfernen.
  - „Alles leeren“ lässt ihn stehen.

**3.2 Feuer**

- **Sichtbarer Flammenkörper:**
  - Holz gibt viel mehr, dafür kurzlebige Flammen ab.
  - Jede Flamme bekommt ihre Farbe aus ihrer Umgebung: dicht und jung gelb, dann orange, am Rand rot. Dazu flackert sie.
  - Eine neue Flammenschicht in halber Auflösung legt dichte Flammen weich zu einem Körper zusammen (Bilder `wood_burning.png`, `sandbox_fire.png`).
- **Feuer klettert nach oben schneller als zur Seite:** Aus einem heißeren Gas darunter (Flammen, heiße Luft, Rauch) geht Wärme dreimal so gut nach oben wie durch bloße Leitung. Das ist Konvektion als allgemeine Regel.
- **Holz brennt durch:**
  - Außen brennen Flammen, darunter glüht die brennende Schicht als Glut, dunkel mit flackerndem Rot.
  - Heißes Holz ohne Luft verkohlt jetzt langsamer und erst deutlich über dem Zündpunkt, damit die Flammenfront ins Holz wandert.
  - Gelöscht bleibt Holzkohle, abgebrannt etwas Asche.
- **Rauch kommt aus den verlöschenden Flammen** (30 %) statt direkt aus dem Holz. So deckt er die Glut nicht zu und erstickt das Feuer im Freien nicht. Im geschlossenen Raum füllt er den Raum weiter und erstickt das Feuer (Test bleibt grün).
- **Feuer braucht Luft:** unverändert.

**3.3 Wasser**

- **Ruhiges Wasser liegt still.** Eine Flüssigkeit fließt seitlich nur noch zur nächsten Stelle, an der sie tiefer sinken kann. Gibt es keine solche Stelle, ist sie eben und bleibt liegen, und ihr Chunk schläft ein.
  - An der Oberfläche sucht sie über anderes Wasser hinweg. So gleitet eine Welle zur tiefen Stelle, statt hinter einem anderen Tropfen hängenzubleiben.
- **Schneller Pegelausgleich:**
  - Wasser sucht bis 40 Zellen weit und fließt bis 8 Zellen pro Schritt.
  - Ein Block von 364 Zellen ist in 2 Sekunden über 60 Spalten eben; vorher war die Oberfläche nach 8 Sekunden noch gestuft.
  - Der Test verlangt jetzt höchstens eine Zelle Unterschied.
- **Darstellung:**
  - Farbrauschen fast weg (Farbvarianz 0,025 statt 0,06).
  - Mit der Tiefe stetig dunkler.
  - Helle Oberflächenkante mit leichter, wandernder Bewegung.
- **Keine schwarzen Löcher:**
  - Fallende Flüssigkeit fällt höchstens zwei Zellen pro Schritt und reißt dadurch nicht mehr in Tropfen auf.
  - Eine einzelne Luftlücke zwischen Wasser darüber und darunter wird wie Wasser gezeichnet, ebenso in Flammen.
- **Aufsaugen auch bei ruhendem Wasser:** Liegt Wasser still auf trockener Erde, bleibt sein Chunk wach, bis die Erde es aufgesogen hat.

**3.4 Bugfix Leiste**

- **Vermutete Ursache:** der Dehn-Effekt beim Überscrollen von Android 12 und neuer. Er staucht den Inhalt der Leiste beim Scrollen über das Ende hinaus.
- **Behebung:**
  - Der Effekt ist für die Leiste abgeschaltet.
  - Jede Karte hat eine feste Breite und schneidet ihren Inhalt ab.
  - Beschriftungen brechen nie um und kürzen mit „…“.
- **Nachstellen konnte ich den Fehler nicht.** Neuer Test: Leiste mit 130 % Systemschrift, halb gescrollt (`09_leiste_grosse_schrift.png`). **Bitte auf dem Gerät prüfen.**

### Abweichungen mit Begründung

1. **Struktur heißt 4-Nachbarschaft.** Feste Zellen, die sich nur über Eck berühren, gehören nicht zusammen. Damit ist eindeutig, was zusammenhält.
2. **Fallen ohne Drehung, wie im Leitfaden.** Ein Brett, das nur an einem Ende aufliegt, bleibt waagerecht liegen. Kippen kommt mit der Körperphysik in Phase 4.
3. **Brennende Strukturen:** Die Prüfung läuft in jedem Schritt, in dem eine Holzzelle verbrennt, also solange ein Feuer an einer Struktur frisst. Das ist eine Änderung fester Partikel und deshalb gewollt. Die Kosten stehen unten bei den Messwerten.

### Leistung

Gemessen nach Phase 1b auf diesem Desktop (Linux, Java 21, 4 Kerne), je 600 Schritte:

| Welt | Phase 1 | Phase 1b |
|---|---|---|
| Voll aktive Welt (alle 77 Chunks wach) | 4,5 ms (221 Schritte/s) | 4,1 bis 4,6 ms (216 bis 246 Schritte/s) |
| Spielplatz mit Feuer, Sand und Wasser | 0,89 ms | 0,9 bis 1,0 ms |
| Bild erzeugen (Pixel) | 0,38 ms | 0,98 ms |

Das Bild kostet mehr: Wassertiefe, Lückenfüllung und Flammenfarben brauchen Nachbarzellen. Dazu kommt die neue Flammenschicht. Auf dem Handy waren es in Phase 1 0,24 ms für das Bild und 2,0 ms für die voll aktive Welt; mit dem neuen Build bitte erneut **„Leistung messen“**.

**Build:** [`beta-61`](https://github.com/shootcat/ReAct/releases/tag/beta-61). CI grün, Screenshots hängen am Release.

## Phase 2a: Bauen und Materialien

### Zusammenfassung

**Bauwerkzeuge im Spielplatz (4.1)**

- **Pinsel** in fünf Größen, die kleinste setzt genau **1 Partikel**. Ein Pinsel aus einer einzigen Zelle setzt immer dicht, auch bei losem Material.
- **Linie:** Startpunkt antippen, ziehen, loslassen. Die Breite folgt der Pinselgröße. Für Bretter, Balken und Rinnen.
- **Rechteck:** gefüllt, oder als **Rahmen** mit der Pinselgröße als Wandstärke. Für Wände, Becken und Plattformen.
- **Vorschau:** Solange der Finger zieht, zeigt eine halbtransparente Fläche in der Farbe des Materials (mit dünnem hellem Umriss), was entsteht. Ein zweiter Finger bricht ab und zoomt.
- **Bauen in der Pause** geht, auch vergrößert. Jede Form ist **ein Handgriff**: Ein Rückgängig nimmt sie ganz zurück, und sie steht als ein Eintrag im Protokoll, deterministisch wieder abspielbar.
- **Gebautes folgt 3.1:**
  - Eine Linie aus Holz zwischen zwei Felswänden steht.
  - Eine frei in die Luft gezeichnete fällt als Ganzes.
  - Die Bauformen setzen Material nur in Luft.
- **Leiste:** in vier Kategorien geteilt (Lose, Flüssig, Bauen, Hitze), dazu der Werkzeugschalter (Pinsel, Linie, Rechteck, Rahmen).

**Materialien**

- **Baumaterialien**, alle fest und damit Strukturen nach 3.1:
  - Holz (Bretter mit Maserung): leicht, brennt.
  - Ziegel (versetzte Steine mit hellen Fugen): hält bis 1500 °C.
  - Stein (Mauerwerk): schwer.
  - Glas: wasserdicht, durchsichtig.
  - Metall (gebürsteter Glanz): leitet Wärme.
- **Neu:**
  - Kies, Lehm, Salz, Schnee, Erzgeröll (Pulver);
  - Erzader (Gelände wie Fels, verankert);
  - Salzwasser und flüssiges Metall (flüssig);
  - Eis und Metall (fest);
  - Glut (brennende Holzkohle ohne Flamme);
  - Holzkohle und Asche gab es schon.
- **`materials.json` nennt bei jedem Material alle allgemeinen Felder aus 6.3:** kind, density, anchored, flammable, wet_blocks_burning, absorbs, conductivity, heat_capacity, hardness, erodes_in_water, dissolves_in, dissolved_product, bucketable, grabbable, emits_light, blocks_light, color, color_variance.
  - Dazu je nach Material die Brenn- und Phasenfelder.
  - bucketable und grabbable sind für 2c schon belegt, blocks_light für 2b.
  - Ein Test prüft, dass kein Material ein Feld vergisst.
- **Erz** zeigt glänzende orange Einschlüsse im grauen Gestein, die hin und wieder aufblitzen. In der Startlandschaft ziehen sich dünne Erzadern durch den Fels.
- **Übersichtsbild** aller Materialien: `docs/phase2/materials.png`.

**Gewünschtes Verhalten (alle in `MaterialBehaviorTest`)**

1. **Sand und Geröll halten ihre Wärme.**
   - Sand leitet schlecht und kühlt in Luft langsam ab: Nach 4 Sekunden ist er noch über 250 °C heiß.
   - Heißer Sand (900 °C, wie frisch von der Lava) auf einem Holzstapel mit Luft zwischen den Scheiten zündet ihn an.
2. **Nasses Holz brennt nicht,** bis es getrocknet ist (Feld `wet_blocks_burning`). Holz, Erde und Sand saugen Wasser auf.
3. **Wasser:**
   - löscht und verdampft (wie bisher);
   - gefriert unter −1 °C an kaltem Eis;
   - spült Sand, Erde, Kies und Lehm weg;
   - weicht Lehm auf: Eine trockene Lehmwand steht senkrecht, nass rutscht sie.
4. **Salz:**
   - Salz löst sich in Wasser; beide Zellen werden Salzwasser.
   - Salzwasser siedet zu Süßwasserdampf und lässt dabei anteilig Salz zurück.
   - Salz lässt Eis und Schnee schmelzen, solange es daneben liegt, auch ohne Wärme. Ihr Schmelzpunkt sinkt auf −18 °C.
5. **Lava:** erstarrt zu Stein, entzündet, verdampft Wasser (wie bisher).
6. **Umwandlungen durch Hitze:**
   - Sand wird bei 1000 °C zu Glas.
   - Lehm brennt bei 800 °C zu Ziegel.
   - Erz schmilzt bei 1100 °C zu flüssigem Metall, das unter 1000 °C (mit Erstarrungswärme) zu festem Metall wird.
7. **Metall leitet Wärme deutlich besser als Stein.**
   - Ein Metallstab auf einem heißen Felsblock ist 10 Zellen weiter nach 5 Sekunden spürbar heißer als ein Steinstab.
   - Dafür darf der Wärmeaustausch pro Schritt jetzt bis 0,9 statt 0,45 des Wegs zur gemeinsamen Temperatur gehen. Das bleibt stabil, weil jeder Austausch beide Zellen zwischen ihren alten Temperaturen lässt.
   - Davon profitiert auch die Konvektion: Feuer klettert schneller.

### Abweichungen mit Begründung

1. **Lehm zu Ziegel nutzt das Feld `melt_temp`.** Brennen ist kein Schmelzen. Der Leitfaden nennt aber nur Schmelzen, Sieden und Erstarren als Phasenwechsel, und ein eigenes Feld wäre ein Sonderfall.
2. **Zusätzliche Felder:**
   - `salt_melt_temp` und `salty` für „Salz schmilzt Eis“: Gefrierpunkterniedrigung als allgemeine Regel statt eines Materialpaars.
   - `boil_residue` und `boil_residue_share` für „Salzwasser lässt Salz zurück“.
   - `pattern`, `speckle` und `speckle_color` für die Darstellung.
3. **Wasser gefriert bei −1 °C statt 0 °C.** Sonst würde eine Zelle bei genau 0 °C zwischen Eis und Wasser hin- und herspringen.
4. **Glut ist ein eigenes Material**, damit man sie malen und später mit dem Eimer tragen kann. Brennende Holzkohle sieht gleich aus und verhält sich gleich.
5. **Salzwasser gefriert bei −10 °C zu (Süßwasser-)Eis.** Das gelöste Salz bleibt dabei nicht zurück; das wäre eine weitere Sonderregel, und in den Leveln spielt Meereis keine Rolle.

### Leistung

Desktop (Linux, Java 21, 4 Kerne), je 600 Schritte:

| Welt | Phase 1b | Phase 2a |
|---|---|---|
| Voll aktive Welt | 4,1 bis 4,6 ms | 4,4 bis 4,7 ms (214 bis 226 Schritte/s) |
| Spielplatz mit Feuer, Sand und Wasser | 0,9 bis 1,0 ms | 1,26 ms (Erzadern im Fels, stärkere Konvektion) |
| Bild erzeugen | 0,98 ms | 0,93 ms |

Auf dem Handy bitte wieder „Leistung messen“; die Grenze von 8 ms pro Schritt (6.1) ist weit entfernt.
