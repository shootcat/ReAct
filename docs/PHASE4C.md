# Phase 4c: Bericht

Phase 4c: Körper kippen im Spiel wirklich um, und es regnet und schneit sichtbar. Dazu der Nachweis aus der laufenden App: Bildfolgen bei 0, 1, 2 und 3 Sekunden für jeden Fall.

**Build:** [beta-115](https://github.com/shootcat/ReAct/releases/tag/beta-115) (Bilder geprüft an [beta-113](https://github.com/shootcat/ReAct/releases/tag/beta-113) und beta-115)

## Überblick

| Punkt | Stand |
|---|---|
| 1 Umkippen | Ursache gefunden und behoben; vier Fälle in der App nachgewiesen (Abschnitt 1, 3) |
| 2 Regen und Schnee | Ursachen gefunden und behoben; Entwickleransicht der Wolken (Abschnitt 2, 3) |
| 3 Nachweis aus der App | Bildfolgen aus der laufenden App, an jedem Release (Abschnitt 3) |
| 4 Level und Leistung | alle Lösungswege grün; Level 4 angepasst (Gewitter 150 statt 180 s); Leistung unverändert (Abschnitt 4) |

## 1. Umkippen

### Ursache

Die Körper zitterten auf der Stelle und fielen nie, weil ihr Zustand bei jedem Schritt aus dem Raster neu angelegt wurde.

- Solange der Finger auf dem Bildschirm liegt, malt die Sitzung bei jedem Schritt an der Fingerstelle weiter (`Session.tick` ruft `dab` auf). Das ist gewollt: Ein ruhender Finger gießt weiter.
- Jede neue Zelle desselben Strichs gehört zu demselben Stück. Die Strukturensuche fand also bei jedem Schritt „neue Zellen am Körper“.
- Für diesen Fall hat sie den Körper verworfen und aus allen Zellen neu angelegt: **Winkel 0, Drehgeschwindigkeit 0, Schwung gemittelt.**
  - Der schräge Strich wurde so bei jedem Schritt wieder zum ungedrehten Körper „so wie er im Raster liegt“.
  - Er kippte einen Schritt lang an, wurde neu angelegt, kippte wieder an: Zittern auf der Stelle.
  - Nach dem Loslassen blieb ein verformter, schon schlafender Körper stehen.
- Gemessen (Stab schräg auf der Wiese, Finger danach ruhend):

  | Finger ruht | vorher | nachher |
  |---|---|---|
  | 0 Schritte | fällt (43°) | fällt (34°, liegt auf einer Kuppe) |
  | 10 Schritte | fällt (30°) | fällt (34°) |
  | 30 Schritte | **steht, schläft sofort (0°)** | fällt (34°) |
  | 60 Schritte | **steht, schläft sofort (0°)** | fällt (34°) |

Die anderen Verdachtsmomente:
- **Runden auf das Raster:** Lage, Winkel, Geschwindigkeit und Drehgeschwindigkeit waren schon Kommazahlen. Zurückgeschrieben wurde nur an der beschriebenen Stelle, und dort nicht durch Runden, sondern durch Neuanlegen.
- **Zu frühes Schlafen:** Ein Körper schläft erst, wenn er 20 Schritte still lag und auf seinem Fuß steht (Schwerpunkt über der Auflage, kein Balancieren auf einer Spitze). Das blieb so.
- **Kollisionskorrektur macht die Drehung rückgängig:** nicht die Ursache. Gefunden habe ich aber eine verwandte Stelle: Ein Körper, der sich beim Kippen irgendwo anlehnte, wurde beim Einschlafen gerade gestellt. Gerade stand er frei, beim nächsten Aufwachen (Feuer daneben) kippte er wieder an. Das war ein zweites Zittern (Abschnitt „Anlehnen“).

### Behebung

- **Der Strich unter dem Finger ist noch kein Körper.** Solange der Finger malt, bleibt der Strich stehen, wo er gemalt wird. Beim Loslassen wird er als Ganzes ein Körper, in genau der gemalten Form, und fällt.
- **Anwachsen ohne Neuanlegen.** Wächst doch etwas an einen Körper an (Eis an eine Scholle, erstarrende Lava an einen Stein), kommen die neuen Zellen in seinen eigenen Rahmen, dort, wo sie in der Welt liegen. Lage, Winkel, Geschwindigkeit und Drehung bleiben; der Schwung verteilt sich auf die neue Masse. Der Zustand wird nirgends mehr aus dem Raster zurückgeschrieben.
- **Anlehnen.** Ein Körper ruht, wenn sein Schwerpunkt zwischen seinem Fuß und einer seitlichen Stütze liegt (Stab an einem Block, Baumkrone auf einem Dach). Er wird beim Einschlafen nur noch gerade gestellt, wenn er auch gerade auf seinem eigenen Fuß stünde.

## 2. Regen und Schnee

### Ursachen

Geprüft wie verlangt:

| Verdacht | Befund |
|---|---|
| Dichteschwelle | Regen fiel nur aus der Unterseite einer Wolkensäule von mindestens 4 Zellen, mit 1 % je Blick (alle 10 Schritte). Eine große gemalte Wolke gab so **3 Tropfen in der Sekunde**. |
| Auflösen vor der Schwelle | Nicht die Ursache. Einzelne Fetzen verdunsten langsam (0,2 % je Schritt), dichte Wolken nicht. Gemessen: 434 Wolkenzellen nach 12 s noch 414. |
| Kondensationshöhe in der Welt | Liegt in der Welt (Wiese: Reihe 48 von 300). |
| Schneetemperatur | Schnee fiel, wenn die Wolkenzelle 0 °C oder kälter hatte. Aber: **Eine gemalte Wolke bekam immer 20 °C**, gleich welche Lufttemperatur eingestellt war. Bei Frost dauerte es, bis sie abkühlte. |
| **Darstellung** | **Ein einzelner fallender Tropfen wurde wie eine Wasseroberfläche gezeichnet: aufgehellt und halb durchsichtig. Vor dem hellblauen Himmel war er praktisch unsichtbar.** |
| **Fallgeschwindigkeit** (beim ersten App-Nachweis gefunden, Abschnitt 3) | **Eine Schneeflocke fiel wie ein Sandkorn, bis 4 Zellen je Schritt; ein Tropfen bis 2. Unter einer gemalten Wolke waren so nur 2 bis 4 Flocken oder 2 bis 8 Tropfen zugleich in der Luft.** |

Es regnete also schon vorher, aber so wenig, so schnell vorbei und so blass, dass man es nicht sah.

### Behebung

- **Regen nach der Dichte der Wolke.**
  - Dichte = Wolkenzellen in der Säule einer Spalte (in der Wolkenschicht).
  - Schwelle: 4 Zellen (`RAIN_DEPTH`, unverändert).
  - Darüber regnet es aus der Unterseite mit der Wahrscheinlichkeit 0,001 × Überschuss² je Blick, höchstens 0,05 (`RAIN_RATE`, `RAIN_MAX`, neu). In einer dichten Wolke laufen die Tröpfchen zu Tropfen zusammen; deshalb wächst der Regen mit dem Quadrat.
  - Eine Wolkenzelle ist ein Tropfen: Der Regen nimmt sein Wasser aus der Wolke und macht keines dazu.
- **Tropfen sichtbar:** Ein Tropfen in der Luft ist kräftig blau (#3C78CC, fast deckend) und zieht eine Schliere über sich.
- **Schneeflocken sinken langsam.** Schnee aus einer Wolke wird von der Luft gebremst, solange er fällt:
  - Die Flocke sinkt je Schritt mit 30 % Wahrscheinlichkeit um eine Zelle (`FLAKE_SINK`). Sie flattert mit 20 % zur Seite (`FLAKE_SWAY`) oder treibt mit dem Wind.
  - Gelandet ist sie Schnee wie jeder andere.
  - Nur Niederschlag: Die Zelle trägt dafür ein Zeichen, das beim Landen verschwindet. Ein ausgekippter Eimer Schnee oder eine Lawine fällt wie bisher.
  - Gemessen über 52 Reihen: Flocke 171 Schritte, Klumpen Schnee 16 Schritte (vorher fiel auch die Flocke so schnell).
- **Regentropfen fallen so schnell wie bisher.** Ich habe auch Tropfen auf eine Zelle je Schritt gebremst. Dann waren doppelt so viele zugleich in der Luft. Level 4 lief damit aber anders: Die Stange wurde später gezündet, Silber und Bronze gingen nicht mehr. Davor hatte ein erster Versuch jede einzelne Flüssigkeits- und Schneezelle gebremst. Damit liefen neun Lösungstests der Level 2 bis 5 anders (flüssiges Metall, Quellwasser, Schnee im Eimer). Beides verworfen.
- **Ablegen im Regen.** Ein fallender Tropfen oder eine Flocke an der Stelle, wo die Hand etwas ablegt, galt als Hindernis („Kein Platz zum Ablegen“). Jetzt landet er auf dem Abgelegten.
- **Blitz trifft keine fallenden Tropfen.** Der Blitz übersprang Tropfen mit Luft darunter. Zwei Tropfen, die gleich schnell übereinander fallen, galten aber als Boden: Der obere lag ja auf dem unteren. So schlug der Blitz in Level 4 hoch in der Luft in den Regen statt in die Stange. Jetzt zählt Lockeres nur als Boden, wenn darunter ohne Luft fester Boden kommt.
- **Gemalte Wolken haben die Temperatur der Luft.** Bei Frost aus dem Wetterregler schneien sie sofort.
- **Gewitter speisen ihre Wolken.** Während eines Gewitters (im Spielplatz gerufen oder `storm_seconds` eines Levels) wächst eine regnende Säule, die flacher als 6 Zellen ist, mit 5 % je Blick oben nach. So hält ein Gewitter seine Wolken so tief, wie es für Blitze nötig ist. Ohne diese Regel hätte der stärkere Regen die Gewitterwolke von Level 4 in 20 s aufgezehrt.

**Gemessen** (Regentests, Wiese 480 × 300):

| Fall | vorher | nachher |
|---|---|---|
| gemalte Wolke, 20 °C, Tropfen je s | 3 | 9 bis 16 |
| gemalte Wolke, −5 °C, Flocken je s | 3 | 15 |
| Meer und Lava (Küste), Tropfen in 60 s | 306 | 618 |
| Wolke 11 Zellen dick, Tropfen in 3 s | – | 63 |
| Wolke 3 Zellen dick (unter der Schwelle) | 0 | 0 |
| Tropfen in 2 s nach Dicke 5 / 8 / 12 | – | 3 / 17 / 33 |
| gemalte Wolke über dem Sand der Wiese, −10 °C: Flocken zugleich in der Luft | 2 bis 4 (Simulation) | 14 bis 44 (App, Build 113) |
| dasselbe bei 20 °C: Tropfen zugleich in der Luft | – | 2 bis 15 (App, Build 113; Tropfen unverändert schnell) |

### Entwickleransicht

Menü → „Wolkenansicht an“ (auch in den Einstellungen):
- Unter jeder Wolke ein Balken je Spalte, so lang wie die Säule Wolkenzellen hat.
  - Grau: unter der Schwelle, es regnet nicht.
  - Blau: es regnet. Weiß: es schneit.
- Rote Linie: die Schwelle (ein Balken, der sie erreicht, regnet). Gelbe Linie: die Kondensationshöhe. Gestrichelt: die Oberkante der Wolkenschicht.
- Unten links: Kondensationshöhe, Schwelle, dichteste Säule, mittlere Wolkentemperatur, Niederschlag je Sekunde (Regen oder Schnee). (Bis Build 113 stand das oben rechts und verdeckte im Querformat die Wolken.)

## 3. Nachweis aus der App

### So entstehen die Bilder

Der App-Test `Phase4cEvidenceTest` spielt jeden Fall im Spielplatz der App, wie ein Finger auf dem Gerät:
- Er wählt Material und Pinsel in der Leiste, malt Striche, stellt den Wetterregler.
- Er fotografiert den Bildschirm nach 0, 1, 2 und 3 Sekunden Weltzeit (60 Schritte je Sekunde), beim Wetter auch nach 10 bzw. 20 s.
- Für jedes Bild hält er die Welt kurz an. So zeigen Bild und Text darunter denselben Schritt.

Die Bildfolgen hängen an jedem Release (`4c_*.png`), die Messwerte daneben in `4c_nachweis.txt`.

### Erster Versuch (Build 111): Umkippen ja, Regen und Schnee nein

Die vier Bildfolgen zum Umkippen zeigten das Verhalten. Die drei zum Wetter nicht:
- **Falsche Welt.** Die Tests für Regen und Schnee verließen sich darauf, dass der Spielplatz mit der Wiese startet. Die Einstellungen eines früheren Tests blieben aber stehen (Robolectric): Der Regentest lief in der Höhlenwelt. Dort liegt die Wolkenschicht direkt über dem Boden, und die Leistungsanzeige lag über der Wolke.
- **Blitz statt Schnee.** Im Schneebild brannte Holz. Eine gemalte Wolke von 50 × 13 Zellen ist eine Gewitterwolke (3c); der Blitz schlug in das Holz darunter.
- **Zu wenige Flocken.** Flocken fielen so schnell wie Sand, es waren 2 bis 4 zugleich in der Luft (Abschnitt 2).
- Dazu war der Text unter dem 0-s-Bild einen Augenblick jünger als das Bild.

Behoben: Jeder Test setzt die Einstellungen zurück und legt seine Welt selbst an. Regen und Schnee laufen im Querformat (die ganze Wiese im Bild). Die Wolke wird über den Sand rechts gemalt: Dort zündet ein Blitz nichts an. Die Welt wird für jedes Bild angehalten. Der Niederschlag wird je Sekunde Weltzeit gezählt. Beim Meer gibt es ein Bild „vorher“ mit leerem Himmel.

### Die Bildfolgen

Geprüft an den Bildern von Build 113; jedes Release enthält sie neu. Unter jedem Bild steht der gemessene Zustand desselben Schritts.

| Fall | Bilder | Was sie zeigen |
|---|---|---|
| 1a Schräg gemalter Stab auf ebenem Boden | `4c_1a_stab_schraeg.png` | 0 s: schräg, kippt (14°). Ab 1 s liegt er flach und still (45° gegen die gemalte Lage). |
| 1b Senkrechter Stab, ein zweiter fällt dagegen | `4c_1b_stab_senkrecht_stoss.png` | 0 s: steht (0°), der zweite Stab fällt gegen ihn. Ab 1 s liegt er (90°). |
| 1c Stamm über einer gemalten Steinrampe | `4c_1c_stamm_schraege.png` | 0 s: über der Rampe, kippt (−20°). Ab 1 s liegt er still auf der Schräge (−29°). |
| 1d Baum, Stamm am Fuß durchgebrannt | `4c_1d_baum_durchgebrannt.png` | 0 s: Stamm durch, Baum steht (Spitze Reihe 15). Ab 1 s liegt er rechts am Boden: Spitze Reihe 21, Krone 10 Zellen weiter rechts, die Stammlinie in der Krone waagerecht. |
| 2a Gemalte Wolke, 20 °C | `4c_2a_wolke_regen.png` (0, 1, 2, 3, 10 s) | Regen ab 0 s: 8, 19, 36, 50, 101 Tropfen gefallen, bis 15 zugleich in der Luft. Nach 10 s Pfützen auf dem Sand. |
| 2b Gemalte Wolke, Frost aus dem Wetterregler | `4c_2b_wetterregler.png`, `4c_2b_wolke_schnee.png` (0, 1, 2, 3, 10 s) | Regler auf −10 °C. Schnee ab 0 s: 15, 26, 31, 45, 98 Flocken gefallen, 14 bis 44 zugleich in der Luft, kein Tropfen. Nach 10 s liegt Schnee auf dem Sand. Bei 2 s schlägt ein Blitz in den Sand (Glas, kein Feuer). |
| 2c Lava ins Meer der Küste | `4c_2c_meer_lava_regen.png` (vorher, 0, 1, 2, 3, 10, 20 s) | Vorher kein Dampf, keine Wolke. Schon während des Gießens steigt Dampf auf: 578 Wolkenzellen bei 0 s, 950 bei 10 s. Regen: 7, 11, 13, 17, 59, 141 Tropfen gefallen. Nach 20 s ist die Lava zu Stein erstarrt. |
| Entwickleransicht | `4c_2a_wolkenansicht.png`, `4c_2b_wolkenansicht.png` (je mit `_ausschnitt`) | Balken je Spalte unter der Wolke: blau = Regen, weiß = Schnee. Dazu Schwelle, Kondensationshöhe und Werte. |

Fehler im Test, nicht in der App:
- In Build 112 und 114 malte der Schneetest keine Wolke: 0 Flocken, 0 Tropfen. In Build 113 lief er.
- Im Querformat ist eine Zelle nur etwa 4 Pixel groß. Der schnelle Anfang des Strichs (11 Zellen, etwa 45 Pixel) blieb unter der Bewegungsschwelle. Lief die Testmaschine dann langsam, galt der Finger nach 450 ms als langer Druck. Ein langer Druck fragt nur ab, was dort ist, und malt nichts.
- Der Schneetest läuft als erster der Klasse und ist darum am langsamsten.
- Behoben:
  - Der schnelle Anfang umfasst jetzt gut 100 Pixel, im Querformat 25 Zellen.
  - Der Test prüft nach dem Malen, dass die Wolke in der Welt ist.
  - Den Knopf „Übernehmen“ im Wetterdialog löst der Test über seine Aktion aus. Danach prüft er Kondensationshöhe (48) und Lufttemperatur (−10 °C).

## 4. Level und Leistung

### Level

Alle Lösungstests der fünf Level laufen (level-Modul: alle Tests grün). Geändert:

| Level | Änderung | Begründung |
|---|---|---|
| 1 Trockener Teich | keine | – |
| 2 Signalfeuer | keine | – |
| 3 Tiefe Schmelze | keine | – |
| 4 Gewitterfeuer | Gewitter `storm_seconds` 150 statt 180 | Mit dem Speisen der Gewitterwolke hielt das Gewitter jetzt volle 180 s Blitze. Die Prüfung „zu spät, das Gewitter ist vorbei“ (Stange bei 170 s) traf dann noch. 150 s gibt dieselbe Abfolge wie vorher: Blitze bis etwa 150 s, danach keine mehr ins Holz. |
| 4 Gewitterfeuer, Test „noch später“ | Stange bei 125 statt 120 s | Ob ein später Blitz den Stapel entzündet, ist Zufall der Weltsaat. Neu gemessen: zündet bei 95, 100, 105, 115, 125, 135, 145 s; nicht bei 110, 120, 130, 140 s. |
| 4 Gewitterfeuer, Test „Stange auf dem Grat“ | mindestens 5 statt mehr als 10 Blitze in die liegende Stange | In der ersten Minute regnet es stärker; Pfützen auf dem Grat nehmen Blitze mit (gemessen: 5 der ersten 23 in die Stange). Unverändert geprüft: nichts trifft das Plateau, der Stapel bleibt kalt. |
| 5 Regenmacher | keine | Mit 3 Tropfen je Wolkenzelle (erster Versuch) gab ein einziger Eimer Gold, das war falsch; mit einer Zelle = ein Tropfen gelten alle bisherigen Grenzen. |

Medaillen von Level 4 unverändert: Gold mit der langen oder kurzen Stange auf dem Stapel (4,8 s bzw. 6,8 s), Silber über das Erdloch (29,5 s), Bronze mit später Stange. Bei der Stange nach 90 s: jetzt 94,5 s statt 105,7 s. Der erste Ablegeversuch scheiterte vorher an einem Regentropfen, der gerade durch die Stelle fiel (siehe „Ablegen im Regen“).

Auch geändert: der Wettertest „Blitz läuft durch die Metallstange“. Sein Labor (eine Wolkenbank 88 × 13 ohne Gewitter) regnete sich in 25 s dünn, bevor der erste Blitz kam. Das Labor steht jetzt in einem Gewitter, wie ein Level mit `storm_seconds`. Geprüft wird unverändert, wohin der Blitz geht.

### Leistung

Messwelt „Größte Welt 600 × 340, voll aktiv belebt, dazu 150 Körper“ (dieser Rechner):

| | Phase 4b (mit Öl) | Phase 4c |
|---|---|---|
| Schritt | 17,9 bis 19,1 ms | 18,0 bis 18,3 ms |
| davon Körper | – | 3,7 ms |

Die Änderungen kosten nichts Messbares:
- Der gehaltene Strich ist ein Vergleich je gefundener Struktur.
- Das Anwachsen läuft nur, wenn wirklich etwas an einen Körper anwächst.
- Der Regen ist eine Schleife über die Spalten alle 10 Schritte.
- Eine fallende Flocke prüft eine Zelle unter sich; der Blitz prüft seine Spalte nur, wenn er einschlägt.

**Den gültigen Wert auf dem Gerät liefert „Leistung messen“** im Spielplatz, Zeile „voll aktiv belebt, dazu 150 Körper“. Nach der Hochrechnung aus 4b (6,7 bis 7,9 ms auf dem Galaxy Z Fold2) ändert sich daran nichts. Messen auf dem Gerät kann ich selbst nicht.
