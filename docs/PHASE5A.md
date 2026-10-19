# Phase 5a: Bewohner, Grundverhalten

Die Bewohner nach Leitfaden V3, Abschnitt 7a, erster Teil. Sie sind kleine Figuren im Stil von Lemmings. Sie sind Ziele, keine Mitspieler: Sie folgen festen Regeln, und der Spieler kann vorhersagen, was sie tun.

**Build:** [beta-116](https://github.com/shootcat/ReAct/releases/tag/beta-116)

## Überblick

| Punkt | Stand |
|---|---|
| Laufen, Stufen bis 2 Zellen, Fallen, Ohnmacht bei großer Fallhöhe | gebaut, getestet |
| Meiden: Feuer, Rauch, Lava, tiefes Wasser, Abgründe | gebaut, getestet (Rauch nur als Sicht, Atemnot folgt in 5b) |
| Ausweichen vor fallenden und rollenden Körpern, Treffer = Ohnmacht | gebaut, getestet |
| Schwimmen kurzer Strecken, Erschöpfung = Untergehen = Ohnmacht | gebaut, getestet |
| Über Brücken und Flöße, sofern sie tragen | gebaut, getestet (das Gewicht drückt das Floß ein, ein zu kleines Floß kippt) |
| Kein sichtbares Sterben: Ohnmacht, dann zurück ins Dorf | gebaut, getestet |
| Spielplatz: Werkzeug „Bewohner“ (ein Tippen = ein Bewohner) und „Dorf“ | gebaut, im App-Test geprüft |

## Was ein Bewohner ist

- Eine Figur ist 4 Zellen hoch und 1 Zelle breit (`Folk.HEIGHT`). Ihre Füße stehen in einer Reihe, darunter muss etwas tragen.
- Sie ist **kein Teil des Rasters**. Sand, Wasser und Körper fließen um sie herum und durch sie hindurch. Die Figur schaut ins Raster, um zu wissen, wo sie ist.
- Getragen wird sie von Fels, Erde, Sand, Schnee, Holz, Stein und jedem Körper. Durchgehen kann sie Luft, jedes Gas, Gras, Moos, Laub, Ranken und kleine Tiere.
- Alles folgt Regeln, in der Reihenfolge der Figuren, **ohne Zufall** (`World.hash` enthält die Bewohner; zwei gleiche Läufe ergeben dieselbe Welt). Rückgängig machen nimmt sie mit zurück.

## Die Regeln mit ihren Werten

| Regel | Wert |
|---|---|
| Gehen | eine Zelle alle 8 Schritte (7,5 Zellen je Sekunde) |
| Rennen (vor einem Körper) | eine Zelle alle 3 Schritte, für 1,5 s |
| Stufen | bis 2 Zellen hoch, wenn über dem Kopf Platz ist; 3 Zellen: kehrt um |
| Wand | kehrt um |
| Fallen | schneller um 0,15 Zellen je Schritt, höchstens 2,5 Zellen je Schritt |
| Ohnmacht durch Sturz | ab 12 Zellen Fallhöhe (dreimal die eigene Größe); in tiefes Wasser erst ab 40 Zellen |
| Abgrund | von einer Kante mit 9 oder mehr Zellen Tiefe geht er nicht hinunter; niedrigere Kanten steigt er hinab |
| Sehen | alle 4 Schritte bis 10 Zellen voraus, in Kopf- bis Fußhöhe; feste Dinge versperren die Sicht |
| Was er meidet | Feuer, Glut, Lava, flüssiges Metall, Magma, Rauch, Dampf, alles über 90 °C |
| In etwas Heißem | Feuer, Lava, heißer Dampf in seinen Zellen: Ohnmacht |
| Wasser | bis 2 Zellen tief watet er hindurch; ab 3 Zellen kehrt er um |
| Schwimmen | hineingefallen schwimmt er (eine Zelle alle 12 Schritte), Kopf über Wasser; am Ufer steigt er aus, wenn der Boden höchstens 3 Zellen höher liegt |
| Erschöpfung | nach 6 s Schwimmen geht er unter; nach 1,5 s unter Wasser: Ohnmacht |
| Ausweichen | vor einem Körper schneller als 0,12 Zellen je Schritt, der über ihm fällt (bis 30 Reihen darüber) oder auf ihn zurollt (bis 14 Zellen weit): rennt weg, wenn er kann |
| Treffer | ein Körper in seinen Zellen, der schneller als 0,2 Zellen je Schritt ist oder von oben über den Kopf kommt: Ohnmacht |
| Verschüttet | kommt etwas Festes in seine Zellen (Sand von oben), steht er daraus auf (bis 12 Zellen); geht das nicht: Ohnmacht |
| Floß, Brücke | er geht auf jedem Körper; bewegt sich der Körper, nimmt er ihn mit (der Punkt, auf dem er steht, im Rahmen des Körpers) |
| Gewicht | wie 4 Zellen Wasser, wie ein Mitfahrer (Phase 4b): ein Floß sinkt tiefer und neigt sich zu ihm hin |
| Ohnmacht | er liegt 2,5 s still, Sterne über dem Kopf. Dann wird er ins Dorf gebracht und blendet dort in 0,75 s ein. Ohne Dorf kommt er dorthin, wo er zuerst gestanden hat. |
| Dorf | einmal dort angekommen, bleibt er darin (geht zwischen den Rändern 10 Zellen links und rechts hin und her) |

Gemessen in den Tests (`FolkTest`, 13 Fälle, alle grün):
- **Stufen:** bis Spalte 59 gekommen, die dritte Stufe nicht.
- **Lava:** voraus gesehen, bei Spalte 73 umgekehrt (die Lava beginnt bei 90).
- **Fackel aus brennendem Holz:** vor Spalte 88 umgekehrt.
- **Wasser:**
  - Durch den Tümpel (2 tief) gewatet, vor dem tiefen Becken (7 tief) umgekehrt.
  - In einen See 20 breit gefallen: hinübergeschwommen, ausgestiegen.
  - In einen See 120 breit gefallen: erschöpft, untergegangen, ohnmächtig, im Dorf.
- **Kante:** durch die Mulde (2 tief) gegangen, vor der Schlucht (18 tief) umgekehrt.
- **Sturz aus 49 Zellen:** Ohnmacht, nach 3,3 s im Dorf.
- **Fallender Steinblock 6 × 6:**
  - Frei läuft er darunter weg.
  - Zwischen zwei Wänden so breit wie der Block trifft ihn der Block: Ohnmacht.
- **Brücke:** eine Planke über eine Schlucht, an beiden Enden auf dem Fels; er geht hinüber.
- **Floß:**
  - 16 × 3 Zellen trägt zwei Figuren: Es liegt im Mittel 0,35 Reihen tiefer (49,36 → 49,71), und beide bleiben darauf.
  - 8 × 2 Zellen kippt, als die Figur an den Rand geht (bis 49°). Sie fällt ins Wasser und schwimmt.

## Spielplatz

- In der Leiste „Werkzeug“ gibt es zwei neue Werkzeuge.
  - **„Bewohner“:** Ein Tippen setzt einen Bewohner. Ein Strich setzt auch nur einen. In die Erde getippt, kommt er obendrauf; in die Luft getippt, fällt er.
  - **„Dorf“:** Ein Tippen setzt das Dorf auf den Boden darunter.
- Unter den Werkzeugen steht, wie viele Bewohner es gibt und wie viele davon im Dorf sind.
- Die App zeichnet die Figuren als kleine Menschen über die Welt. Sie gehen mit schwingenden Armen und Beinen, reißen beim Fallen die Arme hoch, schwimmen mit dem Kopf über Wasser und liegen in Ohnmacht auf dem Rücken mit Sternen. Das Dorf ist eine Hütte mit Fahne.
- Das Dorf ist im Spielplatz ein Zeichen, kein Material. In den Leveln von 5c werden Häuser aus Material gemalt (Schnee auf den Dächern).

## Bilder

Szenen aus der Simulation (`FolkPicturesTest`):

- `docs/phase5a/5a_stufen.png`: zwei Stufen hinauf, vor der dritten umgekehrt
- `docs/phase5a/5a_lava.png`: Lava voraus gesehen, umgekehrt
- `docs/phase5a/5a_sturz.png`: Sturz, Ohnmacht, zurück ins Dorf
- `docs/phase5a/5a_schwimmen.png`: in den See gefallen, hinübergeschwommen, ausgestiegen
- `docs/phase5a/5a_ausweichen.png`: unter einem fallenden Block weggelaufen
- `docs/phase5a/5a_bruecke.png`: über die Brücke

Aus der laufenden App (`Phase5aAppTest`, an jedem Release):
- `5a_bewohner_0s.png`, `5a_bewohner_5s.png`, `5a_bewohner_15s.png`: Dorf und fünf Bewohner, getippt auf der Wiese.
- Der Test prüft: fünf Tippen ergeben fünf Bewohner, ein Strich einen.

## Leistung

Messwelt „Größte Welt 600 × 340, voll aktiv belebt, 150 Körper und 50 Bewohner“ (neu in „Leistung messen“):

| | ohne Bewohner | mit 50 Bewohnern |
|---|---|---|
| Schritt (dieser Rechner) | 23,2 ms | 24,3 ms |
| davon Bewohner | – | 0,01 ms |

Der Rechner war bei dieser Messung ausgelastet: Dieselbe Welt ohne Bewohner hatte in 4c 18,0 bis 18,3 ms. Die Bewohner selbst kosten 0,01 ms je Schritt. Den Wert auf dem Gerät liefert „Leistung messen“; messen kann ich dort nicht.

## Abweichungen und offene Punkte

- **Rauch** meiden die Bewohner nur, weil sie ihn sehen. Atemnot im Rauch und Grubengas gehört zu 5b.
- **Gewicht auf Brücken:** Eine Brücke aus festem Bau, die an beiden Enden aufliegt, trägt immer. Gewicht spielt nur bei Körpern eine Rolle (Floß, lose Planke).
- **Durchlässigkeit:** Sand und Wasser fließen durch die Figuren. Ein Bewohner kann nichts aufhalten oder stauen. Wird er verschüttet und kann nicht aufstehen, fällt er in Ohnmacht.
- **Zu kleines Floß:** Es kippt nach der Physik der Körper, wenn eine Figur an den Rand geht. Das ist gewollt („sofern diese tragen“).
