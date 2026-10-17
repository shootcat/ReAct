# Elemente: Vorschläge, die noch nicht umgesetzt sind

Hier stehen Materialien und Regeln, die erwogen, aber bewusst (noch) nicht gebaut wurden, jeweils mit
Begründung: Schwarzpulver (2d) und was der Körperphysik aus Phase 4 noch fehlt. Grundlage ist der Leitfaden V3
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
