# Phase 1b und Phase 2: Bericht

Leitfaden V3 in der aktualisierten Fassung, Abschnitte 3 und 4. Reihenfolge nach Abschnitt 8: 1b, 2a, 2b, 2c, je ein Build. Nach 2c wird gestoppt, bis der Testspieler freigibt.

## Phase 1b: Mängel aus dem Spielplatz

### Zusammenfassung

**3.1 Schwerkraft und Strukturen**

- **Neue Materialart `solid`.** Holz, Stein (gemauert) und Glas sind jetzt feste Materialien. Später kommen Ziegel, Metall und Eis dazu.
- **Struktur:** Feste Zellen, die waagerecht oder senkrecht aneinanderstoßen, bilden eine Struktur. Holz auf Stein gehört also mit dazu.
- **Verankerung:** Eine Struktur, die verankertes Gelände berührt, bleibt stehen. Verankert ist Fels (Feld `anchored`) oder jede Zelle, die ein Level als verankert markiert (Flag `ANCHORED`, für die Masken aus Phase 3).
- **Lose Strukturen fallen als Ganzes**, ohne Drehung und mit zunehmender Geschwindigkeit. Sie fallen durch Luft und Gase und durch Flüssigkeiten, die leichter sind als die Struktur im Mittel.
  - Ein Holzfloß bleibt deshalb auf dem Wasser liegen, ein Steinblock sinkt.
  - Was unter der Struktur lag, rückt nach oben.
- **Verbindungsprüfung nur bei Änderungen:** Sie läuft, wenn feste Zellen entstehen, verschwinden, sich umwandeln oder bewegen, also bei Brand, Schmelzen, Abbau oder Bauen. Nicht in jedem Schritt.
  - Lose Strukturen prüfen in jedem Schritt nur, ob unter ihnen Platz ist. So fällt ein Brett, wenn die Erde darunter weggespült wird.
- **Während des Falls hält sich nichts fest:** Ein fallendes Brett bleibt nicht an einer Felswand hängen, an der es vorbeirutscht. Erst wenn es liegt, verankert es sich an berührtem Fels.
- **Gewollte Folge:** Brennt der Pfosten einer Brücke durch, stürzt sie ein (Test und Bilder `bridge_burning.png`, `bridge_fallen.png`).
- **Geröll:** Stein gibt es zusätzlich als **Geröll**, ein schweres Pulver. Gemauerter Stein ist fest, Lava erstarrt zu festem Stein.
- **Fels ist reines Gelände:**
  - Er steht nicht mehr in der Leiste.
  - Der Radierer kann ihn nicht entfernen.
  - „Alles leeren“ lässt ihn stehen.

**3.2 Feuer**

- **Sichtbarer Flammenkörper:**
  - Holz gibt viel mehr, dafür kurzlebige Flammen ab.
  - Jede Flamme bekommt ihre Farbe aus ihrer Umgebung: dicht und jung gelb, dann orange, am Rand rot. Dazu flackert sie.
  - Eine neue Flammenschicht in halber Auflösung legt dichte Flammen weich zu einem Körper zusammen (Bilder `wood_burning.png`, `sandbox_fire.png`).
- **Feuer klettert nach oben schneller als zur Seite:** Aus einem heißeren Gas darunter (Flammen, heiße Luft, Rauch) geht Wärme dreimal so gut nach oben wie durch bloße Leitung. Das ist Konvektion als allgemeine Regel.
- **Holz brennt durch:**
  - Außen brennen Flammen, darunter glüht die brennende Schicht als Glut, dunkel mit flackerndem Rot.
  - Heißes Holz ohne Luft verkohlt jetzt langsamer und erst deutlich über dem Zündpunkt, damit die Flammenfront ins Holz wandert.
  - Gelöscht bleibt Holzkohle, abgebrannt etwas Asche.
- **Rauch kommt aus den verlöschenden Flammen** (30 %) statt direkt aus dem Holz. So deckt er die Glut nicht zu und erstickt das Feuer im Freien nicht. Im geschlossenen Raum füllt er den Raum weiter und erstickt das Feuer (Test bleibt grün).
- **Feuer braucht Luft:** unverändert.

**3.3 Wasser**

- **Ruhiges Wasser liegt still.** Eine Flüssigkeit fließt seitlich nur noch zur nächsten Stelle, an der sie tiefer sinken kann. Gibt es keine solche Stelle, ist sie eben und bleibt liegen, und ihr Chunk schläft ein.
  - An der Oberfläche sucht sie über anderes Wasser hinweg. So gleitet eine Welle zur tiefen Stelle, statt hinter einem anderen Tropfen hängenzubleiben.
- **Schneller Pegelausgleich:**
  - Wasser sucht bis 40 Zellen weit und fließt bis 8 Zellen pro Schritt.
  - Ein Block von 364 Zellen ist in 2 Sekunden über 60 Spalten eben; vorher war die Oberfläche nach 8 Sekunden noch gestuft.
  - Der Test verlangt jetzt höchstens eine Zelle Unterschied.
- **Darstellung:**
  - Farbrauschen fast weg (Farbvarianz 0,025 statt 0,06).
  - Mit der Tiefe stetig dunkler.
  - Helle Oberflächenkante mit leichter, wandernder Bewegung.
- **Keine schwarzen Löcher:**
  - Fallende Flüssigkeit fällt höchstens zwei Zellen pro Schritt und reißt dadurch nicht mehr in Tropfen auf.
  - Eine einzelne Luftlücke zwischen Wasser darüber und darunter wird wie Wasser gezeichnet, ebenso in Flammen.
- **Aufsaugen auch bei ruhendem Wasser:** Liegt Wasser still auf trockener Erde, bleibt sein Chunk wach, bis die Erde es aufgesogen hat.

**3.4 Bugfix Leiste**

- **Vermutete Ursache:** der Dehn-Effekt beim Überscrollen von Android 12 und neuer. Er staucht den Inhalt der Leiste beim Scrollen über das Ende hinaus.
- **Behebung:**
  - Der Effekt ist für die Leiste abgeschaltet.
  - Jede Karte hat eine feste Breite und schneidet ihren Inhalt ab.
  - Beschriftungen brechen nie um und kürzen mit „…“.
- **Nachstellen konnte ich den Fehler nicht.** Neuer Test: Leiste mit 130 % Systemschrift, halb gescrollt (`09_leiste_grosse_schrift.png`). **Bitte auf dem Gerät prüfen.**

### Abweichungen mit Begründung

1. **Struktur heißt 4-Nachbarschaft.** Feste Zellen, die sich nur über Eck berühren, gehören nicht zusammen. Damit ist eindeutig, was zusammenhält.
2. **Fallen ohne Drehung, wie im Leitfaden.** Ein Brett, das nur an einem Ende aufliegt, bleibt waagerecht liegen. Kippen kommt mit der Körperphysik in Phase 4.
3. **Brennende Strukturen:** Die Prüfung läuft in jedem Schritt, in dem eine Holzzelle verbrennt, also solange ein Feuer an einer Struktur frisst. Das ist eine Änderung fester Partikel und deshalb gewollt. Die Kosten stehen unten bei den Messwerten.

### Leistung

Gemessen nach Phase 1b auf diesem Desktop (Linux, Java 21, 4 Kerne), je 600 Schritte:

| Welt | Phase 1 | Phase 1b |
|---|---|---|
| Voll aktive Welt (alle 77 Chunks wach) | 4,5 ms (221 Schritte/s) | 4,1 bis 4,6 ms (216 bis 246 Schritte/s) |
| Spielplatz mit Feuer, Sand und Wasser | 0,89 ms | 0,9 bis 1,0 ms |
| Bild erzeugen (Pixel) | 0,38 ms | 0,98 ms |

Das Bild kostet mehr: Wassertiefe, Lückenfüllung und Flammenfarben brauchen Nachbarzellen. Dazu kommt die neue Flammenschicht. Auf dem Handy waren es in Phase 1 0,24 ms für das Bild und 2,0 ms für die voll aktive Welt; mit dem neuen Build bitte erneut **„Leistung messen“**.
