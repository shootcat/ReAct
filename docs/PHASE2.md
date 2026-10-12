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

**Build:** [`beta-63`](https://github.com/shootcat/ReAct/releases/tag/beta-63). Der erste Lauf (62) war rot: Auf 411 dp Breite drückte die Pinselauswahl den Reiter „Hitze“ auf null Breite, der App-Test fand „Feuer“ nicht. Die Reiter rollen jetzt seitlich, die Pinselknöpfe sind 32 dp groß. Screenshots `10_bauen_vorschau` (halbtransparente Vorschau beim Ziehen) und `11_gebaut` (Ziegelrahmen mit Holzbrett fällt als ein Stück) geprüft.

## Phase 2b: Flora, Fauna, Licht

### Zusammenfassung

**Licht (4.2, Licht 1 bis 3)**

- **Lichtfeld** in Blöcken zu 2 × 2 Zellen, Werte von 0 (dunkel) bis 1 (Tageslicht). Die Simulation rechnet es alle 8 Schritte neu (`Light.kt`), nur mit Addition, Multiplikation und Vergleichen, also deterministisch. Es gehört zum Zustand und steckt im Schnappschuss, damit Undo und Wiedergabe exakt bleiben.
- **Lichtquellen:**
  - Himmel: fällt von oben senkrecht ein und streut von dort langsam in alle Richtungen (bleibt pro Block zu 95 % erhalten). So ist der Schatten unter der Brücke oder einem Baum gedämpft, aber nicht schwarz, und Tageslicht reicht ein Stück in einen Höhleneingang.
  - Feuer 1,0; Lava und flüssiges Metall 0,8; Brennendes und Glut 0,7; alles ab 480 °C glüht, je heißer, desto heller (heißes Metall, heißer Sand, heißer Fels); Glühwürmchen 0,35. Licht von Quellen fällt schneller ab (86 % pro Block).
- **Was Licht durchlässt** (Feld `light_pass` pro Zelle):
  - Luft ganz;
  - Wasser 0,985, Salzwasser 0,98 (ein 20 Zellen tiefer Teich ist unten etwa halb so hell);
  - Glas 0,99, Eis 0,95;
  - Laub 0,75, Ranke und Moos 0,6;
  - Rauch 0,85, Dampf 0,93.
  - Feste Stoffe, Pulver und Fels halten es auf. Ein Block lässt den kleineren Wert aus „quer durch“ und „senkrecht durch“ durch; so hält eine Wand Licht auf, egal wie sie im Raster liegt.
- **Höhlen ohne Lichtquelle sind wirklich dunkel:** Die Grotte unter dem Hügel ist schwarz bis auf die Glühwürmchen, die Lavahöhle leuchtet von innen.
- **Pflanzen wachsen nur mit Licht:** Gras braucht 0,45, Samen 0,35, Bäume 0,3, Ranken 0,25. Moos ist die Ausnahme aus 4.2.2: Es wächst nur unter 0,2.
- **Darstellung:**
  - Luft, Wasser und Glas zeigen das echte Licht, ebenso der Hintergrund dahinter.
  - Selbstleuchtendes (Feuer, Glut, Lava, Glühendes, Glühwürmchen) wird nicht abgedunkelt.
  - Rauch in einer dunklen Höhle ist dunkel.
  - Die Helligkeit folgt dem Auge (1 − (1 − Licht)²): Halbschatten wirkt hell, Dunkel bleibt dunkel.

**Flora (4.2, Flora 1 bis 6)**

- **Wächst langsam:** Statt jede Pflanze in jedem Schritt anzusehen, bekommen pro Schritt 1093 zufällig gewählte Zellen einen „Zufallstick“ (eine pro 64 Zellen), also jede Zelle etwa einmal pro Sekunde. Pflanzen halten ihren Chunk deshalb nicht wach.
- **Brennt gut:** Gras zündet bei 230 °C, Laub bei 240 °C, Welkes bei 200 °C; Flammen und Rauch wie Holz. Ein Grasbrand läuft den ganzen Hang entlang.
- **Hält Erde zusammen:** Erde unter oder neben Wurzeln (Gras, Moos, Ranke, Stamm, bis drei Zellen tief) rutscht nicht und wird nicht weggespült, auch nass nicht. Eine nasse Erdwand mit Grasnarbe steht, eine nackte sackt zusammen.
1. **Gras** ist Erde mit Gras darauf. Es breitet sich auf feuchte Erde daneben aus (eben, eine Stufe hinauf oder hinab), nur an der Oberfläche und nur im Licht, etwa eine Zelle alle 3 bis 4 Sekunden je Rand. Auf trockener Erde und im Dunkeln wächst es nicht. Halme werden in die Luft darüber gezeichnet.
2. **Moos** wächst an Wänden, Böden und Decken dunkler Höhlen, so weit es feucht ist: bis zu 6 Zellen von Wasser oder nassem Grund entfernt.
3. **Ranken** suchen in 30 Zellen Umkreis das nächste Wasser und wachsen Zelle für Zelle zur Wasseroberfläche, über Kanten hinweg und um Hindernisse herum; ohne Wasser in Reichweite hängen sie herab. Sie sind fest: An Fels verankert tragen sie als lebende Brücke oder Leiter. Bis 64 Zellen lang, mit gelegentlichen Verzweigungen.
4. **Samen** keimen auf feuchter Erde (oder Gras) im Licht, zu 60 % zum Baum, sonst zum Busch.
5. **Bäume** wachsen an der Spitze: ein Stamm von 14 bis 21 Zellen, im oberen Teil Äste zur Seite, am Ende eine Krone aus Laub, an den Astenden kleine Kronen. Stamm, Äste und Laub sind fest, also eine Struktur nach 3.1:
   - Am Fuß abgesägt fällt der Baum als Ganzes, mit jedem Blatt.
   - Brennt der Stamm unten durch, kommt der obere Teil herunter. Meist hat das Feuer die Krone dann schon erfasst, weil Flammen am Stamm hochsteigen.
   - Neue Zellen gehören sofort zur Struktur des Baums. So muss beim Wachsen nicht neu nach Strukturen gesucht werden; die Suche läuft nur, wenn ein Baum an Fels oder eine andere Struktur stößt.
   - Ausgewachsene Bäume lassen ab und zu einen Samen fallen.
6. **Absterben:**
   - Gras, Moos, Ranken, Samen und Laub sterben über 60 bis 70 °C und neben Salz oder Salzwasser. Gras wird zu Erde (nass bleibt nass), der Rest zu „Welkes“, einem leichten, gut brennbaren Pulver.
   - Ein Stamm in Salzwasser (oder über 150 °C) wird zu Totholz. Das Sterben wandert den Stamm hinauf, und Laub neben Totholz welkt.

**Fauna (4.2, Fauna 1 bis 4)**, Tiere sind Zellen wie alles andere, mit einfachen Regeln:
1. **Fische** schwimmen in Süßwasser hin und her, ab und zu auf und ab, und wenden an Hindernissen. In Salzwasser oder über 38 °C sterben sie sofort. An Land zappeln sie, fallen und ersticken nach 4 Sekunden. Was bleibt, ist ein toter Fisch, der treibt und nach 40 Sekunden verschwindet. Gezeichnet mit Schwanzflosse.
2. **Glühwürmchen** schwirren durch die Luft, geben schwaches Licht (0,35) und blinken. In hellere Bereiche fliegen sie nur selten: Freigelassen an der Grenze zwischen Höhle und Tageslicht sammeln sie sich im Dunkeln.
3. **Würmer** kriechen langsam durch Erde, meist seitwärts, und lockern sie (Markierung pro Zelle). Nasse lockere Erde gibt ihr Wasser an trockene Erde darunter weiter; so sickert Wasser auf Wurmerde viel tiefer ein, eine Pfütze darauf verschwindet. Außerhalb der Erde trocknen Würmer nach 15 Sekunden aus.
4. **Vögel** sitzen auf Bäumen und Brücke, hüpfen selten. Sehen sie Feuer oder Rauch in 14 Zellen Umkreis, fliegen sie auf und davon, aus der Welt hinaus (nur Stimmung). Gezeichnet mit Kopf, im Flug mit schlagenden Flügeln.

**Startlandschaft:** Gras auf dem Hügel, am Teich ist die Erde feucht. Zwei Bäume und ein Busch, eine Ranke wächst vom unteren Baum zum Teich. Sechs Fische im Teich, sechs Würmer in der Erde, drei Vögel. Unter dem Hügel eine geschlossene, dunkle Grotte mit Wasser, Moos an den feuchten Wänden und acht Glühwürmchen.

**App:** Neue Kategorie **Leben** in der Leiste: Samen, Gras, Moos, Ranke, Fisch, Glühwürmchen, Wurm, Vogel. „Leistung messen“ misst zusätzlich die voll aktive Welt mit Pflanzen, Tieren und Licht und das Bild in der Pause.

**Tests** (alle grün, 65 in `sim`):
- `LightTest`: Tageslicht, geschlossene Höhle dunkel, Öffnung lässt Licht ein; Wasser und Glas lassen durch, Stein nicht; Feuer, Glut, Lava, heißes Metall und Glühwürmchen erhellen eine dunkle Höhle.
- `FloraTest`: Gras nur auf feuchter Erde und im Licht; Moos nur im Dunkeln; Ranke erreicht Wasser und bleibt am Fels; Samen wird zu Baum oder Busch, nicht im Dunkeln und nicht ohne Wasser; Baum fällt abgesägt als Ganzes; durchgebrannter Stamm; Absterben durch Salzwasser und heißen Sand, Baum in Salzwasser; Wurzeln halten eine nasse Erdwand.
- `FaunaTest`: Fische schwimmen und bleiben im Wasser; sterben in Salzwasser, heißem Wasser, an Land; Glühwürmchen sammeln sich im Dunkeln; Würmer lassen Wasser einsickern; Vögel fliehen vor Feuer.

Bilder in `docs/phase2/`:
- `sandbox_start.png`: die Startlandschaft;
- `life_hill.png`, `life_minute.png`: der Hang am Start und eine Minute später;
- `life_fire.png`, `life_after_fire.png`: Feuer im Baum am Hang, der Grasbrand läuft den Hang hinauf;
- `life_grotto.png`: die dunkle Grotte;
- `tree.png`, `tree_cut.png`: ein Baum, abgesägt;
- `vine.png`: eine Ranke wächst vom Fels zum Wasser;
- `seed_grown.png`: ein Samen ist zum Busch geworden;
- `materials.png`: alle Materialien, ohne Licht gezeichnet.

### Abweichungen mit Begründung

1. **Gras ist ein Erdmaterial („Erde mit Gras“), keine Zelle Halm über der Erde.** Als eigene Zelle in der Luft wäre jedes Grasbüschel für Wasser eine Wand. So fließt Wasser über die Wiese, und Gras stirbt zu Erde zurück. Die Halme sind nur gezeichnet.
2. **Moos wächst gerade im Dunkeln.** Der Leitfaden sagt „Pflanzen wachsen nur mit Licht“ und „Moos wächst in dunklen, nassen Höhlen“. Moos ist hier die Ausnahme (`light_max` 0,2); alle anderen brauchen Licht.
3. **Feste Stoffe werden im Dunkeln nicht schwarz, sondern höchstens auf 58 % abgedunkelt** (Schnittfläche). Ganz physikalisch wäre der ganze Fels unter der Oberfläche schwarz, mit allen Erzadern. Höhlen selbst (Luft, Wasser, Hintergrund) sind wirklich dunkel. Für die Pflanzen gilt nur das echte Licht.
4. **Licht in Blöcken zu 2 × 2 Zellen, alle 8 Schritte neu.** Zellgenau und in jedem Schritt wäre es etwa 30-mal so teuer. Beim Zeichnen nutzt die App das Licht der Simulation, solange sie läuft (es kann bis zu 8 Schritte alt sein, das sieht man nicht). In der Pause rechnet das Bild sein eigenes, damit Platziertes sofort leuchtet.
5. **Neue Materialien über die Liste hinaus:** Totholz (abgestorbener Stamm), Welkes (abgestorbene Pflanzen), toter Fisch. Ohne sie müssten Pflanzen und Fische einfach verschwinden.
6. **Neue Felder:**
   - `organism` (Verhalten);
   - `light_need`, `light_max`, `grow`, `grow_size`, `grows_on`;
   - `lives_in`, `move`;
   - `dies_above`, `dies_in_salt`, `death_product`;
   - `roots`, `light_pass`.

   Das Verhalten selbst (wie ein Baum wächst, wie ein Fisch schwimmt) steht im Code in `Life.kt`. Allgemeine Regeln für Lebewesen statt Rezepte; die Werte stehen in der Datei.
7. **Tiere halten ihren Chunk wach,** weil sie sich in jedem Schritt bewegen können. Die Startlandschaft hat deshalb 51 statt 33 wache Chunks (Teich, Grotte, Hang). Das kostet die meiste zusätzliche Zeit im Spielplatz.
8. **Glühwürmchen suchen das Dunkel nicht aktiv,** sie meiden hellere Plätze. Im gleichmäßigen Tageslicht schwirren sie frei herum; geraten sie ins Dunkel, bleiben sie dort.
9. **Vögel kommen nicht zurück**, wenn sie einmal weggeflogen sind (Stimmung, kein Spielelement).
10. **Zwei ältere Tests waren vom Zufall abhängig** („Salz schmilzt Eis und Schnee“, „Salzwasser siedet“). Die Zufallsticks verbrauchen Zufallszahlen, dadurch kippten sie. Sie vergleichen jetzt mit einer Kontrollgruppe ohne Salz und zählen Siedevorgänge statt sichtbaren Dampf. Am Verhalten hat sich nichts geändert.
11. **Der Leistungstest wärmt jetzt 300 statt 60 Schritte vor.** Mit 60 fiel das Nachübersetzen des Just-in-time-Compilers in die gemessenen Schritte; die Werte schwankten um das Dreifache.

### Leistung

Desktop (Linux, Java 21, 4 Kerne), je 600 Schritte nach 300 zum Vorwärmen, je zwei Läufe; Phase 2a auf derselben Maschine mit demselben Verfahren nachgemessen:

| Welt | Phase 2a | Phase 2b |
|---|---|---|
| Leere Welt | 0,00 ms | 0,10 ms (Licht alle 8 Schritte, Zufallsticks) |
| Leere Welt, alle Chunks wach | 0,26 bis 0,31 ms | 0,33 bis 0,37 ms |
| Voll aktive Welt (alle 77 Chunks wach) | 3,51 bis 3,63 ms | 3,89 bis 4,13 ms |
| **Voll aktive Welt mit Pflanzen, Tieren und Licht** | – | **4,02 bis 4,28 ms** |
| Spielplatz mit Feuer, Sand und Wasser | 0,91 bis 1,06 ms | 1,81 bis 1,86 ms |
| Bild erzeugen, laufend | 1,23 bis 1,39 ms | 1,30 bis 1,82 ms |
| Bild erzeugen in der Pause (eigenes Licht) | – | 1,83 bis 2,55 ms |

- Das Licht kostet pro Berechnung 0,4 ms (leere Welt) bis 1,2 ms (mit Lava und Feuer). Alle 8 Schritte macht das 0,05 bis 0,15 ms pro Schritt.
- Die voll aktive Welt mit Pflanzen, Tieren und Licht liegt bei rund 4 ms, also bei der Hälfte der Grenze von 8 ms (6.1) – auf diesem Rechner, der in Phase 1 etwa halb so schnell war wie das Galaxy Z Fold2 (voll aktive Welt 4,4 ms hier gegen 2,0 ms auf dem Handy).
- **CI-Rechner** (Java 17, beta-64): voll aktive Welt 6,43 ms, mit Pflanzen, Tieren und Licht 10,19 ms, Spielplatz 2,57 ms, Bild 2,49 ms (in der Pause 5,99 ms). Dieser Rechner schwankt stark und ist deutlich langsamer als das Handy: Für Build beta-60 maß er 7,46 ms für die voll aktive Welt, das Galaxy Z Fold2 2,01 ms. Umgerechnet ergibt das auf dem Handy etwa 2,8 ms für die Welt mit Pflanzen, Tieren und Licht.
- **Bitte auf dem Handy erneut „Leistung messen“.** Die neue Zeile „Voll aktive Welt mit Pflanzen, Tieren und Licht“ ist der Wert für 6.1. Liegt er über 8 ms, ist der erste Hebel, das Licht seltener zu rechnen (alle 16 statt 8 Schritte), der zweite, Tiere nur jeden zweiten Schritt ziehen zu lassen.

**Build:** [`beta-64`](https://github.com/shootcat/ReAct/releases/tag/beta-64). CI grün. Screenshots `12_leben` (lebende Startwelt) und `13_hoehle` (dunkle Grotte mit Glühwürmchen, herangezoomt, Kategorie „Leben“ in der Leiste) geprüft.

## Phase 2c: Beschaffen und Entdeckungsbuch

### Zusammenfassung

**Spielplatz-Modus (4.3):** Im Menü schaltet „Modus: nur Beschaffen“ zwischen dem unendlichen Vorrat (bisher) und dem Beschaffen um. Beim Beschaffen zeigt die Leiste nur noch die Kategorien **Werkzeug** (Eimer, Hand) und **Lager**. Pinsel, Bauformen und Radierer aus dem Nichts gibt es dann nicht. Die Kopfzeile zeigt „Nur Beschaffen · Eimer 120/200“. Auch im unendlichen Modus stehen Eimer und Hand unter „Werkzeug“ bereit.

**Eimer**
- „Schöpfen“: Der Finger nimmt lose und flüssige Stoffe auf, **eine Sorte pro Ladung** (was unter dem Finger liegt), bis der Eimer voll ist. Die Größe ist pro Level einstellbar (`Session.bucketSize`), im Spielplatz 200 Zellen.
- **Nimmt nicht auf** (Feld `bucketable` plus Regeln):
  - Feuer und alles, was brennt (auch Glut);
  - Lava und flüssiges Metall;
  - Gase;
  - Fels und vom Level verankerten Boden;
  - feste Strukturen.
  Ein Test prüft alle neun Fälle.
- **Behält die Temperatur:** Beim Schöpfen mittelt sich die Temperatur des Inhalts. Im Eimer kühlt er langsam auf die Umgebung ab (Hälfte des Unterschieds in etwa 20 Sekunden). Heißer Sand kommt nach 5 Sekunden noch mit über 300 °C wieder heraus.
- „Gießen“: verteilt den Inhalt in die Luft unter dem Finger, mit der Temperatur aus dem Eimer. Danach gelten die normalen Regeln: Wasser aus dem Eimer auf Lava kocht und lässt sie zu Stein erstarren.

**Hand**
- Greift mit einem Tippen das Objekt unter dem Finger:
  - eine ganze lose Struktur (Stamm, Stein, Metall, Glas, Ziegel, Eis, Totholz, Holz);
  - oder einen Klumpen Holzkohle (bis 30 Körner).
- **Grenzen:**
  - „Das ist fest verankert“: in Fels eingebaut, also seitlich oder von oben berührt. Was nur auf dem Boden liegt, lässt sich heben.
  - „Zu schwer“: über 120 Einheiten (Dichte / 1000 je Zelle). Ein Stamm von 170 Zellen geht, ein Stein von 46, ein Metallbarren von 15.
  - „Zu heiß zum Anfassen“: über 60 °C, oder es brennt.
  - „Das lässt sich nicht greifen“: etwa ein Baum mit Laub.
- Beim Ziehen zeigt eine halbtransparente Vorschau, wo das Objekt landet. Losgelassen wird es dort abgelegt, wenn Platz ist, und fällt dann nach 3.1.
- **Ins Lager / Zu Brettern:** Ein Stamm in der Hand ergibt **3 Bretter (Holz) pro Zelle** (`split_into`, `split_yield`), Totholz 2. Stein, Metall, Glas, Ziegel und Eis gehen 1 : 1 ins Lager.

**Bauen aus Beschafftem:** Beim Beschaffen bauen Pinsel, Linie, Rechteck und Rahmen nur mit Material aus dem Lager und verbrauchen es Zelle für Zelle. Eine Linie, die länger ist als der Vorrat, endet, wo das Holz ausgeht.

**Rückgängig und Protokoll:** Eimer, Hand, Lager und Modus gehören zum Zustand.
- Jeder Handgriff ist rückgängig zu machen, Rückgängig gibt auch Bretter und Stamm zurück.
- Alles steht im Protokoll und spielt deterministisch gleich ab (Test `procuring plays back the same`).

**Startlandschaft:** Auf der Düne liegen jetzt ein umgestürzter Stamm (28 Zellen, ergibt 84 Bretter) und ein loser Stein zum Tragen. Der Holzstapel auf dem Hügel ist als Ganzes zu schwer.

**Entdeckungsbuch**
- **54 Einträge** in `sim/src/main/resources/discoveries.json`, je eine Eigenschaft eines Materials:
  - „Sand behält Wärme“, „Wasser löscht Feuer“, „Salz taut Eis und Schnee auch in der Kälte“;
  - „Holz brennt nicht, solange es nass ist“, „Metall leitet Wärme sehr gut“;
  - „Fisch lebt nur in Süßwasser“, „Vogel flieht vor Feuer und Rauch“ …
  - Nie „X + Y → Z“; ein Test prüft, dass kein Eintrag wie ein Rezept aussieht.
- **Wie entdeckt wird:**
  - Die Welt zählt pro Material, was ihm passiert (`World.seen`): entzündet, verbrannt, gelöscht, erstickt, verkohlt, geschmolzen, vom Salz getaut, gesiedet, Salz zurückgelassen, erstarrt, kondensiert, gelöst, aufgesogen, weggespült, eingesickert, gewachsen, an Hitze oder Salz gestorben, erstickt, geflohen, gegraben, gefallen.
  - Dazu sieht eine Abtastung alle halbe Sekunde ein Viertel der Zeilen an. Sie erkennt, was nur der Zustand zeigt: behält Wärme (heiß und heißer als alle Nachbarn), glüht, schwimmt, trägt Wärme weiter (Metall), leuchtet im Dunkeln, nass und heiß, ohne zu brennen.
  - Zählen und Abtasten ändern die Welt nicht (Test).
- **Einblendung:** Ein neuer Eintrag erscheint oben über der Welt als „Neu: Sand behält Wärme“, mit leisem Ton. Mehrere kommen nacheinander, je etwa 2,5 Sekunden. Dieselbe Einblendung meldet auch, was Hand und Eimer tun („Zu schwer“, „Ins Lager: 84 × Holz“).
- **Das Buch** (Menü → Entdeckungsbuch): alle Materialien mit ihren Einträgen.
  - Entdecktes steht als Satz da, Unentdecktes als „…“; Materialien ohne Entdeckung heißen „???“.
  - Ein Tippen auf einen Eintrag spielt seine **kurze Animation**: eine kleine eigene Welt von 48 × 32 Zellen, 20 Sekunden in Schleife, die genau diese Eigenschaft zeigt.
  - Für jeden der 54 Einträge prüft ein Test, dass seine Szene die Eigenschaft innerhalb der 20 Sekunden wirklich auslöst.
  - Gefundene Einträge werden dauerhaft gespeichert.

**Tests** (alle grün, 77 in `sim`):
- `SupplyTest` (7): Eimer, verbotene Stoffe, Temperatur und Mischen, Hand, Grenzen der Hand, Lager und Bauen, Wiedergabe.
- `DiscoveryTest` (5): Eigenschaften statt Rezepte, jede Szene zeigt ihren Eintrag, jeder Eintrag wird einmal gemeldet, Beobachten ändert nichts, Bilder.
- App-Rauchtest `procureAndBook`:
  - Modus umschalten;
  - Stamm greifen (`14_hand`), zu Brettern machen, eine Linie bauen (`15_lager_gebaut`);
  - Wasser aus dem Teich schöpfen und auf den Hügel gießen (`16_eimer`);
  - das Buch (`17_buch`) und eine Szene (`18_buch_szene`).

Bilder: `docs/phase2/book_scenes.png` zeigt sechs Szenen, wie das Buch sie zeigt: Sand behält Wärme, Salz taut Eis, nasses Holz, durchbrennender Stamm, Moos im Dunkeln, Vogel flieht.

### Abweichungen mit Begründung

1. **„Verankert“ heißt für die Hand: in Fels eingebaut** (seitlich oder von oben berührt). Nach der Strukturregel 3.1 gilt alles als verankert, was Fels berührt, auch ein Stamm, der auf dem Felsboden liegt. Den muss man aber aufheben können. Wofür 3.1 gilt (stehen oder fallen), ändert sich nicht.
2. **Gewicht als Summe der Dichten** (120 Einheiten), Temperaturgrenze 60 °C. Beides sind feste Werte in `Session`, bei Bedarf pro Level einstellbar wie die Eimergröße.
3. **Ins Lager kommen nur feste Objekte.** Ein Klumpen Holzkohle lässt sich tragen und ablegen, aber nicht lagern; loses Material transportiert der Eimer.
4. **Eis und Schnee brauchen jetzt Schmelzwärme** (`latent_heat` 160). Ohne sie schmolz ein Eisblock in Wasser von 20 °C in einer halben Sekunde, „Eis schwimmt“ war nicht zu sehen. Salz taut weiter ohne Wärme.
5. **Die Animationen sind kleine echte Simulationen**, keine gezeichneten Filme. Dunkle Szenen (Moos, Glühwürmchen) sind im Buch etwas aufgehellt (Grundhelligkeit 0,3 statt 0,05), sonst wäre nichts zu sehen.
6. **„Beobachtet“ heißt: in der Welt passiert.** Der Spielplatz zeigt fast immer die ganze Welt. Deshalb melden sich gleich nach dem Start ein paar Einträge von selbst, etwa „Fels glüht in großer Hitze“ an der Lavahöhle oder „Glühwürmchen leuchtet im Dunkeln“ in der Grotte. Ob nur der sichtbare Ausschnitt zählen soll, ist für die Level zu entscheiden.
7. **Bauen beim Beschaffen geht nur mit festen Stoffen aus dem Lager.** Lose Stoffe und Flüssigkeiten kommen ausschließlich aus dem Eimer.

### Leistung

Der Container wurde zwischendurch neu gestartet, der Rechner ist ein anderer. Deshalb sind 2b und 2c auf dieser Maschine mit demselben Verfahren nachgemessen (Linux, Java 21, 4 Kerne, je zwei Läufe):

| Welt | Phase 2b | Phase 2c |
|---|---|---|
| Voll aktive Welt (alle Chunks wach) | 4,91 bis 5,21 ms | 4,73 bis 5,18 ms |
| Voll aktive Welt mit Pflanzen, Tieren und Licht | 5,84 bis 6,34 ms | 5,31 bis 5,87 ms |
| Spielplatz mit Feuer, Sand und Wasser | 2,22 bis 3,48 ms | 2,29 bis 2,65 ms |
| Bild erzeugen | 2,78 bis 2,93 ms | 2,21 bis 2,98 ms |

- Kein messbarer Unterschied. Das Zählen der Beobachtungen ist je ein Arrayzugriff an Stellen, an denen ohnehin ein Phasenwechsel passiert.
- Die Abtastung fürs Buch läuft nur in der App, alle 30 Schritte über ein Viertel der Zeilen. Eimer und Hand rechnen nur, wenn der Finger sie benutzt.
- Die Grenze von 8 ms (6.1) bleibt eingehalten; auf dem Handy bitte wieder „Leistung messen“.

- CI-Rechner (Java 17, beta-66): voll aktive Welt 7,03 ms, mit Pflanzen, Tieren und Licht 9,22 ms (beta-64: 10,19 ms), Spielplatz 2,41 ms. Dieser Rechner ist etwa 3,7-mal langsamer als das Galaxy Z Fold2 (siehe 2b).

**Build:** [`beta-66`](https://github.com/shootcat/ReAct/releases/tag/beta-66). CI grün. Screenshots geprüft:
- `14_hand`: Stamm in der Hand, „Zu Brettern“ angeboten;
- `15_lager_gebaut`: die Bretterlinie, das Lager danach leer;
- `16_eimer`: Wasser aus dem Teich auf dem Hügel;
- `17_buch`: 11 von 54 entdeckt;
- `18_buch_szene`: Glühwürmchen im Dunkeln.

**Hier stoppe ich und warte auf die Freigabe** (Leitfaden V3, 4.3 und 8).


## Phase 2d: Fehler, neue Materialrollen, Druck, Welt und Bedienung

### Zusammenfassung

**4a.1 Fehler**

1. **Schwebende Strukturen.** Ursache gefunden: Ein frei gemalter Block fiel durchaus, blieb aber in zwei Fällen hängen. Erstens wurde er Teil eines Baums, sobald er Laub oder eine Ranke berührte, und der Baum (auf seinen Wurzeln stehend) trug ihn. Zweitens blieb eine Struktur liegen, die nur mit einer Ecke auf etwas auflag. Behoben mit drei Regeln:
   - Pflanzen und gebautes Material bilden nie eine gemeinsame Struktur (auch die Hand greift sie getrennt).
   - Laub und Ranken tragen nichts Schweres: Ein fallender Block reißt sie ab, sie fallen als welkes Laub.
   - Liegt der Schwerpunkt einer losen Struktur neben ihrer Auflage, kippt sie über die Kante. Ohne Drehung (die kommt mit echter Körperphysik) rutscht sie schräg oder seitlich ab. Bäume mit Laub halten ihre Wurzeln.

   Tests: „Block in der Luft malen, nach wenigen Schritten liegt er“ für Metall, Glas, Eis, Ziegel, Stein und Holz, über die Pinselbedienung wie in der App, dazu Ecke, Baumkrone und Brett am Stamm.
2. **Tiere werden gesetzt, nicht gemalt.**
   - Ein Tippen setzt ein Tier, ein Strich alle 12 Zellen eines; Linie, Rechteck und Rahmen verteilen Tiere ebenso einzeln.
   - Ein Tier sucht sich im Umkreis von 3 Zellen seinen Lebensraum: der Fisch Wasser, der Wurm Erde.
   - Fische an Land zappeln einzeln: Sie fallen, rutschen vom Haufen, hüpfen. Nach 4 Sekunden ersticken sie, der tote Fisch verrottet in etwa 40 Sekunden.
   - Ursache des „festen Haufens“ war, dass ein Fisch neben einem anderen Fisch als „im Wasser“ galt.
3. **Basalt.** Neues Material: dunkel, mit Gasporen aus ein bis drei Zellen, unregelmäßig. Erkaltete Lava wird zu Basalt, die Kruste der Lavahöhle ist Basalt. Er gehört zu Stein (schmilzt zu Lava, zerbricht zu Geröll).
4. **Mindesthelligkeit.** Unbeleuchtete Höhlen zeigen die Höhlenwand gedämpft:
   - Grundhelligkeit 0,25 statt 0,05;
   - der Hintergrund bekommt mindestens 70 % seiner Helligkeit;
   - die Höhlenwand des Hintergrunds ist etwas heller.

   Test: Höhlenwand, Wasser und Fels im Dunkeln sind sichtbar, aber deutlich dunkler als bei Tag.

**4a.2 Glas, Thermoschock.** Als Eigenschaften statt als Paarregel:
- Glas ist spröde (`shock_product`: Glasscherben, `shock_rate` 20). Wasser, Salzwasser, Eis und Schnee schrecken ab (`quenches`).
- Nimmt ein abschreckender Nachbar dem Glas in einem Schritt mehr als 20 Grad ab, springt es. Das passiert ab etwa 150 °C.
- Langsames Abkühlen an der Luft lässt Glas ganz.
- Glasscherben sind ein durchlässiges Pulver und schmelzen bei 1000 °C wieder zu Glas.

**4a.3 Kies und Schnee**
- **Kies (und Glasscherben) sind durchlässig.** Wasser läuft in die Poren (das Nass-Flag hält eine Zelle Wasser). Von dort geht es an trockene Körner weiter oder tritt ins Freie aus, nach unten zuerst. So sickert Wasser durch Kiesdämme bis an ihren Fuß und durch ein Kiesbett in eine Höhle darunter. Sand bleibt auf dem Kies liegen, Wasser geht verloren nicht.
- **Lehm ist wasserdicht.** Er wird an der Oberfläche nass und weich, lässt aber nichts durch; Sand und Erde ebenso (nur Wurmgänge lassen sickern, wie bisher).
- **Schnee isoliert** (Wärmeleitung 0,004 statt 0,08). Ein Block unter Schnee erwärmt sich in 5 Sekunden um weniger als ein Drittel dessen, was er unter Geröll gewinnt. Schnee schmilzt in warmer Luft nur langsam und ist so eine kleine, stetige Wasserquelle.
- **Schnee wird unter Last zu Eis**, als Zufallsticks: ab einer Last von 3 (Dichte/1000 der Zellen darüber, etwa ein Stein oder zehn Zellen Schnee).
- **Lawine.** Schnee, der warm wird (ein Teil seiner Schmelzwärme aufgenommen) oder erschüttert wird, kommt los. Erschütterung heißt: ein Block landet schnell in der Nähe, oder eine Explosion. Loser Schnee fließt wie eine zähe Flüssigkeit hangabwärts und reißt Schnee darüber und daneben mit; auf ebenem Grund kommt er zur Ruhe.

**4a.4 Druck und Explosionen**
1. **Druck pro Gasbereich.** Zusammenhängende Gaszellen (Luft, Dampf, Rauch, Grubengas) ohne Verbindung zum offenen Himmel sind ein geschlossener Bereich. Die Bereiche werden alle 10 Schritte neu gesucht, und nur, wenn in der Welt etwas in Bewegung ist. Der Druck ist Gasmenge mal absolute Temperatur je Zelle, bezogen auf die Luft draußen. Dampf zählt sechsfach: Er ist stark ausgedehntes Wasser.
2. **Festigkeit** ist eine Materialeigenschaft:

   | Glas | Lehm | Eis | Holz | Ziegel | Stein, Basalt | Metall | Fels |
   |---|---|---|---|---|---|---|---|
   | 2 | 3 | 4 | 5 | 6 | 9 | 14 | nie |

   Pulver und Flüssigkeit, die einen Ausgang verstopfen, halten 2,5. Eine Pfütze oder ein Haufen am Boden ist keine Wand. Übersteigt der Druck die schwächste Wand, birst sie: Glas zu Scherben, Ziegel und Stein zu Geröll, Eis zu Schnee.
3. **Explosion:** eine Druckwelle mit einer Kraft und einer Reichweite.
   - Sie zerbricht, was schwächer ist als die Kraft an seiner Stelle.
   - Sie setzt Brennbares in Brand und löst Schnee.
   - Sie schleudert Pulver und Flüssigkeit von der Mitte weg.
   - Beim Bersten ist die Kraft der Druck selbst, die Reichweite folgt der Menge des ausströmenden Gases. Bei Grubengas folgen beide der Größe der Wolke.
4. **Dampfexplosion.** Wasser neben etwas über 600 °C (Lava) verdampft schlagartig und nimmt die Verdampfungswärme der Lava ab. In einem geschlossenen Raum steigt der Druck, die schwächste Wand birst. Im Freien zieht der Dampf einfach ab.
5. **Grubengas** ist leichter als Luft und sammelt sich unter Decken. Zu sehen ist es kaum, die Luft darüber flimmert leicht. Es zündet an Feuer, Glut oder ab 580 °C als ganze Wolke: ein Feuerblitz und eine Explosion. Die **Gasspalte** ist Levelgelände wie Fels und gibt ab und zu Grubengas ab.
6. **Schwarzpulver** ist nicht umgesetzt, der Vorschlag steht in `docs/ELEMENTE.md`. Es wäre ein Rezept aus drei Stoffen, von denen zwei sonst keine Rolle hätten. Der Vorschlag: Salpeter als Eigenschaft („lässt Brennbares ohne Luft brennen“).

Neu im Entdeckungsbuch: 8 Einträge mit Szenen (62 statt 54):
- Glas springt beim Abschrecken; Glas gibt unter Druck als Erstes nach;
- Kies und Glasscherben lassen Wasser durch;
- Schnee wird unter Last zu Eis; Schnee rutscht am Hang ab;
- Dampf baut in geschlossenen Räumen Druck auf;
- Grubengas zündet explosionsartig.

**4a.5 Welt und Bedienung**
1. **Breite Welt.** Größen: Klein 320 × 240, Mittel 480 × 300 (Standard), Groß 600 × 340.
   - Die Ansicht füllt die Höhe; die Welt ist im Hochformat zwei bis drei Bildschirmbreiten breit.
   - Zwei Finger verschieben und zoomen, hinaus bis zur ganzen Welt.
   - Gezeichnet wird nur der sichtbare Ausschnitt mit 24 Zellen Rand; ein Test prüft, dass er pixelgleich zum Gesamtbild ist.
2. **Hoch- und Querformat.** Die Drehsperre ist entfernt.
   - Im Querformat steht die Bedienung als Spalte neben der Welt: Kopfzeile, Kategorien, Materialien als umbrechendes Raster.
   - Der Titelbildschirm hat ein eigenes Querformat.
3. **Vorlagen** über „Neue Welt …“ im Menü; Vorlage und Größe werden gemerkt. Jede Welt hat ihre eigene Umgebungstemperatur.
   - **Wiese mit Teich**: die bisherige Startwelt, unverzerrt in der Mitte, mit Hügeln und Bäumen links und Dünen rechts.
   - **Leeres Feld**.
   - **Insel**: Felsenhügel mit Erde, Bäumen und Stränden im Salzwasser.
   - **Küste**: Hügel mit Bäumen, Steilküste mit kleiner Höhle, Strand, Meer.
   - **Berg mit Schnee**: kalte Luft (−6 °C), zwei Gipfel, Schneekappe, gefrorener Teich im Tal, Geröll am Fuß.
   - **Vulkan**: Basaltkegel, Krater mit Lava, Schlot, heiße Magmakammer, Meer.
   - **Höhlensystem**: Gänge und Kammern mit Teichen, Kies, Moos und Glühwürmchen, eine Gasspalte mit Grubengas unter der Decke, eine von Fels versiegelte Lavatasche.
4. **Bedienleiste.** Ausgeklappt zwei Zeilen: Kategorien mit Einklappknopf, darunter Materialien oder Werkzeuge. Eingeklappt eine schmale Zeile mit dem aktuellen Werkzeug. Tempo (Pause, Normal, Schnell) und Pinselform (ein Knopf, öffnet Formen und Größen) sitzen klein in der rechten unteren Ecke der Welt.

Bilder:
- `docs/phase2/2d_physik.png`: Block durch Baumkrone, Fische an Land, abgeschrecktes Glas, Kiesdamm, Lawine, Dampfexplosion, Grubengas, gedämpfte Höhle;
- `docs/phase2/2d_vorlagen.png`: die sieben Vorlagen;
- `docs/phase2/2d_materialien.png`: alle Materialien, unten Basalt neben Stein und Ziegel, Glasscherben, Glas, Kies, Schnee, Eis, Lehm.

### Abweichungen mit Begründung

1. **Kippen statt Drehen.** Eine Struktur, deren Schwerpunkt über die Kante ragt, rutscht ab, ohne sich zu drehen. Echtes Kippen braucht die Körperphysik aus Phase 4.
2. **Seitlich in Fels gebaute Strukturen halten weiter** (Regel 3.1.2 und Brücken nach 4.1.6). Ein Block, der eine Felswand berührt, ist verankert. Falls das als „Schweben“ empfunden wird, ist der nächste Schritt eine Tragfähigkeit pro Haftstelle; das gehört zu Phase 4.
3. **Lawinenauslöser „erwärmt“** heißt: Der Schnee hat schon ein Drittel seiner Schmelzwärme aufgenommen. In der Spielplatzwelt mit 20 °C Luft rutscht Schnee an warmem Fels darum nach einigen Sekunden ab. Auf dem Berg ist die Luft deshalb kalt.
4. **Druck aus warmer Luft** zählt mit (doppelte absolute Temperatur, doppelter Druck). Ein geschlossener Raum mit Feuer kann so ein Glasfenster sprengen. Kleine heiße Lufttaschen in Haufen bleiben ruhig: Bersten erst ab 40 Zellen überschüssigem Gas.
5. **Wasser in den Poren des Kieses ist Süßwasser.** Salzwasser bleibt auf Kies stehen, sonst müsste jede Pore die Salzigkeit mitführen.
6. **Größte Welt 600 × 340 statt 640 × 360.** Hochgerechnet lag 640 × 360 auf dem Fold2 bei etwa 7,6 ms, zu knapp an 8 ms (siehe Leistung).
7. **„Normal, Schnell, Pause“ als drei kleine Symbole** in der Ecke statt einer beschrifteten Leiste. Pinselform und Größe sind ein Knopf mit kleinem Menü; das Wort „Pause“ über der Welt entfällt, das Pause-Symbol ist hervorgehoben.

### Leistung

Gemessen auf diesem Rechner (Linux, Java 21, 4 Kerne), Benchmark einzeln, je zwei Läufe. Der Rechner schwankt stark.

| Welt | Phase 2c | Phase 2d |
|---|---|---|
| Voll aktive Welt 200 × 350 | 4,73 bis 5,18 ms | 4,49 bis 5,92 ms |
| Voll aktive Welt mit Pflanzen, Tieren und Licht 200 × 350 | 5,31 bis 5,87 ms | 5,20 bis 5,78 ms |
| **Größte Welt 600 × 340, voll aktiv mit Pflanzen, Tieren und Licht** | – | **15,2 bis 18,3 ms** |
| Größte Welt 600 × 340, voll aktiv | – | 16,5 bis 17,3 ms |
| Größte Welt, Wiese mit Feuer, Sand und Wasser | – | 2,6 bis 3,8 ms |
| Bild der größten Welt, sichtbarer Ausschnitt (ein Drittel) | – | 1,9 bis 3,8 ms |

Aufteilung eines Schritts der größten belebten Welt (lokal, 15,2 ms):

| Zellen | Wärme | Strukturen | Licht | Druck | Fallen | Zufallsticks |
|---|---|---|---|---|---|---|
| 8,7 ms | 4,1 ms | 1,1 ms | 0,7 ms | 0,25 ms | 0,3 ms | 0,1 ms |

- **Hochrechnung aufs Handy:** Das Fold2 brauchte für die belebte Welt 200 × 350 in 2c 2,2 ms, dieser Rechner 5,3 bis 5,9 ms (Faktor etwa 2,5). Die größte Welt kostet hier das 2,9- bis 3,4-Fache der kleinen. Das ergibt auf dem Fold2 **etwa 6,5 bis 7,5 ms** für die voll aktive, belebte größte Welt. Die Grenze von 8 ms ist eingehalten, aber nicht mit viel Luft.
- **Die Strukturen** wurden in der belebten Welt fast jeden Schritt neu gesucht, weil Feuer und Wachstum ständig feste Zellen ändern. Dabei wurde jedes Mal das ganze Besitzer-Feld geleert. Jetzt werden nur die Zellen der letzten Suche zurückgesetzt: 1,1 statt 1,5 ms in der größten Welt. Eine Drosselung auf jeden dritten Schritt hätte weitere 0,7 ms gebracht, hätte aber das Rückgängigmachen ungenau gemacht; das habe ich verworfen.
- **Druck** kostet 0,1 ms (200 × 350) bis 0,3 ms (größte Welt) pro Schritt.
- **Zeichnen:** In der Ansicht ist etwa ein Drittel der größten Welt zu sehen. Gezeichnet wird nur dieser Ausschnitt, das spart rund zwei Drittel.
- **Bitte auf dem Handy „Leistung messen“.** Die Zeile „Größte Welt 600 × 340, voll aktiv mit Pflanzen, Tieren und Licht“ ist der Wert für 4a.5.5. Liegt er über 8 ms, sind die nächsten Hebel:
  1. Licht in großen Welten alle 16 statt 8 Schritte;
  2. die Strukturen gedrosselt, mit den Strukturlisten im Schnappschuss, damit Undo exakt bleibt;
  3. als letztes Mittel eine kleinere größte Welt (560 × 320).
