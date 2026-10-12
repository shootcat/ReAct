# Elemente: Vorschläge, die noch nicht umgesetzt sind

Hier stehen Materialien und Regeln, die erwogen, aber bewusst (noch) nicht gebaut wurden, jeweils mit
Begründung. Grundlage ist der Leitfaden V3 (Abschnitt 2: Eigenschaften statt Rezepte).

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
