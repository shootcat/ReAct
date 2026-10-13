# Phase 3b: Bericht

Phase 3b vor Phase 4: Bedienung, Sichtbarkeit von Temperatur und Material, warme Luft, Level 2 und 3 organisch, Medaillen neu gemessen. Phase 4 ist nicht begonnen.

**Build:** [`beta-96`](https://github.com/shootcat/ReAct/releases/tag/beta-96), APK und Screenshots dort.
- beta-95 war schon grün: Level 1 im App-Test Gold in 0:34 mit 1 Griff. beta-96 ändert nur den neuen App-Test. Die Materialinfo zeigt dort die Lava in der Höhle statt Luft, und die Hand-Hervorhebung wird aufgenommen, bevor Feuer den Stamm auf der Düne verbrennt.
- beta-92 bis beta-94 waren rot, nur an App-Tests. sim und level waren immer grün.
  - Der neue Test suchte am Ende die Materialleiste. Im Werkzeug-Reiter heißt sie aber `tool_bar` (ab beta-93 behoben).
  - Level 1 ohne Medaille. Die Diagnose aus beta-94 ergab nach dem Schöpfen: Eimer leer, 0 Griffe. Der Griff war als langes Drücken gewertet worden, also als Materialinfo ohne Handgriff.
  - Ursache: Robolectric hat eine Berührungsschwelle von 16 dp (48 px). Der Test wischte nur 30 px, das zählte nicht als Bewegung. Außerdem läuft die Testuhr beim Warten auf den Bildschirm über die 0,45 s hinaus.
  - Die App verhält sich dabei richtig: ein still gehaltener Finger fragt nur. Der Test wischt jetzt 60 px, auch beim Malen von Feuer, Lava und Wasser für die Screenshots.


## 1. Materialinfo durch langes Drücken

- **Geste:** Ein Finger liegt 0,45 s still auf der Welt. Unten links erscheint eine Karte mit:
  - Name und Farbfeld des Materials,
  - Temperatur in °C,
  - Zustand: glühend (über 500 °C), heiß (über 60 °C, ab da nimmt die Hand nichts mehr), unter null, brennt, nass, verankert, außer Reichweite (nur im Modus „nur Beschaffen“ und im Level),
  - „Eimer: nimmt es“ oder warum nicht: „nimmt der Eimer nicht“, „brennt“, „fest verankert“, „Da kommst du nicht hin“,
  - „Hand: nimmt es“ oder warum nicht: „nicht zu greifen“, „Gelände“, „Zu groß zum Greifen“, „Das ist fest verankert“, „Zu schwer“, „Zu heiß zum Anfassen“, „Da kommst du nicht hin“.
- Der Finger darf weiterwandern; die Karte zeigt, was gerade darunter liegt. Nach dem Loslassen bleibt sie gut 3 s stehen.
- **Kein Handgriff:** Die Abfrage liest nur. Sie erscheint nicht im Aktionsprotokoll, zählt nicht und lässt sich nicht rückgängig machen. Ein zweiter Finger zoomt auch während der Abfrage.
- **Unterscheidung vor jeder Aktion:** Bevor etwas geschieht, entscheidet die Geste, ob es ein Tippen, ein Strich, zwei Finger oder ein langes Drücken ist. Ein Strich beginnt, sobald der Finger sich über die Berührungsschwelle bewegt.
- **Zielkonflikt Halten ↔ langes Drücken:** Früher schüttete ein liegen gelassener Finger weiter. Das geht nicht mehr gleichzeitig mit dem langen Drücken. Lösung:
  - Ein **Tippen** schüttet so lange, wie der Finger lag.
  - Mit dem **Eimer** dauert ein Tippen mindestens einen ganzen Zug (0,45 s = 27 Schritte). Gemessen: Mit Pinsel 1 braucht ein voller Eimer (40 Zellen) Sand 20 bis 27 Schritte zum Ausgießen. Ein gewöhnliches Tippen (6 bis 9 Schritte) gösse nur 11 bis 21 Zellen aus und kostete einen zweiten Handgriff.
  - Ein **Strich** schüttet weiter, solange der Finger danach liegen bleibt. Wer an einer Stelle viel schütten will, wischt kurz und hält dann.
  - Alternative, falls das im Test stört: ein eigener Info-Knopf statt des langen Drückens. Dann käme Halten zum Schütten zurück.
- **Optik:**
  - Salz („crystal“): Facetten und einzelne Körner, die im Takt aufblitzen.
  - Schnee („matte“): weich und matt, die Oberseite hell, in Verwehungen ein bläulicher Schatten, kein Glanz.
  - Metall („sheen“): stärkerer Glanz, mit heller Oberkante.
  - Bild: `docs/phase3b/hoehle.png` (unten).

## 2. Eimer

- Die erste Berührung legt die Sorte fest: was unter dem Finger liegt, sonst das Nächstliegende, das der Eimer nehmen kann.
- Beim Wischen wird nur diese Sorte geschöpft. Liegt der erste Tupfer eines neuen Strichs auf etwas anderem, während schon etwas im Eimer ist, kommt der Hinweis „Im Eimer ist schon Sand“.
- Ein durchgehendes Wischen ist **ein** Handgriff. Gezählt wird das Aufsetzen, nicht die Bewegung.
- Test: `SupplyTest` („one stroke with the bucket is one action, and the first touch decides what it takes“).

## 3. Hand: greifbare Objekte leuchten

- Ist die Hand gewählt, leuchtet alles, was sie gerade nehmen kann: lose, nicht zu schwer, nicht zu heiß, nicht verankert, und im Level in Reichweite.
- Darstellung: gelber Rand, der langsam pulsiert, innen ein Hauch derselben Farbe.
- Neu berechnet wird höchstens alle 30 Schritte (gut zweimal pro Sekunde) oder nach einer Eingabe.
- Bild: Screenshot `38_hand_greifbar`.

## 4. Zoom bleibt in seinem Bereich

- Die Welt wird auf ihren eigenen Kasten beschnitten. Auch stark hineingezoomt zeichnet sie nie über die Leiste, die Ecke oder die Spalte im Querformat.
- Bedienelemente, die über der Welt liegen (Zielanzeige im Level, Materialinfo), fangen alle Berührungen ab. Nichts davon erreicht die Welt.
- Gilt im Level und im Spielplatz. Screenshot `39_zoom_im_rahmen` (achtfach hineingezoomt).

## 5. Entscheidungen

### Eimer ins Lager, auch Lehm

- Im Modus „nur Beschaffen“ und im Level mit Bauwerkzeug erscheint neben dem Eimer die Karte „Ins Lager – aus dem Eimer“, sobald er etwas Loses enthält: Sand, Erde, Kies, Lehm, Salz, Schnee, Geröll, Asche …
- Flüssiges kommt nicht ins Lager („Flüssiges kommt nicht ins Lager“).
- Aus dem Lager wird mit Pinsel, Linie, Rechteck oder Rahmen gebaut, wie mit Brettern.

### Ziegelweg als vierter Weg in Level 3: nicht umgesetzt

Die Idee: Lehm einlagern, eine Rinne oder Brücke daraus bauen, die an der Lava zu Ziegel brennt und die Lava zum Sand führt, wo Holz durchbrennen würde. Ich habe sie in fünf Versuchsreihen gemessen. Keine Anordnung ergibt einen Weg, der sich vom Lavaweg unterscheidet und Glas in die Kammer bringt.

- **Wie weit Lava heiß genug bleibt** (Sand wird ab 1000 °C zu Glas):
  - Aus einer mit Magma ausgekleideten Röhre (3 bzw. 6 Zellen) auf kalten Fels: Lava erreicht 6 bis 7 Zellen, über 1000 °C bleibt sie nur 2 bis 3 Zellen.
  - Auf Ziegelboden: 7 Zellen, über 1000 °C 2 bis 4 Zellen.
  - Nur auf Magma bleibt sie heiß (1240 °C auf 11 bis 15 Zellen). Magma kann der Spieler nicht bauen.
- **Rinne zur Kammer** (10 bis 25 Zellen lang, Wände aus nichts, Lehm oder Holz): Die Lava kommt in allen Fällen gleich weit (Zelle 62 bis 66) und schmilzt in keinem Fall Sand. Die Wand ändert nichts, weil die Lava am Boden auskühlt, nicht an der Wand.
- **Lehm an der Mündung:** Lehm brennt zu 2 bis 11 Zellen Ziegel. Mit Lava entsteht dahinter kein Glas. Mit flüssigem Metall entsteht Glas wie ohne Lehm (bis 8 Zellen); das ist der Erzweg, den es schon gibt.
- **Lehmschicht als Boden unter der Lava:** Sie brennt zu Ziegel (bis 26 Zellen), aber die Lava läuft durch die noch ungebrannten Stellen ab (bis 95 Zellen in der Grube).
- **Holzbühne unter Lava, mit und ohne Lehmdecke** (5, 7, 9 Zellen lang): Das Holz ist nach 5 bis 20 s verbrannt.
  - Ohne Lehmdecke fällt die Lava durch: 5, 16 bzw. 33 Zellen in der Grube.
  - Mit Lehmdecke hält die gebrannte Ziegelhaut sie: 0 Zellen fallen durch, 6 bis 12 Zellen Ziegel. Flüssiges Metall hält sie nur auf der kürzesten Bühne.
  - Das ist die Einsicht „Lehm brennt zu Ziegel und hält, Holz nicht“. Glas bringt sie in Level 3 aber nicht, weil die Lava auf der Bühne auskühlt.
- **Holz an der Mündung** ist nach 2 bis 5 s verbrannt, bevor der Lehm dahinter gebrannt ist.

Vorschläge:
- **Eigenes Level „Ziegelbrücke“:** Lava muss über eine Lücke zu etwas Brennbarem oder Schmelzbarem. Eine Holzbrücke brennt durch, eine mit Lehm gedeckte hält (gemessen oben: 0 statt 5 bis 33 Zellen durchgefallen). Das Ziel liegt höchstens 3 Zellen hinter der Mündung einer mit Magma ausgekleideten Röhre, oder es ist Holz (zündet bei 280 °C) statt Sand.
- **Mehr latente Wärme für Lava:** Lava flösse weiter, bevor sie erstarrt. Über 1000 °C bliebe sie fern vom Magma trotzdem nicht. Das ändert außerdem Level 2 und 3 und die Lavaversuche aus Phase 1 bis 2d.

### Level 3 organisch, Wege gleich

- Der Abstieg windet sich. Die Galerie hat eine unregelmäßige Decke und runde Enden. Der Schlot ist eine runde Mulde über Magma. Die Kammer hat eine gewölbte Decke, der Sumpf einen runden Boden, die Lavatasche ist rund. Neu: ein Lehmhaufen in der Galerie, als Baustoff für das Lager.
- Der Schacht behält im Bereich des Eises seine Breite. Mit unebenen Wänden waren es 193 statt 174 Zellen Eis, und das Salz reichte nicht mehr.
- Der Zielbereich ist etwas größer geworden, weil die Scherben im gerundeten Sumpf tiefer fallen: 52/112 bis 116/150.
- Alle Wege wie vorher: Scherben Gold (15,8 s, 10 Griffe), Lava Silber (14,3 s, 12), Erz Silber (92 s, 14; vorher 43,5 s).
- **Falle „Wasser auf die Lava“ entfällt.** In Phase 3 hielt sie nur zufällig. Gemessen:
  - Das Testskript schöpfte bei 75/140. Dort liegt im Sumpf Salzwasser unter einer Schicht Süßwasser.
  - Mit Salzwasser von dort: ohne Salzkorrektur 3 Glas (keine Lösung), mit ihr 5 Glas (Silber).
  - Mit Süßwasser von der Oberfläche, wie ein Spieler schöpft, sind es in beiden Fällen 6 bis 8 Glas, und das Level ist mit Silber gelöst. Das gilt bei ein oder zwei Eimern und bei 112/129, 115/130 und 118/131.
  - Ursache: Das Wasser macht eine Kruste (11 bis 14 Zellen Basalt). Das Magma hinter dem Tunnel schiebt aber weiter Lava nach (74, nach 2 s wieder 82 Zellen).
  - Der Test zeigt jetzt dieses Verhalten: Kruste ja, Lösung trotzdem, Silber mit 14 statt 12 Griffen. Eine echte Falle hat Level 3 damit nur noch in „Glas auf dem Schlot ist fest und heiß“ (Eimer und Hand nehmen es nicht).
- Bild: `docs/phase3/level3_wege.png`.

### Level 2: der Lavakasten als Krater

- Ein Trichter mit unebenen Wänden und Basaltauskleidung, darunter ein runder Lavasee in einer Schale. Magma liegt unter und um den unteren Teil. Alles steckt in Tuff, damit die Wärme nicht durch den Vulkan zum Meer wandert.
- Alle Wege wie vorher: Schmelzen Gold (14,8 s, 8), Ins Meer Silber (9,3 s, 12), Salzwasser Silber (13,7 s, 10); Fallen Krater und „zu lange“ unverändert.
- Ins Meer: Der vierte Schöpfgriff geht jetzt an den Rest Schnee am rechten Hang (158/26). An der alten Stelle lag nach dem Umbau keiner mehr, und rechts blieb Schnee liegen.
- Bild: `docs/phase3/level2_wege.png`.

### Reichweite im Spielplatz

Im Modus „nur Beschaffen“ reichen Eimer und Hand auch im Spielplatz nur an das, was zum Himmel offen ist, und an alles, was daran grenzt. Mit unendlichem Vorrat gilt keine Grenze.

### Freischalten

- `LevelGate`: Offen sind immer die nächsten drei ungelösten Level. Gesperrte erscheinen blass mit Schloss und dem Hinweis „Gesperrt: erst ein früheres Level lösen“.
- In der Testphase sind alle Level offen (`LevelGate.ALL_OPEN = true`). Zum Umschalten genügt diese eine Zeile.

## 6. Medaillen neu gemessen

Die Lösungsskripte sind ein Roboter: Er greift sofort. Ich habe sie deshalb auch wie ein Mensch nachgespielt:
- Vor jedem Griff 2 s bzw. 4 s für Werkzeugwahl und Zielen. Gewolltes Warten im Skript zählt mit, die Zeiten addieren sich nicht.
- Jedes Tippen mit dem Eimer dauert mindestens 0,45 s, wie in der App.

| Level, Weg | Roboter | Mensch 2 s | Mensch 4 s | Medaille (alle drei) |
|---|---|---|---|---|
| L1 Feuer (Ast auf die Glut) | 12,7 s · 2 | 14,2 s | 23,2 s | Gold |
| L1 Graben (Mitte des Pfropfs, 52/46) | 38,2 s · 1 | 29,2 s | 35,8 s | Gold |
| L1 Anspülen | 50,2 s · 6 | 52,0 s | 69,0 s | Silber |
| L1 Rinne | 96,8 s · 2 | 101,3 s | 110,3 s | Silber |
| L1 Tragen | 13,0 s · 30 | 66,0 s · 26 | 118,0 s · 26 | Bronze |
| L2 Schmelzen | 14,7 s · 8 | 25,0 s | 39,7 s | Gold |
| L2 Ins Meer | 9,3 s · 12 | 31,8 s | 55,7 s | Silber |
| L2 Salzwasser | 13,7 s · 10 | 30,0 s | 47,8 s | Silber |
| L3 Scherben | 15,8 s · 10 | 30,2 s | 47,2 s | Gold |
| L3 Lava | 14,3 s · 12 | 34,5 s | 56,5 s | Silber |
| L3 Erz | 92,0 s · 14 | 56,8 s | 110,7 s | Silber |

Ergebnis:
- **Grenzen L1 und L2 unverändert.** Jeder Weg behält seine Medaille. Der knappste Abstand ist L1 Rinne: 110 s gegen die Silbergrenze von 150 s.
- **L3: Bronze-Zeit von 480 auf 240 s** (siehe unten), Gold und Silber unverändert.
- **Gefunden und behoben – Salz auf warmem Eis:** Im ersten menschlichen Durchlauf war Level 3 unlösbar. Das Salz lag als Haufen auf dem Eis und taute nichts.
  - Ursache: Die warme Luft (Punkt 9) wärmt den Salzhaufen in der Galerie auf etwa 30 °C. Warmes Salz bringt die oberste Eisschicht auf 0 °C. Dort griff nur noch das Schmelzen durch Wärme, das erst latente Wärme sammeln muss. Die Salzregel wurde übersprungen.
  - Behoben in `World.kt`: Salz taut Eis ab −18 °C immer sofort, auch wenn es schon 0 °C hat.
  - Test `warm salt melts ice as well`: Warmes Salz (30 °C) lässt nach 3 s 19 von 288 Zellen Eis übrig, gleich warmer Sand 189.
- **Zeitfenster Level 2:** Der Sand darf 1,5 bis 4,0 s auf der Lava liegen. Nach 1,0 s ist er noch nicht heiß genug, nach 5,0 s zum großen Teil Glas. Das Fenster ist 2,5 s breit und mit Tippen und Werkzeugwechsel gut zu treffen (Gold in 24 bis 29 s).
- **L1 Graben:** Wie in Phase 3 beschrieben trifft ein Griff nach mehr als 1,5 s am linken Rand des Pfropfs (48/45) eingesickertes Wasser, dann gibt es keine Medaille. Ein Mensch, der auf die Mitte zielt, bekommt Gold.

### Level 3: Das Eis taut nach etwa 4 Minuten von selbst einen Kanal

Beim Nachmessen aufgefallen und **schon vor 3b so** (Gegenprobe mit dem Stand von Phase 3: ebenfalls nach 240 s):
- Die Hitze des Schlots kriecht durch den Tuff. Nach 200 s ist der Tuff links des Schachts über 30 °C warm. Nach 240 s ist an der linken Schachtwand eine Zellenspalte getaut. Durch sie reicht der Arm in die Kammer, obwohl noch 140 von 174 Zellen Eis stehen.
- Der alte Test prüfte nur „mehr als 100 Zellen Eis nach 300 s“ und hat das nicht bemerkt. Er prüft jetzt die Reichweite: Nach 200 s ist die Kammer zu.
- „Warten, dann graben und Sand auf die Lava“ ergab Bronze in 261 s mit 8 Griffen (Mensch, 2 s pro Griff), ohne die Salz-Einsicht. Die Bronze-Zeit ist deshalb jetzt 240 s. Dieser Weg endet mit „Geschafft“ ohne Medaille. Der neue Test spielt ihn als Roboter: 247 s, also nur 7 s über der Grenze. Ein Mensch braucht für drei Grabungen und den Sand länger.
- Gemessen, was das Tauen verzögert:

  | Änderung | Kammer offen nach | Salzweg |
  |---|---|---|
  | keine | 240 s | Gold |
  | Tuff um den Schacht auf −15 °C | 260 s | scheitert |
  | Eis auf −15 °C | 250 s | scheitert (Schmelzwasser friert wieder) |
  | halb so viel Magma unter dem Schlot | 260 s | Gold |
  | Eismantel um den Schacht (3 bzw. 5 Zellen) | 240 bzw. 250 s | scheitert (Eis leitet 10-mal besser als Tuff) |
  | ohne Schlot | 500 s | – |
  | ohne Lavatasche | 250 s | Gold |

- Der Schlot ist also die Ursache. Gegen eine unbegrenzte Wärmequelle hilft nur Abstand. Etwa 16 Zellen mehr zwischen Schlot und Schacht ergäben gut 8 Minuten (die Zeit wächst mit dem Quadrat des Abstands). Dafür müssten Galerie, Schacht und Kammer neu angeordnet werden.
- **Zur Freigabe:** So lassen (Bronze-Grenze 240 s), oder in Phase 4 die Geometrie umbauen?

## 7. Temperatur sichtbar

- **Glühen in Stufen:**
  - ab 450 °C dunkelrot,
  - 600 °C rot,
  - 750 °C orange-rot,
  - 900 °C orange,
  - 1100 °C gelb,
  - 1300 °C hellgelb,
  - 1500 °C weiß.
  - Bis 350 °C über dem Beginn mischt sich das Glühen mit der Farbe des Stoffs, darüber bleibt nur das Glühen.
  - Über 600 °C leuchtet ein heißer Stoff auch seine Umgebung schwach an.
  - Bild: `docs/phase3b/gluehen.png` (Stein bei 20, 450, 600, 750, 900, 1100, 1300, 1500 °C).
- **Wärmebild** (Thermometer-Knopf in der Ecke, im Level und im Spielplatz): Die ganze Welt erscheint in Falschfarben nach Temperatur, von Dunkelblau (−30 °C) über Blau (0 °C), Blaugrün (20 °C), Grün (60 °C), Gelb (150 °C), Orange (400 °C) und Rot (800 °C) bis Weiß (1400 °C).
  - Feste Stoffe behalten als grauer Schatten ihre Form, Kanten sind etwas deutlicher. Luft und Gase sind etwas dunkler.
  - Licht, Dunst und Flammen sind im Wärmebild aus.
  - Bild: `docs/phase3b/gluehen_waermebild.png`, Screenshot `37_waermebild`.

## 8. Eis sofort erkennbar

- Bläulich (#a9d8f0), glasig-durchscheinend, mit feinen hellen Schlieren und Glanzkanten an der Oberseite und an den Rändern.
- **Auch im Schatten:** Eis wird nie dunkler als 72 % seiner Farbe, Fels im Dunkeln dagegen fast schwarz. Die Glanzkanten werden erst nach dem Abdunkeln aufgesetzt und bleiben hell.
- Bild: `docs/phase3b/hoehle.png`: Eis, Salz, Schnee und Metall im Schatten einer Höhle mit schmaler Öffnung.

## 9. Trägt Luft Wärme?

**Vorher: nein.** Luft leitete Wärme nur wie jeder Stoff an ihre Nachbarn und kühlte überall auf die Umgebungstemperatur ab, auch in geschlossenen Räumen. Ein verschlossener Gang wurde deshalb nicht wärmer als ein offener.

**Jetzt:**
- **Warme Luft steigt.** Ist die Luft unter einer Zelle wärmer (um mehr als 0,5 °C), tauschen beide ihre Temperatur, mit einer festen Wahrscheinlichkeit von 0,1 pro Schritt.
  - Der Zufall ist ein Hash aus Zelle und Schritt, also deterministisch. Undo und Wiederholung bleiben gleich.
  - Mit Wahrscheinlichkeit 1 kühlte heißer Sand auf Lava zu stark: Er erreichte nur noch 920 °C statt 1000 °C, kein Glas mehr. Level 2 und 3 änderten sich. Über 0,2 blieben die Wege nicht stabil, deshalb 0,1.
- **Unter einer festen Decke** breitet sich warme Luft seitlich aus, eine Zelle pro Schritt.
- **Eingeschlossene Luft kühlt nicht ab.** Erkannt wird sie mit derselben Raumerkennung wie für den Druck: Luft ohne Verbindung zum Himmel. Sie wird alle 10 Schritte neu bestimmt und mit Undo gesichert. Offene Luft kühlt wie bisher ab.
- **Die oberste Zeile ist der Himmel** und hat immer Umgebungstemperatur. Sonst sammelte sich warme Luft am oberen Rand der Welt.

**Testfall** (`AirTest`): Gang im Fels, 47 × 6 Zellen, am Ende Magma im Boden, Schacht zum Himmel; einmal offen, einmal mit Stein, einmal mit Holz verschlossen. Werte nach 30 s:

| | offen | Stein | Holz |
|---|---|---|---|
| Luft im Gang, Mittel | 110,3 °C | 206,4 °C | 206,6 °C |
| fernes Ende (30 Zellen und mehr von der Hitze) | 50,1 °C | 85,5 °C | |

Im Freien steigt die Wärme über Magma auf. 10 Zellen darüber hat die Luft 42,6 °C, gleich hoch, aber 21 Zellen seitlich, 22,2 °C. Weiter oben kühlt sie ab. Bild: `sim/build/pictures/air_gallery_*.png`.

**Weiterer Weg in Level 3? Nein.** Gemessen über 10 Minuten:

| Galerie | Luft in der Galerie nach 30 / 300 / 600 s | Eis nach 300 / 600 s |
|---|---|---|
| offen (wie im Level) | 33 / 39 / 42 °C | 116 / 0 |
| Abstieg mit Fels dicht verschlossen | 144 / 234 / 273 °C | 66 / 0 |
| Abstieg mit zwei Eimern Sand verfüllt | 34 / 39 / 42 °C | 113 / 0 |

- Ganz dicht verschlossen staut sich die Wärme deutlich: 144 °C nach 30 s, 273 °C nach 10 min. Das Eis taut aber kaum schneller. Die Luft über dem Eis hat nach 120 s erst 4,6 °C, nach 300 s 34 °C.
- Mit dem, was der Spieler hat, lässt sich der Abstieg nicht dicht machen. Zwei Eimer Sand rieseln durch den gewundenen Gang. Die Luft bleibt mit dem Himmel verbunden.
- Ein ganz verschlossener Abstieg schnitte zudem die Reichweite ab: Die Galerie ist dann nicht mehr zum Himmel offen.
- Ein eigener Weg entsteht also nicht. Die warme Luft hat aber den Salzfehler aus Punkt 6 sichtbar gemacht.

## Leistung

Gemessen auf diesem Rechner (JVM, etwa 2,5-mal langsamer als das Galaxy Z Fold2), größte Welt 600 × 340:

| | ms |
|---|---|
| Hand-Hervorhebung berechnen, unendlicher Vorrat | 3,1 pro Berechnung |
| dasselbe, nur Beschaffen (mit Reichweite) | 4,2 pro Berechnung |
| Materialinfo abfragen | 0,007 bis 0,025 |
| Bild, sichtbares Drittel, normal | 1,79 |
| Bild, sichtbares Drittel, Wärmebild | 0,77 |
| Bild, sichtbares Drittel, mit Hand-Hervorhebung | 2,15 |

- Die Hervorhebung wird höchstens alle 30 Schritte berechnet und nur, solange die Hand gewählt ist. Im Mittel sind das 0,1 bis 0,14 ms pro Schritt, auf dem Fold2 etwa 0,05.
- **Warme Luft:** Die warme Luft kostet Rechenzeit. Gemessen habe ich den Stand vor 3b (`6b4b667`) und den heutigen abwechselnd, je zweimal, mit demselben Benchmark, in ms pro Schritt:

  | Welt | vor 3b | 3b | Zuwachs |
  |---|---|---|---|
  | Voll aktive Welt 200 × 350 | 4,35 / 4,54 | 5,28 / 5,05 | +16 % |
  | dieselbe mit Pflanzen, Tieren und Licht | 5,33 / 5,02 | 5,62 / 5,78 | +10 % |
  | Spielplatz 200 × 350 mit Feuer, Sand und Wasser | 2,06 / 2,10 | 2,71 / 2,53 | +26 % |
  | Größte Welt 600 × 340, voll aktiv | 15,14 / 15,04 | 17,22 / 16,36 | +11 % |
  | Größte Welt, voll aktiv mit Pflanzen, Tieren und Licht | 16,68 / 16,13 | 18,64 / 18,25 | +12 % |
  | Größte Welt, Wiese mit Feuer, Sand und Wasser | 2,41 / 2,16 | 2,62 / 2,66 | +15 % |

  - Hochgerechnet auf das Fold2 (2d: 6,5 bis 7,5 ms für die größte, voll aktive, belebte Welt) wären das 7,3 bis 8,4 ms, also an der Grenze von 8 ms. Den gültigen Wert liefert „Leistung messen“ auf dem Gerät.
  - Die Level (190 × 160 und kleiner) und gewöhnliche Spielplatzwelten liegen weit darunter (Wiese 600 × 340: 2,6 ms auf diesem Rechner, etwa 1 ms auf dem Fold2).
  - Vorschlag: In 3c laufen Wind und Wolken durch dieselbe Schleife. Dort optimiere ich den Luftteil gezielt (nur Luftzellen, nur wo es Temperaturunterschiede gibt) und messe vorher und nachher.

## Tests

- sim und level: 159 Tests, alle grün (lokal).
- Neu oder geändert:
  - `AirTest`: vier Tests zur warmen Luft;
  - `SupplyTest`: ein Strich gleich ein Griff, Lehm ins Lager, Reichweite im Spielplatz, Materialinfo, greifbare Objekte;
  - `MaterialBehaviorTest`: warmes Salz taut Eis;
  - `HeatPicturesTest`: Bilder für diesen Bericht;
  - `TiefeSchmelzeTest`: Reichweite nach 200 s, Warten ohne Medaille;
  - `LevelGateTest`: Freischalten.
- **App-Test** (Robolectric, Screenshots im Release):
  - Neu: `infoHeatAndHand`, mit den Screenshots `35_info_wasser`, `36_info_lava`, `37_waermebild`, `38_hand_greifbar` und `39_zoom_im_rahmen`.
  - Angepasst an das lange Drücken: Malen und Schöpfen beginnen mit einem kurzen Wischen über die Berührungsschwelle. Ein still liegender Finger wird nach 0,45 s zur Materialinfo.

## Zur Freigabe

1. **Langes Drücken oder Info-Knopf?** Halten zum Schütten geht jetzt nur noch nach kurzem Wischen (Punkt 1).
2. **Ziegelweg:** nicht in Level 3 möglich (Messungen oben). Eigenes Level „Ziegelbrücke“ später?
3. **Level 3, Tauen nach 4 Minuten:** Bronze-Grenze 240 s lassen, oder Geometrie umbauen (Schacht etwa 16 Zellen weiter vom Schlot)?
