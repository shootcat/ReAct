# Phase 5c: Ziele mit Bewohnern, Level „Bergleute“ und „Winternacht“

Die Bewohner nach Leitfaden V3, Abschnitt 7a, dritter Teil: **Ziele, keine Mitspieler.** Die Bewohner gehorchen nicht.
Der Spieler formt die Welt, und sie tun von sich aus, was ihre Regeln aus 5a und 5b sagen:
- Sie gehen Wege.
- Sie fliehen vor Gas.
- Sie wärmen sich am Feuer.
- Sie zünden Holz an einer Flamme an, die schon brennt.

Dazu kommen drei neue Zieltypen und zwei Testlevel mit gemessenen Lösungswegen und Fallen.

**Build:** [beta-122](https://github.com/shootcat/ReAct/releases/tag/beta-122); Nachtrag (Holzkohle-Korrektur, neuer Herd in
„Winternacht“): [beta-123](https://github.com/shootcat/ReAct/releases/tag/beta-123)

## Überblick

| Punkt | Stand |
|---|---|
| Ziel: bringe mindestens n Bewohner ans Ziel (`bring`) | gebaut, getestet |
| Ziel: rette eingeschlossene Bewohner ins Dorf (`rescue`) | gebaut, getestet |
| Ziel: halte das Dorf eine Zeit lang warm (`keep_warm`) | gebaut, getestet; zwei Formen, siehe unten |
| Level 6 „Bergleute“ (Mine) | 4 Lösungswege, 2 Fallen, alle getestet |
| Level 7 „Winternacht“ (Berg) | 3 Lösungswege, 4 Fallen, alle getestet; Verlässlichkeit über Zeitpunkt und Ablagestelle gemessen |
| Neue Materialien | keine nötig; Vorschläge (Kleidung, Baumwolle, Nahrung, Seil, Fackel) in `docs/ELEMENTE.md` |
| Leistung: 50 Bewohner, voll aktive Welt, 150 Körper | auf dem Rechner gemessen; den Wert auf dem Gerät liefert „Leistung messen“ |
| Nachweis aus der App | `Phase5cAppTest`, Bilder am Release |

## Neue Ziele

Ein Ziel sagt, was erreicht werden soll, nie wie (Leitfaden 5.2). Neu ist, dass ein Ziel auch **scheitern** kann.
Dann ist das Level sofort verloren, und die App nennt das Ziel, das gescheitert ist.

- **`bring`:** Mindestens `amount` Bewohner stehen im Zielbereich auf den Beinen. Wer ohnmächtig ist oder gerade
  zurückgebracht wird, zählt nicht.
- **`rescue`:** Mindestens `amount` Bewohner sind im Dorf und stehen. Im Level gilt dazu `"faint_return": "start"`:
  Ein Ohnmächtiger wird dorthin zurückgebracht, wo er eingeschlossen war, nie ins Dorf. Sonst wäre eine Ohnmacht ein
  bequemer Weg hinaus.
- **`keep_warm`** in zwei Formen:
  - *Mit `min`:* Die Luft im Bereich (die Häuser) bleibt bis zum Ende der Nacht (`seconds`) mindestens `min` °C
    warm. Wird sie kälter, scheitert das Ziel (nach 2 s Anlauf).
  - *Ohne `min`:* Niemand erfriert. Das Ziel scheitert, sobald ein Bewohner vor Kälte ohnmächtig daliegt. Diese
    Form nutzt „Winternacht“; warum, steht unter „Abweichungen“.
- Neu im Levelformat:
  - `folk`: die Figuren mit Platz und Blickrichtung, das Dorf und `faint_return`;
  - `heat`: kann mit `material` nur ein Material erwärmen, etwa das Feuer und nicht den Fels darunter.

Tests: `PlayTest` (vier neue Fälle), dazu die Level-Tests unten.

## Level 6 „Bergleute“ (Mine, Kernniveau)

Vier Bergleute sitzen tief im Berg in einem Stollen fest. Der Weg hinaus führt eine Rampe hinauf und waagerecht in die
Felswand über dem Dorf, ist aber verschüttet. Hinter dem Schutt hat sich Grubengas gesammelt, und aus Rissen in der
Decke sickert weiteres nach. In der Mündung, direkt vor dem Schutt, brennt das Feuer der Hirten in einer Grube voll
Teer. Im Dorf gibt es einen Brunnen und einen Haufen Sand. Werkzeug ist nur der Eimer (30 Zellen).

Ziel: **Bring mindestens drei Bergleute ins Dorf.**

Die Falle aus der Physik: Gräbt man den Schutt weg, solange das Feuer brennt, strömt das Gas ans Feuer, und die ganze
Wolke geht hoch. Bergleute in der Wolke fallen in Ohnmacht und werden in den Stollen zurückgebracht. Das nachsickernde
Gas brennt danach als Flamme an der Mündung weiter, an der niemand vorbeigeht.

| Weg | Handgriffe | Zeit | Medaille | Explosionen, Ohnmachten |
|---|---|---|---|---|
| Den vorderen Teil des Schutts tief in die brennende Grube kippen, dann den Rest ausgraben | 6 | 24,0 s | Gold | 0, 0 |
| Mit Brunnenwasser löschen, dann graben | 8 | 24,0 s | Silber | 0, 0 |
| Mit Sand vom Dorf ersticken, dann graben | 8 | 22,0 s | Silber | 0, 0 |
| Graben, das Gas explodiert, dann die Gasflamme mit Sand ersticken | 8 | 30,7 s | Silber | 1, 1 |
| *Falle:* graben, das Feuer brennen lassen | 6 | nicht gelöst in 120 s | – | 2, 2 |
| *Falle:* graben, dann Wasser in die Gasflamme (es verdampft) | 8 | nicht gelöst in 120 s | – | ≥ 1 |
| ohne Eingriff | 0 | nicht gelöst in 120 s, niemand kommt heraus | – | – |

**Robustheit:** Alle Wege habe ich zusätzlich mit 0 bis 5 s Wartezeit vor dem ersten Griff gemessen.
- Sand, dann graben: gewinnt immer.
- Explosion, dann Sand: gewinnt immer.
- Wasser, dann graben: gewinnt bis 4 s Wartezeit.
- Schutt aufs Feuer: gewinnt immer, mit Gold bis 4 s und Silber bei 5 s. Das gilt, wenn der Schutt tief in die Grube
  fällt (bei 59, 64). Oben auf das Feuer gekippt (bei 59, 62) entsteht ein Haufen in Kopfhöhe. Dann gewann der Weg
  nur in 2 von 7 Fällen: Die Bergleute flohen vor dem Gas zurück, weil der Haufen ihnen den Weg zur frischen Luft
  versperrte.

Bilder: `docs/phase5c/level6_*.png` (Start, die vier Wege, die zwei Fallen).

## Level 7 „Winternacht“ (Berg, Kernniveau)

Ein Dorf auf einem Felsband hoch im Berg, eine Nacht bei −15 °C. Es gibt vier Bewohner und zwei Holzhäuser mit
Schnee auf den Dächern. Auf dem Platz steht ein niedriger Steinherd mit einer **Glutgrube**: Eine Reihe Holz brennt
auf einem Bett aus Holzkohle. Die Glut zieht sich von oben nach unten und hält etwa eine Minute, nicht bis zum Morgen.
Eine Felsnase schließt den Platz nach Osten. Holz ist knapp: Hinter dem ersten Haus liegen zwei Scheite. Am Hang gibt
es eine Pechquelle und den Meiler eines Köhlers. Werkzeuge sind Eimer und Hand.

Ziel: **Niemand im Dorf darf bis zum Morgen erfrieren** (Nacht 180 s).

Die Bewohner wärmen sich von selbst an der Glut. Frierend zünden sie Brennholz an, das nahe bei einer Flamme liegt,
aber nie ohne Flamme (5b). Die Hitze des Feuers geht in den Herd und in den Fels und hält den Platz noch eine Weile
warm, nachdem es aus ist.

| Weg | Handgriffe | Ergebnis | Medaille | höchste Kälte (Ohnmacht bei 2400) |
|---|---|---|---|---|
| Die Scheite auf den Herd neben die Glutgrube legen | 2 | Morgen erreicht | Gold | 740 |
| Die Scheite neben dem Herd auf den Boden legen; frierende Bewohner zünden sie an der Glut selbst an | 2 | Morgen erreicht | Gold | 1133 |
| Die Scheite auf den Herd, nach 10 s Kohle aus dem Meiler dazu auf den Herd | 4 | Morgen erreicht | Silber | 1094 |
| ohne Eingriff | 0 | verloren nach 117 s | – | – |
| *Falle:* ein Eimer kalte Kohle in die Glutgrube, sie erstickt | 2 | verloren nach 86 s | – | – |
| *Falle:* die Scheite oben auf die Glutgrube, sie ersticken sie | 2 | verloren nach 60 s | – | – |
| *Falle:* Pech in die Grube, es brennt lange, aber der schwarze Rauch vertreibt die Bewohner | 2 | verloren nach 100 s | – | – |
| *Falle:* die Scheite erst nach 60 s; die Glut ist aus, niemand zündet sie an | 2 | verloren nach 106 s | – | – |
| *hilft nicht:* Schnee vom Dach in die Grube; er nässt das Holz, die Kohle darunter glimmt weiter | 2 | verloren nach 128 s | – | – |

**Wie verlässlich die Wege sind.** Gemessen in einer offenen Nacht: Wann erfriert der erste Bewohner, je nachdem,
wann (Wartezeit vor dem ersten Griff) und wo die Scheite hinkommen? Gewonnen heißt: niemand vor 180 s.

| Weg, Ablagestelle | Wartezeiten | gewonnen |
|---|---|---|
| auf den Herd, x 86 | 0 bis 30 s, je 5 s | 7 von 7 |
| auf den Herd, x 87 | 0 bis 30 s, je 5 s | 7 von 7 |
| auf den Herd, x 88 | 0 bis 40 s, je 2,5 s | 15 von 17 (verloren bei 0 s und 20 s, nach 142 und 137 s) |
| auf den Herd, x 90 (der Scheit kippt über die Kante) | 0 bis 30 s, je 5 s | 5 von 7 |
| auf den Boden, x 64 | 0 bis 32,5 s, je 2,5 s | 14 von 14; ab 35 s ist die Glut zu schwach, verloren |
| auf den Boden, x 60 / 62 / 66 | 0 bis 30 s, je 5 s | 4 / 6 / 6 von 7 |
| auf den Herd, dann Kohle aus dem Meiler | 0 bis 30 s, je 5 s | 7 von 7 |

Zusammen gewinnen die Scheite auf dem Herd in 34 von 38 Nächten, die Scheite am Boden in 30 von 35. Ganz verlässlich
ist es nicht: Die Bewohner gehen, wohin ihre Regeln sie führen, und wie lange der Boden nach dem Feuer warm hält,
hängt davon ab. Wer die Scheite nach etwa 40 s bringt, kommt zu spät.

Bilder: `docs/phase5c/level7_*.png` (Start, drei Wege, vier Fallen, Schnee, ohne Eingriff).

## Feinere Regeln für die Bewohner (aus 5b)

Zwei Regeln aus 5b haben bei großen Feuern versagt. Ich habe sie verfeinert:

1. **Durch Rauch zum Feuerschein.**
   - *Vorher:* Ein Frierender sah den Schein eines Feuers (bis 40 Zellen) und drehte sich hin. Hatte er aber Rauch
     vor sich und noch keine Flamme im Blick, drehte er vor dem Rauch wieder um. So pendelte er hin und her, bis er
     erfror.
   - *Jetzt:* Wer vom Schein angezogen wird, geht durch den Rauch weiter. Bei Atemnot flieht er wie bisher.
2. **Näher heran, solange es erträglich ist.**
   - *Vorher:* Ein Frierender blieb im Zustand „wärmt sich“ stehen, sobald heiße Luft (90 °C) bis 4 Zellen nah war.
     Bei einem großen Feuer ist das schon weit weg. Er spürte dort keine Wärme und erfror im Stehen.
   - *Jetzt:* Spürt er noch nichts, geht er einen Schritt näher, solange dort nichts 60 °C erreicht.

Neue Tests in `FolkNeedsTest` (jetzt 14 Fälle): „durch den Rauch zum Feuerschein“ und „am großen Feuer näher heran,
bis es wärmt“ (warm nach 20 s, keine Ohnmacht). Alle bisherigen 5a- und 5b-Tests sind unverändert grün.

## Neue Materialien

Keine. Beide Level kommen mit vorhandenen Materialien aus:
- Bergleute: Teer, Grubengas, Geröll, Wasser, Sand.
- Winternacht: Holz, Holzkohle, Pech, Schnee.

Vorschläge für Kleidung, Baumwolle, Nahrung, ein Seil aus Pflanzenfasern und eine Fackel stehen in
`docs/ELEMENTE.md`, als Eigenschaften und nicht als Rezepte, mit Kosten und Spielidee. Gebaut ist davon nichts.

## Leistung

Messwelt „Größte Welt 600 × 340, voll aktiv belebt, 150 Körper und 50 Bewohner“ (die Zeile in „Leistung messen“), auf
diesem Rechner, zwei Durchgänge:

| | ohne Bewohner | 50 Bewohner | 50 frierende Bewohner |
|---|---|---|---|
| Schritt | 11,4 / 13,2 ms | 11,6 / 13,8 ms | 12,1 / 12,2 ms |
| davon Bewohner | – | 0,001 / 0,005 ms | 0,001 ms |

Auch frierende Bewohner kosten nichts Messbares. Sie suchen alle 4 Schritte nach Feuerschein und nach Holz und Flamme.
Die Streuung zwischen den Durchgängen kommt vom Rechner, nicht von den Bewohnern. **Auf dem Gerät messen kann ich
nicht.** Den gültigen Wert liefert „Leistung messen“ im Spielplatz.

## Nachweis aus der App

`Phase5cAppTest` öffnet beide Level aus der Levelauswahl und spielt sie in der laufenden App zu Ende. Die Handgriffe
gehen an das Level auf dem Faden der Simulation. Die Bilder hängen am Release:
- `5c_bergleute_start.png`, `5c_bergleute_gerettet.png` (Medaille);
- `5c_winternacht_start.png`, `5c_winternacht_nacht.png` (nach 30 s, Holz neben dem Feuer), `5c_winternacht_morgen.png` (Medaille);
- `5c_winternacht_erfroren.png`: Schnee aufs Feuer, die Nacht ist verloren.

In der App hat das Level schon gut 2 s gelaufen, bevor der Test eingreift. Bei „Bergleute“ ändert das nichts: Der
Gold-Weg gewinnt mit 0 bis 5 s Wartezeit (in der App: Gold, 0:23, 6 Griffe). Bei „Winternacht“ hängt die Nacht davon
ab, wann die Scheite kommen (siehe „Wie verlässlich die Wege sind“). Der Test setzt das Level deshalb vorher auf den
Anfang zurück, so wie „Neu beginnen“, auf den Schritt genau (geprüft: gleicher Weltzustand wie frisch geladen). Dann
spielt er in der App dieselbe Nacht wie der Level-Test.

## Nachtrag: Holzkohle-Korrektur und neuer Herd für „Winternacht“

Nach dem 5c-Bericht kam „mach weiter“. Der Leitfaden V3 hat nach Phase 5 keine weitere Phase; der eigene
Simulationsmodus (7b) soll ausdrücklich jetzt nicht gebaut werden. Offen war nur meine Frage zur Holzkohle. Ich habe
„mach weiter“ als Ja zu dieser Korrektur verstanden.

**Die Korrektur.** Geht glühende Holzkohle aus Mangel an Luft aus, behält sie, was von ihr übrig ist. Fängt sie wieder
Feuer, brennt sie damit weiter und nicht mit ihrer vollen Brenndauer. Die Zufallszahl für die Brenndauer wird dabei
trotzdem gezogen. So läuft der Zufall der Welt gleich weiter, und es ändert sich nur, was die Kohle wirklich anders
macht. Beim ersten Versuch (vor dem 5c-Bericht) fehlte genau das: Die Zufallsfolge lief auseinander, und daran lagen
die Brüche in der Waldvorlage und in Level 4, nicht an der Kohle selbst.

| Messung: Haufen Holzkohle 14 × 10, oben angezündet | ohne Korrektur | mit Korrektur |
|---|---|---|
| nach 3 min | 107 von 140 Zellen übrig, 5 glimmen | aus nach 135 s |
| nach 10 min | 101 übrig, 2 glimmen noch | – |
| kleiner Haufen 6 × 4 | nach 10 min glimmt noch 1 Zelle | aus nach 90 s |

Neuer Test: `BehaviorTest`, „ein Haufen glühender Holzkohle brennt herunter und geht aus“.

**Folgen für Bestehendes:**
- **Waldvorlage:** Der Felsbrocken rollt nach dem Baumbrand weiter in den See; `TemplatesTest` ist unverändert grün.
- **Level 4 „Gewitterfeuer“:** Ob ein später Blitz den Stapel noch entzündet, bleibt Zufall der Weltsaat; der Zufall
  verläuft jetzt etwas anders. Neu gemessen: Ein Pfahl auf dem Stapel bei 90, 120, 130 oder 145 s zündet ihn (bei
  150 s zu spät für eine Medaille); bei 85, 95 bis 115, 125, 135 und 140 s nicht. Der Test „noch später“ nimmt deshalb
  145 statt 125 s. Der Bronzeweg bei 90 s und alle übrigen Wege sind unverändert.
- **Level 6 „Bergleute“:** unverändert, alle Zeiten gleich; dort brennt Teer, keine Holzkohle.
- **Level 1 bis 3 und 5:** unverändert grün.

**„Winternacht“ neu gebaut.** Beim Nachmessen fiel auf, dass das alte Level fast nur in den ersten Sekunden lösbar
war. Das galt schon vor der Korrektur:
- Das kleine Holzfeuer auf dem Herd war nach etwa 9 s fast aus.
- Scheite, die danach neben das Feuer kamen, fingen kein Feuer mehr. Sie versperrten den Bewohnern im Osten den Weg
  zum Herd.
- Gemessen ohne die Korrektur: Wartete man 6, 8 oder 10 s, ging die Nacht verloren, bei 6 und 8 s schon nach 25 s.
  Mit der Korrektur ging zusätzlich die Wartezeit 2 s verloren.
- Auf dem Gerät hätte ein Spieler also nur wenige Sekunden Zeit gehabt.

Ich habe verschiedene Herde gemessen: zwei Lagen Holz, ein Glutbett, ein drittes Scheit, Kohlehaufen in mehreren
Formen, Gruben in mehreren Tiefen und Breiten, einen breiteren Herd. Am besten ist eine **Glutgrube**: oben eine Reihe
Holz, darunter Holzkohle (11 Zellen breit, 4 tief). Die Nacht dauert jetzt 180 statt 200 s. So werden die knappen
Niederlagen (erster Bewohner erfroren nach 183 bis 195 s) zu Siegen, und alle Fallen verlieren weiter mit mindestens
50 s Abstand. Vergleich:

| | altes Level (mit Korrektur) | neues Level |
|---|---|---|
| Scheite neben das Feuer, Wartezeit 0 bis 10 s | 7 von 11 gewonnen, danach immer verloren | 0 bis 40 s: 34 von 38 gewonnen (4 Stellen) |
| Scheite auf den Boden | 7 von 11 (0 bis 10 s), danach meist verloren | 0 bis 32,5 s: 30 von 35 (4 Stellen) |
| Zeit, die ein Spieler hat | etwa 5 s | etwa 35 bis 40 s |

Ein breiterer Herd (damit ein Scheit nicht über die Kante kippt) war deutlich schlechter: nur 3 bis 4 von 7
gewonnen. Der zusätzliche Stein schluckt Wärme. Ich habe ihn wieder verworfen.

Tests: sim 228 und level 89 grün auf dem Rechner. Die App-Tests (`Phase5cAppTest`) spielen Winternacht mit dem neuen
Herd.

## Abweichungen und offene Punkte

- **Keine Wasserfalle in „Bergleute“.** Die Vorgabe nennt als Beispiel „Wasser flutet den Stollen“. Ein Wasserbecken
  hinter einem Damm aus Sand oder Erde bricht in der Simulation nach etwa 75 s von selbst durch. Ein Damm aus
  verankertem Fels ließe sich dagegen nicht öffnen. Der Brunnen im Dorf fasst nur einen Eimer. Die Fallen aus der
  Physik sind deshalb:
  - Das Feuer zündet das Gas.
  - Das nachsickernde Gas brennt als Flamme weiter.
  - Wasser in diese Flamme verdampft.
- **„Warm halten“ als „niemand erfriert“.** Zuerst habe ich das Ziel als Lufttemperatur in den Häusern gebaut
  (`keep_warm` mit `min`, getestet in `PlayTest`). In Häusern mit Herd ließ es sich nicht halten:
  - Offene Luft gleicht sich in etwa 0,5 s der Umgebung an. Ein Haus mit Tür oder Kamin bleibt nur warm, solange ein
    Feuer brennt.
  - Ein dichtes Holzhaus kühlte im Versuch von 18 °C auf −12 °C in 20 s aus, auch mit Schnee rundum.
  - Ein Herdfeuer im Haus setzte die Holzwände in Brand. Ein Ofen ohne Abzug erstickte in seinem Rauch.

  „Niemand erfriert“ misst stattdessen, worum es geht, und lässt die Bewohner handeln.
- **Holzkohle glimmt beliebig lange:** im Nachtrag behoben (siehe dort).
- **Wärme im Fels.** Fels leitet Wärme in dieser Simulation sehr schnell, im Versuch etwa 50 Zellen in 60 s. Das prägt
  Winternacht:
  - Ein Feuer wärmt den Boden des ganzen Platzes, und die Bewohner bleiben nach dem Feuer noch rund eine Minute warm.
  - Der Schnee auf den Dächern taut im Lauf der Nacht teilweise.
  - Ein Hirtenfeuer hinter einer Felsspalte wärmte über den Fels das ganze Dorf. Den Entwurf mit Hirtenfeuer und
    Holzbrücke darüber habe ich deshalb verworfen.
- **Bewohner zünden Brücken an.** Im verworfenen Entwurf zündeten frierende Bewohner die Holzbrücke an, die der
  Spieler über die Spalte gelegt hatte. Sie war das nächste Brennholz bei einer Flamme. Das folgt ihren Regeln, zeigt
  aber, warum ein Seil (Vorschlag in `ELEMENTE.md`) eine eigene Sache wäre.
- **Zu nah am Feuer.** In einer Testszene ging ein Bewohner nach dem Aufwärmen weiter aufs Feuer zu und fiel vor
  Hitze in Ohnmacht. Dabei wurde Rauch dicht vor einem kleinen Feuer in jedem Schritt neu gesetzt. In den Leveln trat
  das nicht auf. Eine Hitze-Ohnmacht lässt ein Ziel nicht scheitern; der Bewohner kommt zurück.
- **Leistung auf dem Gerät:** nicht gemessen (siehe oben).
- **Entdeckungsbuch:** Für 5c ist kein neuer Eintrag nötig. Die Regeln der Bewohner haben ihre Einträge seit 5b.
