# Leveldesign: Welt „Test“

Drei Testlevel nach Leitfaden V2 (`LEITFADEN_REACT_V2.md`): ein leichtes Level, eines aus der Mitte des Spiels und eines an der Obergrenze, jedes aus einem anderen Gebiet. Pro Level: Einsicht, Fallen, Köder, Kette der Reaktionen, Kennzahlen und Startbild. Die früheren Level p_01 … p_10 stehen in der Git-Historie.

Kennungen in den Musterlösungen: Dinge heißen nach Typ und Startfeld (`fire_0_4`); was die Welt erzeugt, bekommt eine laufende Nummer (`charcoal#1`). Eine Verschmelzung behält die Kennung des Ziels: Feuer auf `charcoal#1` ergibt Glut mit der Kennung `charcoal#1`.

## t_01 „Kochstelle“ (Wald, leicht)

![Startzustand](screenshots/test/t_01.png)

**Einsicht:** Glut erhitzt, ohne zu zünden.

**Ziele:** Bring das Wasser im Becken zum Kochen (`heat`, Becken (7,7)–(7,8)). Der Wald darf nicht brennen (`preserve`, beide Bäume).

**Material:** ein Holzstück, ein Lagerfeuer, eine kleine Pfütze in einer Mulde. Die Pfütze hat 2 Einheiten Wasser und reicht für genau ein Löschen.

**Karte:** oben eine Lichtung mit Lagerfeuer, Felsbrocken, Holz und der Mulde; darunter am Waldrand das Becken, ein schmaler Brunnen. Es ist nur über ein einziges Ablagefeld erreichbar, und das grenzt an den ersten Baum.

**Kette:** Feuer neben das Holz, das Holz brennt (2 Züge). Die Pfütze daneben löscht es zu Holzkohle. Feuer auf die Holzkohle ergibt Glut. Glut aufs Feld am Becken bringt das Wasser zum Kochen; der Baum daneben bleibt grün.

**Musterlösung (4 Züge):** `fire_0_4@5,4` (Feuer rechts ans Holz), `water_3_5@3,4` (Pfütze links ans brennende Holz), `fire_0_4@4,4` (Feuer auf die Holzkohle), `charcoal#1@7,6` (Glut ans Becken).

**Fallen** (je ein Test in `TrapsTest`):
- *Feuer direkt ans Becken:* Das Wasser löscht es, aber im selben Schritt steckt es den Wald an. Danach unlösbar.
- *Zu spät gelöscht:* Holz anzünden und im nächsten Zug etwas anderes tun. Am Ende dieses Zuges ist es Asche, Brennstoff gibt es keinen mehr. Danach unlösbar.
- *Pfütze löscht die Flamme statt des Holzes:* Pfütze aufs Feld über der Feuerstelle. Das Feuer ist weg, das Holz verbrennt. Gleich wirkt das Feuer auf dem Feld über der Mulde oder die Pfütze auf dem Feld über dem Lagerfeuer.
- Weitere Sackgassen ohne eigenen Test: Pfütze ins Becken kippen (Löschwasser weg) oder brennendes Holz ins Becken (löscht, steckt aber den Wald an).

**Köder:** kein eigenes Köder-Objekt, das Material ist genau abgezählt. Der Köder ist das Feld am Becken: Dort will man das Feuer hinstellen.

**Ablagefelder:** 8 bei 4 Zügen (2 pro Zug). Falsch-Felder:
- am Becken (für das Feuer);
- über der Mulde (Feuer dort wird gelöscht);
- über der Feuerstelle rechts vom Holz (Pfütze dort löscht die Flamme);
- über dem Lagerfeuer (Pfütze dort löscht das Feuer).

Das Feld am Becken ist das einzige direkt am Hauptziel; es ist die Falle für das Feuer und zugleich der Platz für die Glut.

**Kennzahlen (DifficultyReport):**
- Mindestzüge 4, durch vollständige Suche bewiesen.
- 24 Lösungen mit 4 Zügen. Alle sind Spielarten derselben Kette: andere Reihenfolge, anderer Ort zum Anzünden, oder die Holzkohle ans Becken tragen und dort das Feuer drauflegen.
- 26 % der ersten Züge (5 von 19) führen in eine Sackgasse.
- Der naheliegende Zug, irgendetwas aufs Feld am Becken, führt nicht zur Lösung.

**Ehrlichkeitsprüfung:**
- *Kürzer?* Nein: Die vollständige Suche findet mit 3 Zügen keine Lösung.
- *Offensichtlicher?* Nein. Feuer und brennendes Holz bringen Wasser nicht zum Kochen, und jede Flamme am Becken steckt den Wald an. Ohne Flamme heiß ist nur Glut.
- Die Abkürzung „brennendes Holz im Becken löschen“ ist versperrt, weil das einzige Feld am Becken neben dem Wald liegt.

**Erwartung:** gelöst in etwa einer Minute.

