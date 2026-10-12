# Phase 3: Bericht

Leitfaden V3, Abschnitt 5. Reihenfolge nach Abschnitt 8: ein Commit pro Level, danach Stopp und Freigabe.

Commits:
- `0537ddb` Grundlage: Modul `level`;
- `493d365` Level 1;
- `6184b9d` Level 2;
- `c3fb990` Level 3 und die Reichweite der Werkzeuge;
- `0430d76` App: Levelauswahl, Levelbildschirm, Medaille, Fortschritt;
- `811340b` Level 3: Erzadern im Tuff. Die Zeile „doppelte Prüfung der Hand entfernt“ in dieser Commit-Nachricht stimmt nicht; berichtigt in `7a069b7`.
- `50e4f83` Levelbildschirm: ganzes Level im Bild, Querformat-Spalte;
- `7a069b7` App-Test: Eimer lange genug halten.

## Grundlage: Format, Ziele, Bewertung

- **Eigenes Modul `level`**, reines Kotlin auf `sim`. Die Simulation kennt weiterhin keine Level, Ziele oder Bedienung (7b).
- **Format (5.1):**
  - `terrain.png`: jede Farbe ist die Farbe eines Materials aus `materials.json`, Schwarz oder Durchsichtiges ist Luft.
  - `anchor.png`: Weiß markiert verankertes Gelände. Verankertes loses Material fällt, rutscht und erodiert nicht.
  - `level.json`: Werkzeuge (Eimer ja/nein mit Fassungsvermögen, Hand, Bauen), Startvorrat, Ziele mit Polygonen, Gold-, Silber- und Bronzegrenzen, Gebiet, Niveau, Seed, Umgebungstemperatur, warme oder kalte Bereiche, Bäume.
  - Eigener PNG-Leser ohne Android und AWT, damit die Level auf dem Handy und in den Tests gleich laden.
- **Ziele (5.2):** Material im Bereich, Bereich füllen (mit Flüssigkeit), Temperatur, Entzünden, Löschen, Licht, Wachstum, Schützen. Ein Ziel zählt, wenn es zwei Sekunden ohne Unterbrechung erfüllt ist. Ein Schutzziel gilt umgekehrt: Bricht es, ist das Level verloren.
- **Bewertung (5.1.3):** nach Zeit der Welt und Handgriffen. Ein Handgriff ist jedes Füllen oder Ausgießen des Eimers, jedes Greifen oder Ablegen mit der Hand, jede gebaute Form und jedes Einlagern. Die Medaille ist die beste, deren Grenzen Zeit **und** Handgriffe einhalten.
- **Aktionslisten (5.3.1)** als Text, zum Beispiel `scoop 48 45 3 20` (Eimer füllen bei 48/45, Pinsel 3, 20 Schritte) oder `wait 300`. Die Tests spielen sie deterministisch in die Simulation. Sie nutzen nur Pinselgrößen, die die App anbietet (0, 1, 3, 6, 10).

## Level 1: „Trockener Teich“ (Wald, leicht)

**Welt:**
- Links oben steht ein Becken, von Quellen gespeist, hinter einer Felswand. Deren Fenster ist mit Erde gestopft.
- Vom Becken führt eine Rinne zu einem Schacht. Den Schacht verschließt Erde auf einem umgestürzten Stamm.
- Unter dem Stamm glimmt Glut auf Asche, die Reste eines Lagerfeuers.
- Am rechten Ufer laufen Quellen in eine Mulde. Sie fließt über Kies in eine Höhle ab statt in den Teich.

**Werkzeug:** Eimer mit 40 Zellen, Hand.
**Ziel:** Fülle den Teich mit Wasser (80 % des Teichbeckens, 2 s lang).
**Grenzen:** Gold 60 s und 4 Griffe, Silber 150 s und 10, Bronze 300 s und 40.

| Weg | Aktionen | Zeit | Griffe | Medaille |
|---|---|---|---|---|
| Feuer: einen Ast mit der Hand auf die Glut legen, der Stamm brennt durch, die Erde fällt, das Becken läuft durch den Schacht aus | `hand 91 72`, `hand 77 68` | 12,2 s | 2 | Gold |
| Graben: ein Eimer Erde aus dem Fenster, das Becken läuft über die Felskante | `scoop 48 45 3` | 40,8 s | 1 | Gold |
| Anspülen: drei Eimer Wasser auf die Erde im Fenster, sie wird ausgewaschen | 3 × Schöpfen und Gießen | 46,8 s | 6 | Silber |
| Rinne: ein Eimer Erde vom Hügel in die Ufermulde, ihre Quellen laufen über in den Teich | Schöpfen, Gießen | 94,2 s | 2 | Silber |
| Tragen: Wasser eimerweise vom Becken in den Teich (naheliegend und mühsam) | 15 × Schöpfen und Gießen | 13,0 s | 30 | Bronze |

**Fallen (getestet):**
- Die Hand hebt den Stamm nicht, er ist im Fels eingeklemmt.
- Wasser auf die Glut macht daraus Holzkohle, danach zündet der Ast nicht mehr.
- Wer nur wartet, hat nach 300 s keine Medaille.

**Änderungen an der Simulation:**
- Neues Geländematerial **Quelle**: gibt Wasser ab, solange es frei liegt.
- **Durchbruch:** Ein Korn zwischen Wasser und Luft gibt dem Wasser ab und zu nach. So bricht ein von außen abgetragener Pfropf am letzten Korn durch.
- Fließendes Wasser trägt auch das Korn unter seiner alten Zelle ab (Erosion 0,08 statt 0,05).
- Glut glimmt 240 statt 30 s.
- Eimer und Hand legen auch in Rauch und Dampf ab, nicht in Feuer.

**Abweichungen mit Begründung:**
- **Erdwall → Felswand mit Erdfenster.**
  - Ein breiter Erdwall ließ sich weder mit einer Kerbe noch mit Wasser brechen. Auch nach der höheren Erosion hielt der innere Rest das Wasser.
  - Ein Erdpfropf im Felsfenster dagegen lässt sich einstellen. Gemessen an der Fensterlänge:
    - Mit 4 Zellen bricht er nach 52 s von selbst.
    - Mit 5 und 6 Zellen hält er ohne Zutun, und drei Eimer Wasser spülen ihn auf.
    - Gewählt: 6 Zellen.
  - Die Einsicht bleibt: Erde hält das Wasser, bis man sie anspült oder abträgt.
- **Bachlauf → Schacht mit Erdpfropf auf dem Stamm.**
  - Der Stamm ist im Schacht verkeilt und trägt Erde, die das Becken verschließt. Brennt er durch, fällt die Erde und das Wasser läuft.
  - Die Glut liegt darunter in einer Nische mit Luftspalt. Im geschlossenen Schacht erstickte das Feuer am eigenen Rauch, der sich unter dem Stamm staute.
- **„Zu schwer“ → „fest verankert“.** Der Stamm ist im Fels eingeklemmt, also verankert. Die Hand meldet deshalb „fest verankert“, nicht „zu schwer“. Das Ergebnis ist dasselbe: Die Hand hebt ihn nicht.
- **Rinne:**
  - Umgesetzt als ein Eimer Erde in die Ufermulde. Er verschließt deren Abfluss über den Kies in die Höhle. Danach laufen die Quellen über das Ufer in den Teich.
  - Gebaut im Sinne einer Bauform wird dabei nichts. Die Einsicht ist dieselbe: Erde aus dem Eimer lenkt das Wasser in den Teich.
- **Kein Gras auf der Terrasse der Hangquellen.** Gras ist ein Pulver und hielt das Wasser der Quellen zwischen zwei Büscheln fest.

Bild: `docs/phase3/level1_wege.png`.

## Level 2: „Signalfeuer“ (Vulkan, Kernniveau)

**Welt:**
- Links ein Vulkan mit Lavasee im Krater, über Magma.
- In der Mitte eine Schlucht und das Meer bis zum Grund der Welt.
- Am Fuß des Berges ein kleiner Strand mit wenig Sand (drei Zeilen).
- Auf dem Gipfel ein Holzstoß aus Holzbalken mit Lücken, unter einer Schneehaube, drei Zellen dick. Die Luft ist kalt (−0,5 °C).

**Werkzeug:** Eimer mit 30 Zellen, keine Hand.
**Ziel:** Entzünde den Holzstoß auf dem Gipfel (10 brennende Zellen, 2 s lang).
**Grenzen:** Gold 90 s und 8 Griffe, Silber 180 s und 14, Bronze 300 s und 30.

**Einsichten:**
- Feuer kann man nicht tragen, Hitze schon. Sand liegt kurz auf der Lava und wird heiß, dann trägt ihn der Eimer zum Holz.
- Heißer Sand auf Schnee schmilzt nur Schnee, das Wasser durchnässt das Holz. Der Schnee muss weg, oder er muss so tauen, dass das Wasser abläuft.

| Weg | Zeit | Griffe | Medaille |
|---|---|---|---|
| Schmelzen: zweimal heißer Sand seitlich an den Holzstoß. Die erste Ladung taut den Schnee dort, das Wasser läuft den 45°-Hang hinab. Die zweite Ladung zündet. | 14,5 s | 8 | Gold |
| Ins Meer: vier Eimer Schnee ins Meer, dann eine Ladung heißer Sand | 9,5 s | 12 | Silber |
| Salzwasser: drei Eimer Meerwasser tauen den Schnee ohne Wärme, dann heißer Sand | 13,3 s | 10 | Silber |

**Fallen (getestet):**
- Heißer Sand oben auf den Schnee schmilzt nur Wasser, immer wieder.
- Eine einzelne Ladung seitlich taut nur den Schnee, sie zündet nicht.
- Zu lange gewartet: Der Sand auf der Lava wird zu Glas und zündet nichts mehr.
- Schnee in den Krater: Die Lava erstarrt zu Basalt, die einzige Hitzequelle ist zugedeckt.
- Ohne Eingriff bleibt alles 300 s lang, wie es ist.

**Änderungen an der Simulation:**
- Neues Geländematerial **Magma** (`holds_temp`): Es hält 1250 °C, die Hitze aus der Tiefe.
- Eimer und Hand kühlen zur Umgebungstemperatur der Welt hin, nicht mehr zu den 20 °C der Materialdatei.

**Abweichungen mit Begründung:**
- **Magma.** Ohne Nachschub aus der Tiefe erstarrt ein Lavasee in kalter Luft in unter einer Minute. Magma unter dem See hält ihn flüssig.
- **Schlucht und Meer bis zum Grund.** Magma heizt unbegrenzt, und Fels leitet gut. Ohne Unterbrechung kochte das Meer, und der Schnee auf dem Gipfel taute von selbst. Der Krater liegt deshalb in Tuff (verankerter Asche), und eine Schlucht trennt den Vulkan vom Rest.
- **„Eimer (eine Ladung)“** habe ich als ein Eimer mit 30 Zellen umgesetzt: Er fasst eine Ladung, mehr nicht. Der Sand reicht für wenige Versuche.
- **Medaillen:** Der Weg des Leitfadens (Schnee ins Meer) bekommt Silber, weil er mehr Griffe braucht. Gold bekommt der im Leitfaden zugelassene Weg „Schnee neben dem Holzstoß so schmelzen, dass das Wasser abläuft“. Er ist die cleverere Wechselwirkung: Dieselbe Hitze erst als Tauwerkzeug, dann als Zünder.
- **Zusätzlicher Weg Salzwasser** aus der vorhandenen Regel „Salz taut Schnee“.

Bild: `docs/phase3/level2_wege.png`.

## Level 3: „Tiefe Schmelze“ (Mine, Obergrenze)

**Welt:**
- Ein Hügel aus Tuff (verankerter Asche) unter einer Erdschicht, an der Oberfläche ein Baum. Durch den Tuff ziehen Erzadern.
- Von oben führt ein Gang hinab in eine Galerie. Dort liegen ein Salzhaufen, etwas Sand, Geröll und Erzgeröll, dazu eine Sickerquelle in einer Mulde.
- Am Anfang der Galerie liegt ein glühender Schlot: ein kleiner Lavapool über Magma.
- Im Boden der Galerie führt ein Schacht zur tiefen Kammer. Er ist mit einem Eispfropfen verschlossen.
- In der Kammer liegt ein Sandbett, darunter ein Sumpf, in dem sich Tauwasser sammelt.
- Neben der Kammer liegt eine Lavatasche über Magma, hinter einem Tunnel voll Erde.

**Werkzeug:** Eimer mit 40 Zellen, Hand, Bauen.
**Ziel:** Bringe Glas in die tiefe Kammer (5 Zellen Glas oder Glasscherben, 2 s lang).
**Grenzen:** Gold 120 s und 10 Griffe, Silber 240 s und 16, Bronze 480 s und 40.

**Neue Regel: Reichweite.** Im Level reichen Eimer und Hand nur an das, was zum Himmel offen ist: Luft, Gas und Flüssigkeit, die mit der obersten Zeile verbunden sind, und alles, was daran grenzt. Ohne diese Regel hätte der Eimer durch das Eis in die Kammer geschöpft, und der Pfropf wäre bedeutungslos. Was dahinter liegt, meldet „Da kommst du nicht hin“. Im Spielplatz gilt die Regel nicht.

**Einsicht (Baba):** Wasser auf heißem Glas ist sonst ein Schaden, denn das Glas springt. Hier ist genau das der Schlüssel: Glas auf dem Schlot ist fest und heiß, weder Eimer noch Hand nehmen es. Abgeschreckt zerspringt es zu Scherben, und Scherben passen in den Eimer.

| Weg | Zeit | Griffe | Medaille |
|---|---|---|---|
| Scherben: Salz taut den Eispfropfen. Sand auf den Schlot wird zu Glas. Wasser aus der Quelle schreckt es zu Scherben ab. Der Eimer trägt sie in die Kammer. | 15,8 s | 10 | Gold |
| Lava: Salz taut den Pfropfen. Drei Eimer Erde aus dem Tunnel, die Lava läuft in die Kammer. Sand aus dem Bett auf die Lava an der Mündung. | 14,8 s | 12 | Silber |
| Erz: Salz, Tunnel graben wie oben. Zwei Eimer Erzgeröll auf die Lava in der Tasche. Es schmilzt zu flüssigem Metall, das als Wärmebrücke am Boden ausläuft und den Sand verglast. | 43,5 s | 14 | Silber |

**Fallen (getestet):**
- Solange das Eis hält, erreicht der Eimer weder Kammer noch Tunnel. Die Hand zieht den Pfropfen nicht heraus. Nach 300 s Warten ist das Eis noch da und kein Glas entstanden.
- Wasser auf die auslaufende Lava erstarrt zu einer Kruste. Danach schmilzt der Sand nicht mehr.
- Glas auf dem Schlot ist fest und heiß, weder Eimer noch Hand nehmen es.

**Abweichungen mit Begründung** (alle physikalisch bedingt, mit Messwerten):

1. **Lehmschicht → Erdpfropf.**
   - Lehm neben Lava brennt zu Ziegel. Gemessen: Eine Lehmwand von 2 bis 4 Zellen ist nach 30 s durchgebrannt und dicht. Mit Wasser aufweichen lässt sie sich dann nicht mehr.
   - Der Tunnel ist deshalb mit Erde gefüllt, der Eimer gräbt ihn auf.
   - Die Einsicht „der Pfropf muss weg, dann fließt die Lava“ bleibt. Die Einsicht „Lehm aufweichen“ entfällt.
2. **Keine gebaute Rinne aus Ziegel.** Lava kühlt in langen Gängen zu schnell. Gemessen:
   - Sie fließt 10 bis 20 Zellen weit.
   - In einem 4 Zellen breiten Schacht erstarrt sie nach etwa 24 Zeilen.
   - Eine Rinne von der Tasche bis zum Schacht ist deshalb nicht flüssig zu durchlaufen.
   - Die Tasche liegt jetzt direkt neben der Kammer. Ihr Tunnel ist mit Magma ausgekleidet, damit die Lava flüssig ankommt.
   - Dazu kommt das Werkzeug: Ins Lager kommen nur feste Stücke aus der Hand (Stamm, Stein, Metall, Glas). Lehm ist lose und lässt sich nicht einlagern, also kann der Spieler keine Lehmrinne bauen, die zu Ziegel brennt.
   - Die Einsicht „Holz brennt weg, die Rinne muss aus Ziegel sein“ ist damit **nicht umgesetzt**. Vorschlag siehe unten.
3. **Heißes Geröll eimerweise → Scherben.**
   - Geröll schmilzt bei 1150 °C, ist also höchstens etwa 1140 °C heiß. Sand wird erst bei 1000 °C zu Glas.
   - Gleich viel Geröll (Wärmekapazität 2,0) und Sand (1,3) gleichen sich rechnerisch bei etwa 700 °C aus. Im Versuch verglaste auch frisch von der Lava geschöpftes Geröll keinen Sand.
   - Erhalten ist die Einsicht „erst den Eispfropfen mit Salz entfernen, dann etwas Heißes hinuntertragen“. Getragen wird jetzt das fertige Glas als Scherben.
   - Gemessen: Sand auf magmagespeister Lava ist nach 1 bis 3 s Glas.
4. **Erzadern und Erzgeröll.**
   - Drei Erzadern ziehen durch den Tuff, außer Reichweite und fern der Hitze. Eine Erzader ist verankertes Gestein, Eimer und Hand nehmen sie nicht.
   - Für den Erzweg liegt deshalb loses Erzgeröll als Haufen in der Galerie.
5. **Höhlensystem eher gerade als organisch.** Galerie, Schacht und Kammer sind weitgehend rechteckig. So bleiben die Wege in den Tests stabil. Der Leitfaden wünscht ein organisches Höhlensystem. Die Wände lassen sich nach der Freigabe unregelmäßig machen, wenn die Wege dabei grün bleiben.
6. **Werkzeug Bauen** ist freigeschaltet, wird aber von keinem der drei Wege gebraucht. Gebaut werden kann nur, was die Hand vorher eingelagert hat, etwa Bretter aus dem Stamm des Baums.
7. **Nicht umgesetzte Fallen:**
   - „Zu viel Lava begräbt den Sand“ ist möglich, aber nicht als Test hinterlegt.
   - „Holzrinne brennt durch“ entfällt mit der Rinne.
   - „Feuer im engen Schacht erstickt“ kommt in keinem Weg vor.
8. **Hülle aus Tuff.** Magma heizt unbegrenzt. In Fels taute der Eispfropfen von selbst. Tuff gibt die Wärme langsam weiter: Nach 300 s Warten hält das Eis noch.
9. **Ziel: 5 Zellen Glas oder Glasscherben.** Abgeschrecktes Glas ist nach der Materialdatei „Glasscherben“. Beides ist Glas im Sinne des Ziels.

**Vorschlag für den Ziegelweg** (Entscheidung bei der Freigabe):
- Der Eimer bekommt eine Funktion „Ins Lager“ für lose Stoffe. Dann lässt sich aus Lehm eine kurze Rinne bauen. Sie brennt an der Lava zu Ziegel und hält. Eine Rinne aus Brettern vom Baum brennt durch.
- Die Rinne müsste kurz bleiben (unter 10 Zellen) und mit Magma unterlegt sein, sonst erstarrt die Lava darin.

Bild: `docs/phase3/level3_wege.png`.

## App

- **Startbildschirm:** „Level“ als erster Knopf, „Spielplatz“ darunter.
- **Levelauswahl:**
  - Jedes Level erscheint als Bild aus dem echten Renderer.
  - Dazu Gebiet und Niveau, das Ziel, die Werkzeuge und die beste Medaille mit Zeit und Handgriffen.
  - Alle drei Level sind offen, damit die Freigabe jedes Level einzeln prüfen kann.
- **Levelbildschirm:**
  - Ein Level beginnt mit der ganzen Welt im Bild; zwei Finger zoomen hinein.
  - Der Spielplatz mit nur den Werkzeugen des Levels: Eimer, Hand und Lager nur, wenn das Level sie gibt.
  - Nicht verfügbar sind „Neue Welt“, der Moduswechsel und „Alles leeren“.
  - Über der Welt stehen das Ziel mit Fortschrittsbalken, die Zeit, die Handgriffe und die drei Medaillengrenzen. Eine schon überschrittene Grenze wird blass.
  - Der Zielbereich ist gestrichelt auf der Welt eingezeichnet: wo, nicht wie.
  - Im Querformat steht das Ziel in der Spalte. Mit eingeklappter Spalte liegt es klein über der Welt.
- **Rückgängig** nimmt Zeit und Handgriffe mit zurück. **Neu beginnen** startet das Level frisch.
- **Ende:**
  - Medaille mit Zeit und Handgriffen.
  - Knöpfe „Nochmal“, „Nächstes“ (oder „Auswahl“ nach dem letzten Level) und „Ansehen“.
  - Ein Level mit Schutzziel endet mit „Verloren“, wenn das Geschützte nicht hält.
- **Fortschritt:**
  - Gespeichert wird das beste Ergebnis jedes Levels: die bessere Medaille, bei gleicher Medaille die kürzere Zeit.
  - Entdeckungen aus den Leveln kommen ins Entdeckungsbuch.
- **App-Test** (Robolectric, Screenshots im Release):
  - Levelauswahl.
  - Level 1 herausgezoomt, ein Eimer aus dem Fenster, Medaille, weiter zu Level 2, zurück zur Auswahl mit Medaille.
  - Level 3 im Querformat: Der Eimer in die Kammer meldet „Da kommst du nicht hin“.

## Leistungsmesswerte

Die Level sind klein (220 × 140, 180 × 110, 190 × 160 Zellen), die mittlere Spielplatzwelt hat 480 × 300.

Gemessen auf diesem Rechner (JVM). Er ist etwa 2,5-mal langsamer als das Galaxy Z Fold2, siehe 2d:

| Welt | ms pro Schritt | langsamster Schritt | Fold2 (umgerechnet) |
|---|---|---|---|
| Trockener Teich, Gold-Lösung (730 Schritte) | 0,48 | | 0,2 |
| Trockener Teich, Start ohne Eingriff (600 Schritte) | 0,23 | 1,6 | 0,1 |
| Signalfeuer, Gold-Lösung (870 Schritte) | 0,56 | | 0,2 |
| Signalfeuer, Start ohne Eingriff | 0,27 | 2,2 | 0,1 |
| Tiefe Schmelze, Gold-Lösung (950 Schritte) | 0,55 | | 0,2 |
| Tiefe Schmelze, Start ohne Eingriff | 0,56 | 5,3 | 0,2 |
| Zum Vergleich: Spielplatz 480 × 300 | 3,36 | | 1,3 |

- **Ziele prüfen:** alle 10 Schritte, in den Lösungszeiten oben enthalten.
- **Reichweite:**
  - Breitensuche über die Welt, einmal pro Schritt und nur bei einem Griff.
  - Schritt plus Griff an einer leeren Stelle: 0,49 bis 0,86 ms.
  - Ohne Griff kostet sie nichts.
- **Level laden** (Bilder lesen, Welt bauen): 2 bis 4 ms.
- **Ergebnis:** Die Level kosten auf dem Handy etwa ein Fünftel der Spielplatzwelt. Die Grenze von 8 ms pro Schritt ist weit eingehalten.

**CI-Rechner.** Die Messwerte der Releases liegen seit beta-83 über denen von beta-80:

| Welt | beta-80 | beta-83 | beta-84 | beta-85 | beta-86 |
|---|---|---|---|---|---|
| Voll aktive Welt 200 × 350 | 4,28 | 8,29 | 9,66 | 7,37 | 10,77 |
| Dieselbe mit Pflanzen, Tieren und Licht | 7,99 | 11,97 | 11,87 | 10,15 | 12,38 |
| Größte Welt, voll aktiv mit Pflanzen, Tieren und Licht | 8,78 | 14,97 | 15,01 | 13,42 | 15,82 |
| Spielplatz | | 3,14 | 2,84 | 2,94 | 2,81 |

(ms pro Schritt)

- **Gegenprobe.** Ich habe den Stand von beta-80 (`87a6002`) und den heutigen Stand auf diesem Rechner abwechselnd je zweimal mit demselben Benchmark gemessen:

  | Welt | beta-80 | heute |
  |---|---|---|
  | Voll aktive Welt | 5,93 / 4,89 | 5,40 / 5,01 |
  | Dieselbe mit Pflanzen, Tieren und Licht | 5,85 / 5,76 | 6,10 / 5,69 |
  | Größte Welt, voll aktiv | 16,65 / 17,72 | 17,58 / 16,91 |
  | Größte Welt mit Pflanzen, Tieren und Licht | 18,78 / 17,80 | 18,45 / 18,60 |

- **Ergebnis:** Kein Rückschritt im Code. Die Unterschiede liegen im Rauschen dieses Rechners. Beta-80 lief vermutlich auf einem schnelleren CI-Rechner; in 2d schwankte er schon zwischen 5,39 und 7,99 ms für dieselbe Welt.
- Für die Hochrechnung aufs Handy gilt deshalb weiter der Wert aus 2d: etwa 6,5 bis 7,5 ms für die größte, voll aktive, belebte Welt auf dem Fold2. Den gültigen Wert liefert nur „Leistung messen“ auf dem Gerät.

## Build

**Build:** [`beta-87`](https://github.com/shootcat/ReAct/releases/tag/beta-87). CI grün.

- **Vorher:** beta-86 war ebenfalls grün. Die Screenshots zeigten zwei Mängel:
  - Im Hochformat zeigte ein Level zu Beginn nur die Mitte der Welt.
  - In der Spalte im Querformat waren Levelname und Medaillengrenzen abgeschnitten.
  
  Beides ist in beta-87 behoben.
- **Geprüfte Screenshots (beta-87):**
  - `29_levelauswahl`: drei Level mit Bild, Gebiet, Ziel, Werkzeug;
  - `30_level1`, `32_level2`: ganzes Level im Bild, Ziel mit Grenzen, Zielbereich gestrichelt, nur Eimer und Hand bzw. nur Eimer;
  - `33_auswahl_mit_medaille`: Level 1 als gelöst markiert;
  - `34_level3_quer_nicht_erreichbar`: Querformat, „Da kommst du nicht hin“, Levelname zweizeilig, Grenzen umbrochen.
- **App-Test Level 1:** In beta-86 bis beta-88 war Level 1 erst nach 5:40 bis 5:45 ohne Medaille gelöst.
  - Meine erste Vermutung war falsch: Der Finger lag nicht zu kurz (`7a069b7` hielt ihn 1,2 s, ohne Wirkung).
  - Tatsächlich traf der Eimer kein Erdreich. Nach etwa 1,5 s ist Wasser aus dem Becken in den linken Rand des Pfropfs gesickert, genau auf Zelle 48/45. Der leere Eimer nimmt, was unter dem Finger liegt: 40 Zellen Wasser. Der Teich füllt sich dann nur langsam über die Ufermulde.
  - Gegenprobe in der Simulation, Griff nach 1 bis 3 s: auf 48/45 ab 1,5 s Wasser und kein Erfolg; auf 50/45, 51/45 und 52/46 immer Erde und Gold (31 bis 43 s, 1 Griff).
  - Der App-Test tippt jetzt auf die Mitte des Pfropfs (52/46) und verlangt Gold. Der Lösungstest greift bei 1 s und bleibt bei 48/45.
  - Für Spieler gilt: Wer zögert und den Rand des Pfropfs antippt, schöpft Wasser. Das ist die Regel des Eimers, kein Fehler.

## Zur Freigabe

1. **Level 3, Ziegelrinne:** Soll der Eimer lose Stoffe einlagern können, damit der Ziegelweg (Lehmrinne brennt an der Lava zu Ziegel) als vierter Weg dazukommt? Siehe Vorschlag oben.
2. **Level 3, Höhlenform:** Soll ich die Gänge organischer formen (unregelmäßige Wände, gleiche Wege)?
3. **Reichweite:** Gilt die Regel „nur was zum Himmel offen ist“ so, oder soll sie auch im Spielplatz im Modus „nur Beschaffen“ gelten?
4. **Freischalten:** Alle Level sind offen. Soll später ein Level erst nach dem vorigen offen sein?
