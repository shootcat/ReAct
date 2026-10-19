# Phase 5b: Bewohner, Bedürfnisse und begrenzte Eigenständigkeit

Die Bewohner nach Leitfaden V3, Abschnitt 7a, zweiter Teil. Sie brauchen jetzt **Wärme, Wasser und Luft**. Fehlt ihnen etwas, zeigen sie es mit einem kleinen Symbol über dem Kopf. Selbstständig tun sie dreierlei:

- Frierend gehen sie an ein Feuer.
- Frierend zünden sie Holz an einer Flamme an, die schon brennt.
- Durstig trinken sie Süßwasser.

Alles folgt festen Regeln, ohne Zufall.

**Build:** [beta-117](https://github.com/shootcat/ReAct/releases/tag/beta-117)

## Überblick

| Punkt | Stand |
|---|---|
| Wärme: frieren, langsamer, nach längerer Zeit Ohnmacht | gebaut, getestet |
| Wasser: Durst, trinkt Süßwasser, das er erreicht; nie Salzwasser | gebaut, getestet |
| Luft: Rauch und Grubengas, Atemnot, Flucht ins Freie | gebaut, getestet |
| Symbole über dem Kopf, wenn kritisch | gebaut, in der App und in den Szenenbildern |
| Lagerfeuer an vorhandener Flamme oder Glut, nie aus dem Nichts | gebaut, getestet |
| Deterministisch, im Schnappschuss und im Hash | getestet |
| Einträge im Entdeckungsbuch für die Bewohner-Regeln | 10 Einträge, jede Szene zeigt ihre Regel |

## Die Regeln mit ihren Werten

Jedes Bedürfnis ist ein Zähler je Figur, in Schritten (60 je Sekunde).

### Wärme

| Regel | Wert |
|---|---|
| Gefühlte Temperatur | Mittel der 4 Zellen der Figur (Luft oder Wasser) |
| Kälte steigt | unter 5 °C um 1 je Schritt, unter −10 °C um 2 |
| Kälte sinkt | ab 5 °C um 1, ab 15 °C um 2; am Feuer (bis 6 Zellen) oder in Luft ab 25 °C um 6 |
| Friert (Schneeflocke über dem Kopf, zittert) | ab 600: bei −20 °C nach 5 s, bei 0 °C nach 10 s |
| Geht langsamer (halbes Tempo, Schneeflocke mit dunkelblauem Rand) | ab 1200 |
| Ohnmacht | bei 2400: bei −20 °C nach 20 s, bei 0 °C nach 40 s |
| Frierend sucht er Wärme | bis ihm ganz warm ist (Kälte 0), nicht nur bis unter die Schwelle |
| Was wärmt | Flammen, Glut, Lava, flüssiges Metall, alles Brennende oder über 300 °C Heiße, sichtbar in seinen Reihen bis 6 Zellen |
| Feuerschein | frierend sieht er ein Feuer bis 40 Zellen weit nach beiden Seiten und dreht sich dorthin |
| Ans Feuer | frierend geht er auf ein Feuer zu (auch durch dessen Rauch), statt umzukehren; er bleibt 4 Zellen vor dem Feuer oder seiner heißen Luft (ab 90 °C) stehen und hält die Hände hin |
| Zu nah | kommen Flammen oder heiße Luft auf 2 Zellen heran, tritt er zurück, ohne das Feuer aus dem Blick zu lassen |
| Warm | ist ihm warm, geht er weiter; nicht frierend kehrt er vor Feuer wie in 5a in 10 Zellen um |

### Lagerfeuer

| Regel | Wert |
|---|---|
| Wann | frierend, kein Feuer bis 6 Zellen, höchstens einmal in 10 s |
| Was er braucht | eine **vorhandene** Flamme, Glut oder etwas Brennendes **und** Brennholz (Holz, Totholz, Holzkohle, Welkes), beides bis 12 Spalten weit und etwa in seiner Höhe |
| Welches Holz | das nächste lose Holz neben ihm, nie das unter seinen Füßen (Brücke) und nie verankerter Boden |
| Was geschieht | er kniet sich 0,75 s mit einer Flamme in der Hand hin und bringt die Holzzelle auf Zündtemperatur (+120 °C). Ob sie brennt, entscheidet die Welt: nasses Holz brennt nicht. Danach wärmt er sich daran. |
| Nie aus dem Nichts | ohne Flamme in Reichweite zündet er nichts an (Test: 15 s im Frost, 0 Mal) |

### Wasser

| Regel | Wert |
|---|---|
| Durst steigt | um 1 je Schritt |
| Durstig (Tropfen über dem Kopf) | ab 7200 (2 Minuten) |
| Trinken | durstig trinkt er Süßwasser an seinen Füßen oder eine Zelle daneben (watend, am Ufer, schwimmend). Eine Zelle Wasser ist danach weg. |
| Salzwasser | trinkt er nie, auch nicht durstig |
| Ohnmacht | bei 18000 (5 Minuten) |
| Dorf | hat einen Brunnen: dort sinkt der Durst um 30 je Schritt |

### Luft

| Regel | Wert |
|---|---|
| Schlechte Luft | Rauch, schwarzer Rauch, Grubengas, Gas aus Erdöl, in Kopf oder Brust (die oberen zwei Zellen) |
| Atem | 480 Schritte (8 s); sinkt in schlechter Luft um 1, in schwarzem Rauch um 2; erholt sich in sauberer Luft um 4 je Schritt |
| Atemnot (Rauchwölkchen über dem Kopf) | sobald er schlechte Luft atmet, und solange der Atem unter 360 ist |
| Flucht ins Freie | nach 1 s schlechter Luft rennt er zur näheren sauberen Luft (bis 40 Zellen weit in Kopfhöhe, so weit Wände es zulassen) und rennt, solange er drin ist |
| Grubengas sieht er nicht | er geht hinein; Rauch sieht er und kehrt davor um (5a) |
| Ohnmacht | Atem 0: im eingeschlossenen Grubengas nach 8 s |

Nach jeder Ohnmacht wird er im Dorf (oder am Startpunkt) versorgt: warm, ohne Durst, mit vollem Atem.

### Eigenständigkeit

Die Bewohner tun von sich aus nur vier Dinge, und alle hängen an dem, was der Spieler herstellt:

- ans Feuer gehen,
- Holz an einer vorhandenen Flamme anzünden,
- Süßwasser trinken, an das sie geraten,
- aus schlechter Luft laufen.

Sie suchen kein Wasser und bauen nichts. Sie machen keine Flamme aus dem Nichts. Dadurch kann der Spieler sie lenken, etwa mit Holz neben einem Feuer oder mit Wasser in ihrem Weg. Ein Rätsel lösen sie so nicht von selbst.

## Messungen (`FolkNeedsTest`, 12 Fälle, alle grün)

- **Frost −20 °C:**
  - Friert nach 5,0 s, wird langsamer nach 10,0 s, Ohnmacht nach 20,0 s.
  - Er ging 30 Zellen in 4 s, solange er nicht fror, durchgefroren 15.
  - Nach der Ohnmacht ist ihm wieder warm.
- **0 °C:** friert nach 10,0 s, Ohnmacht nach 40,0 s. **20 °C:** nach 60 s Kälte 0.
- **Lagerfeuer** (Holz 3 × 4 Zellen ab Spalte 100, angezündet, −20 °C, Bewohner frierend):
  - Er geht hin und bleibt bei Spalte 90 stehen, nach 2,7 s, an der heißen Luft vor dem Feuer.
  - Nach weiteren 3,9 s ist ihm warm, danach geht er wieder weg (Spalte 62).
- **Dasselbe Feuer, nicht frierend:** Er kommt nur bis Spalte 85 und kehrt um.
- **Anzünden** (Holzklotz 3 × 2 bei Spalte 80, brennender Pfahl bei 96, −20 °C):
  - Er kniet nach 2,2 s bei Spalte 76 nieder. 1,5 s später brennen 5 Zellen des Klotzes, und er wärmt sich daran.
  - Ohne Flamme: 15 s lang kein Anzünden, keine brennende Zelle. Er erfriert (Kälte 2400), denn aus dem Nichts macht er kein Feuer.
  - Nicht frierend (20 °C): in 30 s kein Anzünden.
- **Trinken:**
  - Durstig watet er in den Teich und trinkt nach 2,6 s; das Wasser geht von 32 auf 31 Zellen zurück.
  - Nicht durstig trinkt er in 20 s nicht.
- **Salzwasser:** in 20 s 0-mal getrunken, 36-mal abgelehnt; der Durst bleibt (8500).
- **Durst:**
  - Ohne Wasser Ohnmacht nach 300 s.
  - Im Dorf ist der Durst nach 5,7 s gestillt (Brunnen).
- **Grubengas mit Ausweg** (Stollen 6 hoch, Gas oben, 50 Zellen lang):
  - Er rennt einmal los. Sein Atem sinkt auf 309 von 480, dann steht er draußen (Spalte 14) mit vollem Atem.
  - Keine Ohnmacht.
- **Grubengas eingeschlossen:** Ohnmacht nach 8,0 s.
- **Rauch im Freien:** Der Rauch steigt schneller auf, als der Atem sinkt; keine Ohnmacht.
- **Gleicher Ablauf zweimal:** derselbe Hash. Rückgängig machen stellt Kälte, Durst und Atem wieder her, und danach geht es gleich weiter.

## Entdeckungsbuch

Neue Gruppe **„Bewohner“** mit 10 Einträgen. Jeder hat eine eigene kleine Szene mit Figuren, und der Test zeigt, dass jede Szene ihre Regel innerhalb von 20 s auslöst:

| Eintrag | Szene |
|---|---|
| kehren um, wenn sie Feuer, Lava oder Rauch vor sich sehen | Lava auf dem Boden |
| fallen aus mehr als dreifacher Körperhöhe in Ohnmacht und kommen ins Dorf zurück | Sturz aus 26 Zellen |
| schwimmen eine kurze Strecke, dann verlässt sie die Kraft | See über die ganze Breite |
| laufen unter einem fallenden Block weg | Steinblock über der Figur |
| frieren in der Kälte, werden langsamer und fallen zuletzt in Ohnmacht | Schnee, −20 °C |
| gehen frierend an ein Feuer heran und wärmen sich daran | brennender Holzstapel, −20 °C |
| entzünden frierend Holz an Glut oder Flamme, nie aus dem Nichts | Holzklotz und Glut, −20 °C |
| trinken Süßwasser, wenn sie durstig sind | flacher Teich |
| trinken kein Salzwasser, auch nicht durstig | flaches Salzwasser |
| bekommen in Rauch und Grubengas keine Luft und laufen ins Freie | Stollen mit Grubengas unter der Decke |

Im Buch stehen sie unter dem Kopf „Bewohner“ mit einer kleinen Figur. Die Szene zeigt die Figuren mit ihren Symbolen.

## Bilder

Szenen aus der Simulation (`FolkPicturesTest`, Bildstreifen mit Text unter jedem Bild):

- `docs/phase5b/5b_frost.png`: friert nach 5 s, langsamer nach 10 s, Ohnmacht nach 20 s
- `docs/phase5b/5b_feuer.png`: frierend ans Lagerfeuer, aufgewärmt, wieder weg
- `docs/phase5b/5b_anzuenden.png`: zündet den Holzklotz am brennenden Pfahl an, wärmt sich daran
- `docs/phase5b/5b_trinken.png`: durstig, watet in den Teich und trinkt
- `docs/phase5b/5b_salzwasser.png`: durstig am Salzwasser, trinkt nicht
- `docs/phase5b/5b_grubengas.png`: Atemnot im Grubengas, läuft ins Freie
- `docs/phase5b/5b_grubengas_zu.png`: eingeschlossen im Grubengas, Ohnmacht nach 8 s

Aus der laufenden App (`Phase5bAppTest`, an jedem Release):

- `5b_frost_feuer.png`: −20 °C im leeren Feld, zwei Bewohner getippt. Bilder in dem Moment, in dem sie frieren, anzünden und sich wärmen, zuletzt warm.
- `5b_durst_rauch.png`: Ein durstiger Bewohner trinkt am Teich. Einer, der in eine Rauchwolke getippt wurde, bekommt Atemnot und läuft hinaus. Bilder bei 0, 1, 2 und 4 s.

Der Test prüft:

- Die Bewohner frieren, zünden an, wärmen sich und werden wieder warm.
- Der durstige Bewohner trinkt.
- Der Bewohner im Rauch hat Atemnot.
- Niemand wird ohnmächtig.

## Leistung

Messwelt „Größte Welt 600 × 340, voll aktiv belebt, 150 Körper und 50 Bewohner“ (in „Leistung messen“), auf diesem Rechner:

| | ohne Bewohner | mit 50 Bewohnern (5b) |
|---|---|---|
| Schritt | 20,65 ms | 21,32 ms |
| davon Bewohner | – | 0,01 ms |

Die Bedürfnisse kosten fast nichts:

- Jede Figur liest je Schritt etwa 80 Zellen: ihre Temperatur, das Fühlen der Wärme bis 6 Zellen und die Luft am Kopf.
- Nur frierende Figuren suchen alle 4 Schritte nach Feuerschein (bis 40 Zellen) und nach Holz und Flamme (25 × 7 Zellen).

Der Unterschied der ganzen Schritte (0,7 ms) liegt im Rauschen des ausgelasteten Rechners. Den Wert auf dem Gerät liefert „Leistung messen“.

## Abweichungen und offene Punkte

- **Glut im Frost:** Zwei bis sechzehn Zellen Glut gehen bei −20 °C in einer halben Sekunde aus und werden zu Holzkohle. Das ist die bestehende Physik der Glut (sie kühlt unter ihre Zündtemperatur). Im Test zum Anzünden dient deshalb ein brennender Holzpfahl als Flamme. In der Buchszene reicht die Glut, weil sie dort lange genug glüht.
- **Großes Feuer:** An einem großen, rauchenden Holzstapel dauert das Aufwärmen länger. Der Bewohner läuft vor herabfallenden Stücken (5a) und vor dichtem Rauch davon und kommt dann zurück, weil er den Feuerschein sieht.
- **Rauch im Freien** steigt schnell auf. Ein Bewohner darin hat kurz Atemnot, muss aber selten fliehen. Unter einer Decke (Stollen) bleibt Grubengas stehen, dort ist es gefährlich.
- **5a-Bilder aus der App:** Der Ausschnitt in Build 116 schnitt die Füße der Figuren ab. Die Tipppunkte liegen jetzt auf der flachen Wiese, und der Ausschnitt richtet sich nach der Höhe der Figuren (Build 117).
