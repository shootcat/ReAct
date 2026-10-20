# Elemente: Vorschläge, die noch nicht umgesetzt sind

Hier stehen Materialien und Regeln, die erwogen, aber bewusst (noch) nicht gebaut wurden, jeweils mit
Begründung: Schwarzpulver (2d), was der Körperphysik aus Phase 4 noch fehlt, und was die Bewohner (Phase 5c)
brauchen könnten. Grundlage ist der Leitfaden V3
(Abschnitt 2: Eigenschaften statt Rezepte).

## Schwarzpulver (Leitfaden V3, 4a.4.6)

**Stand: nicht umgesetzt, nur Vorschlag.**

Der Leitfaden erlaubt Schwarzpulver aus Holzkohle, Schwefel und Salpeter nur, wenn sich eine saubere,
verständliche Mischregel finden lässt. Das ist mit dem heutigen Regelwerk nicht der Fall:

- **Es wäre ein Rezept.** Alle Umwandlungen der Simulation folgen allgemeinen Regeln (Verbrennen, Schmelzen,
  Verdampfen, Lösen, Ausspülen, Aufsaugen, Wärmeleitung, Abschrecken, Verdichten). „Drei Pulver nebeneinander
  ergeben ein viertes“ ist dagegen eine Regel für genau ein Materialtripel – das, was Grundsatz 1 verbietet.
- **Zwei der drei Zutaten fehlen.** Schwefel und Salpeter gibt es in der Welt nicht, und sie hätten außer
  dieser einen Mischung keine Rolle. Zwei Materialien nur für ein Rezept blähen die Leiste auf.
- **Die Spielidee ist schon da.** Explosionen gibt es über Grubengas (Gasspalten in Minen) und über
  Dampfexplosionen (Wasser in einem geschlossenen Raum mit Lava). Beides folgt aus Eigenschaften, die das
  Entdeckungsbuch zeigt.

**Wie es sauber ginge, falls gewünscht:**

1. *Salpeter als Eigenschaft statt als Zutat.* Ein Material „Salpeter“ (weißes Pulver, kommt an Höhlenwänden
   vor) mit der Eigenschaft `oxidizer`: Brennbares, das ihn berührt, braucht keine Luft zum Brennen und brennt
   mit vielfacher Geschwindigkeit. Dann ist ein Gemenge aus Holzkohle und Salpeter von selbst ein
   „Schnellfeuer“ – auch in einem geschlossenen Raum, wo es Druck aufbaut und nach den bestehenden
   Druckregeln (4a.4) eine Wand sprengt. Schwefel bräuchte es dafür nicht.
2. *Kein neues Mischmaterial.* Das Gemenge bleibt zwei Pulver nebeneinander; es wird nirgends zu
   „Schwarzpulver“ umgewandelt. Das Buch zeigt nur: „Salpeter lässt Brennbares ohne Luft brennen“.
3. *Kosten.* Eine weitere Eigenschaft im Brennvorgang (Sauerstoffquelle prüfen), keine neue Schleife.

Damit wäre es eine Eigenschaft wie alle anderen. Umsetzung erst nach Rückfrage, weil es ein neues
Rohmaterial in die Level bringt.

## Grubengas und Gasspalte (umgesetzt in 2d)

Zur Einordnung: Grubengas (`firedamp`) ist leichter als Luft, sammelt sich unter Decken, ist kaum zu sehen
(die Luft darüber flimmert leicht) und zündet an Feuer, Glut oder ab 580 °C als Ganzes. Die Stärke der
Explosion folgt der Größe der Gaswolke. Die Gasspalte (`gas_vent`) ist Levelgelände wie Fels und gibt in
Minenleveln ab und zu Grubengas ab.

## Erdöl (umgesetzt in 4b)

Zur Einordnung: Erdöl (`oil`) folgt nur aus Eigenschaften, keine Rezepte. Es ist zäh (warm dünner), leichter
als Wasser, brennt erst bei großer Hitze oder wenn etwas Brennendes es eine Weile berührt, löscht sich nicht mit
Wasser und erstickt unter Sand, Erde oder einer Abdeckung. Es sickert in Sand und Erde (`oily_sand`,
`oily_earth`, brennbar), vergiftet Pflanzen und Fische, gast erhitzt in eingeschlossene Luft aus (`oil_gas`,
explosiv wie Grubengas) und verdunstet zu Teer (`tar`, klebt, brennt gut). Verbrannt hinterlässt es Ruß
(`soot`) und dicken schwarzen Qualm (`black_smoke`). In Leveln tritt es aus einer Ölquelle im Gestein
(`oil_spring`) oder liegt in Taschen unter Fels; beides kann ein Level über seine Farbe in der Bodenkarte setzen.

**Noch offen, Vorschlag:** Keiner der fünf bestehenden Level enthält bisher Öl. Ein eigener Level mit einer
Ölquelle (Idee: „Ölpest“, ein Ölfilm treibt auf einen Fischteich zu und muss mit Sand und Erde aufgehalten oder
abgefackelt werden, ohne dass das Feuer auf das Ufergras übergreift) wäre der nächste Schritt; er braucht wie
jeder Level mindestens drei Lösungswege mit Tests.

## Körperphysik: was in Phase 4 bewusst fehlt

**Stand: nicht umgesetzt, nur Vorschläge.** Phase 4 („schlanke Fassung“) macht lose Strukturen zu starren
Körpern (`Bodies.kt`, Bericht in [`PHASE4.md`](PHASE4.md)). Die folgenden Punkte waren ausdrücklich nicht Teil
der Phase. Hier steht, wie sie sich auf den bestehenden Löser setzen ließen.

### Brücken, die sich biegen und unter Last einstürzen

- *Heute:* Eine Brücke, die Fels berührt, ist verankert und trägt jede Last. Ein Körper darauf ist nur Last,
  er biegt nichts.
- *Vorschlag:* Jede verankerte Struktur bekommt eine Tragfähigkeit aus der Festigkeit (`strength`) ihrer Zellen
  und der Spannweite zwischen zwei Auflagen. Liegt mehr Gewicht darauf (Summe der ruhenden Körper und des Pulvers
  darüber), bricht sie an ihrer dünnsten Stelle. Dafür gibt es schon das Zerbrechen aus Phase 4 (Kern und Risse).
  Die Teile werden Körper und fallen. Biegen wäre nur eine Darstellung: Die Zellen sinken um eine Zelle in der
  Mitte, bevor es bricht.
- *Kosten:* eine Summe über die ruhenden Körper je Struktur, nur wenn sich etwas ändert.

### Gelenke, Seile, Hebel

- *Heute:* Körper hängen nicht aneinander; was sich berührt und ruht, wird ein Körper.
- *Vorschlag:*
  - Ein Gelenk ist ein Punkt, den zwei Körper (oder ein Körper und der Fels) teilen. Im Impulslöser ist das eine
    weitere Bedingung neben den Kontakten: gleiche Geschwindigkeit an diesem Punkt.
  - Ein Hebel ist ein Balken mit einem Gelenk am Fels.
  - Ein Seil ist eine Kette kurzer Glieder mit Gelenken, gezeichnet als Zellen eines neuen Materials („Seil“,
    brennbar).
- *Kosten:* eine Bedingung mehr je Gelenk in jeder der acht Runden des Lösers. Mehr Sorgfalt braucht das
  Rastern, damit Glieder sich nicht gegenseitig verdrängen.
- *Spielidee:* Ein Seil durchbrennen lassen, und die Last fällt; mit einem Hebel einen Felsbrocken anheben.

### Wasserströmung, die Körper mitträgt

- *Heute:* Ein Körper im Wasser bekommt Auftrieb und Widerstand, aber das fließende Wasser schiebt ihn nicht.
  Ein Floß auf einem Fluss bleibt liegen.
- *Vorschlag:* Der Widerstand wirkt gegen die Geschwindigkeit des Körpers *relativ zum Wasser*. Die Strömung
  ließe sich je Zeile aus der Bewegung der Wasserzellen im letzten Schritt schätzen (wie viele Zellen sich nach
  links oder rechts bewegt haben). Dann treibt ein Floß mit, und ein Stamm verkeilt sich an einer Engstelle.
- *Kosten:* ein Zähler je Wasserzelle, die sich bewegt, gesammelt nur entlang der Körper.

### Pfosten stapeln

- *Heute:* Zwei Pfosten von einer Zelle Breite stehen nicht übereinander, und ein Pfosten steht nicht auf einer
  einzelnen Zelle. Das ist physikalisch nicht falsch (labiles Gleichgewicht), auf dem Raster aber strenger als in
  Wirklichkeit, weil die Auflage nie genau mittig ist. Level 4 hat deshalb eine Steckfuge im Holzstoß und ein
  Pfostenloch im Fels.
- *Vorschlag:* Ein schmaler Körper, der senkrecht auf einer Fläche gleicher Breite ruht, wird mit ihr verbunden
  (wie beim Verschmelzen ruhender Körper in Phase 4), solange nichts ihn anstößt. Er kippt erst bei einem Stoß
  oder wenn sein Schwerpunkt deutlich neben der Auflage liegt.

## Bewohner: Kleidung, Baumwolle, Nahrung, Seil, Fackel (Phase 5c)

**Stand: nicht umgesetzt, nur Vorschläge.** Die beiden Testlevel aus 5c kommen ohne neue Materialien aus:
„Bergleute“ mit Teer, Grubengas, Geröll, Wasser und Sand, „Winternacht“ mit Holz, Holzkohle, Pech und Schnee.
Alle Vorschläge folgen Grundsatz 1 (Eigenschaften statt Rezepte). Jeder würde ein neues Rohmaterial in die Level
bringen, deshalb erst nach Rückfrage.

### Kleidung

- *Heute:* Alle Bewohner frieren gleich schnell: bei −10 °C und kälter nach 5 s, Ohnmacht nach 20 s.
- *Vorschlag:* Kleidung ist keine Zelle der Welt, sondern eine Eigenschaft der Figur. Das Level gibt sie mit
  (`"clothing": "warm"`). Warm gekleidet steigt die Kälte halb so schnell, nass gewordene Kleidung (Regen, Wasser
  bis zur Hüfte) wärmt nicht mehr, bis sie am Feuer trocknet. Das passt zu den Eigenschaften, die es schon gibt
  (`wets` und das Trocknen durch Wärme).
- *Kosten:* ein Wert je Figur mehr, gespeichert wie die Kälte; keine neue Schleife.
- *Spielidee:* In „Winternacht“ wären die Hirten warm gekleidet und könnten Holz holen, die Kinder nicht.

### Baumwolle

- *Vorschlag:* eine Pflanze wie das Gras (wächst auf Erde im Licht), die Samenkapseln trägt. Die Kapseln sind ein
  loses Material „Baumwolle“ mit diesen Eigenschaften:
  - leitet Wärme kaum (wie Schnee, 0,005), schmilzt aber nicht: ein Dämmstoff, der im Warmen bleibt;
  - saugt Wasser auf (`absorbs`) und dämmt nass nicht mehr;
  - brennt schnell und heiß, aber kurz (wie trockenes Gras).
- *Kosten:* ein Material und eine Wuchsregel, die es für Gras schon gibt.
- *Spielidee:* ein Haus dämmen, das sonst in der Nacht auskühlt; ein Docht, der eine Flamme von einem Feuer zum
  nächsten trägt.

### Nahrung

- *Heute:* Die Bewohner haben drei Bedürfnisse: Wärme, Wasser und Luft (5b).
- *Vorschlag:* Hunger als viertes Bedürfnis, langsamer als Durst (etwa 10 Minuten bis zur Ohnmacht). Essbar ist
  eine Eigenschaft (`edible`) von Materialien, die es schon gibt: toter Fisch, Samen, Früchte eines Busches. Ein
  hungriger Bewohner isst, was essbar ist und vor seinen Füßen liegt (wie beim Trinken), nie Verdorbenes:
  `decay_product` gibt es schon, verdorbene Nahrung wäre ein eigenes, nicht essbares Material.
- *Kosten:* ein Zähler je Figur (wie der Durst) und ein Blick auf die Zellen vor den Füßen alle 4 Schritte.
- *Spielidee:* ein Dorf über den Winter bringen: Fische aus dem Eis holen, Samen trocken lagern.

### Seil aus Pflanzenfasern

- *Vorschlag:* ein Material „Seil“ aus Ranken (die es schon gibt): eine abgestorbene Ranke lässt sich mit der Hand
  als Seil aufnehmen. Ein Seil ist eine Kette kurzer Glieder mit Gelenken (siehe „Gelenke, Seile, Hebel“ oben). Es
  brennt und reißt, wenn die Last zu groß wird.
- *Kosten:* die Gelenke im Körperlöser; das ist der eigentliche Aufwand.
- *Spielidee:* eine Hängebrücke über eine Kluft, über die Bewohner gehen. Ein Versuch in „Winternacht“ mit einer
  Brücke aus Scheiten zeigte, warum ein Seil fehlt: Frierende Bewohner zünden Holz in ihrer Nähe an, eine
  Holzbrücke verbrennt also. Ein Seil wäre ebenso brennbar, hinge aber hoch genug über dem Boden.

### Fackel

- *Heute:* Feuer lässt sich nicht tragen. Der Eimer nimmt nichts, was brennt, und die Hand nichts, was heiß ist.
  Die Bewohner zünden nur an einer Flamme an, die schon da ist (5b).
- *Vorschlag:* ein Holzstab, an einem Ende mit Pech getränkt („Fackel“, ein Körper aus Holz und Pech). Die Hand
  darf ihn halten, wenn nur das Pechende brennt. Er brennt etwa eine Minute und zündet an, was er berührt.
- *Kosten:* eine Ausnahme in der Regel „zu heiß zum Anfassen“ für Körper, deren heiße Zellen alle am Ende liegen.
- *Spielidee:* Feuer von einem Ort zum anderen bringen, etwa vom Hirtenfeuer ins Dorf.

