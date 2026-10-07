# Abschlussbericht Leitfaden V2

Stand: alle sechs Schritte aus Abschnitt 6 des Leitfadens erledigt, jeder mit eigenem Commit auf `claude/charming-goodall-6m55iu`. Die CI (Engine-Tests, App-Test, APK) läuft bei jedem Commit.

## Was gebaut wurde

1. **Teil A, Restpunkte:** keine Tropfen-Icons beim Ablegen und in Schächten; getragenes Wasser ist eine schwappende Wasserform statt einer Kiste.
2. **Aufräumen:** Frost samt Grafik, Ton und Regeln entfernt; p_01 bis p_10 und ihre Walkthroughs gelöscht; Welt „Test“ mit t_01 bis t_03; alter Fortschritt wird verworfen.
3. **Engine-Erweiterungen 2.4**, je ein Commit mit Tests:
   - `merge` mit `"keep"` (Stein fällt Baum, bleibt liegen);
   - Löschen ergibt Holzkohle;
   - `source_flame`;
   - Wärmeleitung durch Metall, auch durch eine Wand;
   - Brenndauer in Zügen;
   - Salzwasser verdampft zu normalem Dampf;
   - neue Ziele `produce`, `heat` und `fill` mit `"full"`.
4. **Neue Elemente** mit Grafik, Animation und Ton: Holzkohle, Glut, Erz, Schmelzgut, Metall, Glas, Schlamm, Asche, Erde, dazu die Hütte und der Landschafts-Look „Mine“.
5. **Drei Level**, je ein Commit mit Walkthrough, Fallen-Tests, LEVELDESIGN-Abschnitt und Startbild: t_01 „Kochstelle“, t_02 „Süßwasser“, t_03 „Tiefe Schmelze“.
6. **Dieser Bericht.**

## DifficultyReport

Erzeugt von `DifficultyReportTest` (`engine/build/difficulty.md`):

| Level | Mindestzüge | Beweis | Lösungen | Sackgassen | Naheliegender Zug | Köder | Felder | Zufallsspiele gelöst |
|---|---|---|---|---|---|---|---|---|
| t_01 | 4 | vollständige Suche | 18 | 26 % (5/19) | führt nicht zur Lösung | – | 8 (2.0 pro Zug) | – |
| t_02 | 5 | vollständige Suche | 21 | 19 % (7/36) | führt nicht zur Lösung | earth_8_5 | 10 (2.0 pro Zug) | – |
| t_03 | 9 | keine Lösung bis 4 Züge (vollständig), Walkthrough mit 9 | – | – | – | – | 14 (1.6 pro Zug) | 0 von 3000 (0.0 %) |

„Lösungen“ zählt Reihenfolge- und Ortsvarianten einzeln. Bei t_01 und t_02 sind es alles Spielarten derselben Kette.

Für t_03 sind Lösungszahl, Sackgassen und naheliegender Zug nicht berechnet: Dafür müsste bis 9 bzw. 11 Züge vollständig gesucht werden.

## Was bewiesen ist und was nur stichprobenartig geprüft wurde

**t_01 und t_02**
- *Bewiesen:* Das Minimum (vollständige Breitensuche bis zum Minimum) und die Sackgassen-Anteile (vollständige Suche bis Minimum plus zwei).
- *Bewiesen:* Jede Falle aus dem Leitfaden ist eine Sackgasse. Nach der Falle findet eine vollständige Suche über 6 weitere Züge keine Lösung; ein brennender Wald oder eine brennende Hütte beendet die Suche sofort.

**t_03**
- *Bewiesen:* Keine Lösung mit bis zu 4 Zügen. Das prüft der Test in der CI.
- *Bewiesen:* Nach den ersten 4 Zügen der Musterlösung gibt es keinen Rest in 4 Zügen, nach den ersten 5 keinen in 3.
- *Begründet, nicht durch Suche bewiesen:* das Minimum 9. Eine Zählbegründung in `docs/LEVELDESIGN.md` zeigt, dass jede Lösung 9 verschiedene Züge braucht:
  - 1 Fällen;
  - 2 Hölzer bewegen;
  - 2 Löschzüge;
  - 2 Verschmelzungen;
  - 2 Platzierungen am Schlitz.
- *Stichprobe:* 3000 Zufallsspiele mit je 12 erlaubten Zügen (Minimum plus drei). Keines löst das Level.
- *Fallen:* Jede Falle ist über ihre Materialbilanz gezeigt (was verbraucht ist, kommt nicht zurück) und durch eine vollständige Suche über die nächsten 3 Züge. Eine vollständige Suche bis zum Ende ist nicht machbar.

## Ehrlichkeitsprüfung

**t_01 „Kochstelle“**
- *Kürzer?* Nein, vollständig bewiesen.
- *Offensichtlicher?* Nein. Feuer und brennendes Holz bringen Wasser nicht zum Kochen. Das Becken ist nur über ein Feld am Waldrand erreichbar, und jede Flamme dort steckt den Wald an.
- *Beim Bau gefunden und beseitigt:* Brennendes Holz ließ sich im Becken selbst löschen, dann wäre die Pfütze überflüssig gewesen (3 Züge). Abhilfe: nur ein Feld am Becken, und das liegt am Wald.

**t_02 „Süßwasser“**
- *Kürzer?* Nein, vollständig bewiesen.
- *Offensichtlicher?* Nein. Süßwasser gibt es im Level nicht. Löschdampf reicht nicht für eine Wolke; nur dauerhaftes Kochen des Meeres füllt das Becken, und dauerhaft heiß ohne Flamme ist nur Glut.
- *Beim Bau gefunden und beseitigt:*
  - Ein zweites Feld über dem Meer hätte das Löschen ohne Pfütze erlaubt.
  - Ein Feld direkt über dem Becken machte die Salz-Falle umkehrbar: Holz im Becken anzünden und mit genau dem hineingekippten Salzwasser löschen. Das Feld liegt jetzt auf der Klippenkante daneben.

**t_03 „Tiefe Schmelze“**
- *Kürzer?* Nach der Zählbegründung nein, durch Suche bewiesen bis 4 Züge.
- *Offensichtlicher?* Nein. Glut oder Feuer in den Schlitz schmelzen nichts, denn nur Metall gibt Hitze durch die Wand. Zuerst Glut zu machen verbraucht die einzige Flamme zu früh.
- *Beim Bau gefunden und beseitigt:*
  - Eine Pfütze löschte zwei Hölzer zugleich. Behoben in der Engine: Eine Quelle wirkt nur einmal.
  - Gesalzener Schnee hinterließ noch brauchbares Wasser. Abhilfe: formfester Schneeball an der Schachtkante.
  - Heißes Metall ließ sich neben der Glut erzeugen und in den Schlitz tragen (8 statt 9 Züge). Abhilfe: Glühendes Metall kann man nicht anfassen.

## Abweichungen vom Leitfaden, mit Begründung

**Elemente**

1. **Hütte (HUT) ist ein zusätzliches Element.** Level 2 verlangt „Die Hütte darf nicht brennen“, die Elementliste in 2.1 nennt sie nicht. Sie verhält sich wie ein Baum: Sie fängt an einer Flamme Feuer und brennt 3 Züge. Gelöscht bleibt sie verkohlt; sie liefert keine Holzkohle, sonst wäre sie ein zweiter Holzvorrat.
2. **Schneeball ist ein eigener Zustand von Schnee (BALL).** Pulverschnee rieselt wie Sand von Kanten. Der Schneeball bleibt liegen, schmilzt aber gleich. Das braucht Level 3, damit „Salz auf den Schnee“ wirklich verschwendet: Das Schmelzwasser läuft in einen Schacht.
3. **Heißes Metall lässt sich nicht anfassen** (neue Eigenschaft `untouchable_state`). Der Leitfaden sagt dazu nichts. Ohne die Regel hätte Level 3 eine kürzere Lösung, die „Glut neben den Schlitz“ umgeht. Physikalisch ist sie naheliegend.
4. **Holzstapel samt Rezept entfernt.** Laut 2. kommt er später. In Level 3 mit zwei Hölzern wäre er ein ungewollter Weg.
5. **Ältere Elemente bleiben im Katalog:** großes Feuer, Lava, Bimsstein, Samen und Eis. Gestrichen werden sollte nur Frost. Keines davon ist in den Testleveln erreichbar; zwei Flammen gibt es nirgends.
6. **Glut erlischt nie.** Wasser neben Glut kocht (Tabelle 2.2); eine Regel zum Löschen von Glut gibt der Leitfaden nicht vor.

**Engine**

7. **Brenndauer:** gezählt am Ende jedes Zuges; der Zug des Entzündens zählt mit. Holz (2) ist im Zug danach noch löschbar, Baum und Hütte (3) in den zwei Zügen danach. Die restlichen Züge zeigen kleiner werdende Flammen und Glutpunkte. Das ist die Lesart von 2.3 („nach dem Zug, in dem es entzündet wurde, ist es noch löschbar“).
8. **Wärme durch die Wand nur aus einem Schlitz:** Metall muss auf zwei gegenüberliegenden Seiten Wand haben, und es geht genau eine Wandzelle weit. Sonst würde Metall, das auf dem Boden liegt, durch den Boden heizen.
9. **Heißes Metall gibt immer Hitze 6 ab,** egal ob ein Feuer oder Glut es erhitzt hat. Das ist eine Vereinfachung.
10. **Sand schmilzt erst ab Hitze 6:** Glut, heißes Metall oder Lava ja, ein Feuer nicht. Das passt zu „Sand neben Glut oder heißem Metall“. Schmelzgut wird, wie in der Tabelle, nur neben Glut zu Metall.
11. **Eine verbrauchte Quelle wirkt nur einmal.** Löschen braucht 2 Einheiten in einer Zelle. Vorher löschte eine Pfütze zwischen zwei Hölzern beide. Das ist eine Korrektur, ohne die „Material reicht für genau einen Weg“ nicht hält.
12. **Ziele:**
    - `heat` ist ein Ereignis: Einmal gekocht, bleibt es erfüllt. Dampf vom Löschen zählt nicht.
    - Bei `fill` verdirbt jede fremde Flüssigkeit im Bereich das Ziel, nicht nur Salzwasser. `"full"` berechnet das Minimum aus den offenen Zellen.
    - `preserve` gibt es auch für alle Dinge eines Typs in einem Bereich (der Wald); bewahrt heißt gleicher Typ und gleicher Zustand.

**Level**

13. **Karten im Hochformat** (bestehender Test: Höhe ≥ Breite) und kompakt.
14. **t_01:** Das einzige Feld am Becken ist die Falle für das Feuer und zugleich der Platz für die Glut. Das lässt sich nicht vermeiden, weil die Glut ans Becken muss. Regel 6 („kein Feld direkt am Hauptziel außer als Falle“) gilt also mit dieser Doppelrolle.
15. **t_02:** Das Holz liegt anfangs auf dem Hüttendach. So ist „Holz weit weg von der Hütte anzünden“ ein echter Schritt, und die Lösung hat 5 Züge.
16. **t_03:**
    - „Glut neben den Schlitz“ heißt hier: Glut auf das Schmelzgut im Schlitz. Das ist die einzige Zelle, die den Schlitz von außen berührt.
    - 14 Ablagefelder (1,6 pro Zug).
17. **Musik:** Die Mine bekommt keine eigene Melodie (Musik bleibt laut Leitfaden, wie sie ist), sondern die Vulkan-Melodie.
18. **Welt „Test“:** Alle Reaktionen sind von Anfang an bekannt, das Entdeckungsbuch ist vollständig. Das setzt die Grundannahme aus 1.2 um.

**Prüfung**

19. **Level 3, Suchtiefe:** Statt vollständig bis Tiefe 6 wird bis Tiefe 4 gesucht (siehe oben). Die Zahl der Welten wächst pro Zug um den Faktor 15 bis 20. Bei 6 Zügen wären es rund 200 Millionen, mit vielen Stunden Rechenzeit und weit mehr Speicher, als die Testmaschine hat; ein Versuch mit Tiefe 5 brach am Speicher ab. Ersatz:
    - die Zählbegründung;
    - die gezielten Suchen ab Zwischenständen;
    - die Zufallsspiele.
20. **Fallen-Tests:** in Level 1 und 2 eine vollständige Suche über 6 weitere Züge, in Level 3 Materialbilanz plus 3 Züge. Die Fallen von Level 1 und 2 sind damit nicht „unendlich weit“ bewiesen. 6 weitere Züge reichen aber für jede vollständige Lösung (Minimum 4 bzw. 5).

## Offene Punkte

- **Rechenzeit:** Der DifficultyReport für Level 3 braucht in der CI einige Minuten (vollständige Suche bis 4 Züge und 3000 Zufallsspiele).
- **Nicht im Gerät durchgespielt:** Die drei Level sind in der Simulation geprüft, nicht auf einem Gerät mit Spielern. Die Startbilder liegen in `docs/screenshots/test/`.
