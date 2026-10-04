# Leveldesign: Welt „Prüfung“

Zehn Level nach dem Leitfaden: jedes hat genau eine Einsicht, eine Falle für den ersten Instinkt und ab Level 3 mindestens zwei Hebel für Tiefe. Alle Level nutzen Ablagefelder (`"placement": "marked"`). Die Kennzahlen kommen aus `DifficultyReportTest` (vollständige Suche, siehe README, Abschnitt „Schwierigkeit“).

## p_01 „Umweg“

![Start](screenshots/pruefung/p_01.png)

- **Einsicht:** Wasser kommt nicht nur von unten: Als Dampf steigt es auf, wird unter der Decke zur Wolke und regnet dort, wohin kein Wasser fließen kann.
- **Falle:** Eine Flamme neben dem Samen verbrennt ihn. Eine einzelne Flamme am Teich verlischt und gibt zu wenig Dampf für eine Wolke. Ein großes Feuer direkt am Wasser wird gelöscht.
- **Lösung:** Die beiden Flammen zu einem großen Feuer verschmelzen und so auf die Mauer am Teich legen, dass seine Hitze das Wasser über eine Luftlücke erreicht. Der Dampf wird zur Wolke, der Wind treibt sie zum Samen.
- **Hebel:** Umnutzung (Feuer erzeugt hier Wasser), verzögerte Folgen (Dampf → Wolke → Wind → Regen).
- **Kennzahlen:** 2 Mindestzüge · 3 Lösungen · 44 % Sackgassen (4 von 9 ersten Zügen) · der naheliegende Zug (Flamme neben den Samen) führt nicht zur Lösung · Köder: keiner (erst ab Level 5 gefordert).

## p_02 „Zu wenig“

![Start](screenshots/pruefung/p_02.png)

- **Einsicht:** Wasser muss erst gesammelt werden, bevor es reicht.
- **Falle:** Eine Pfütze (4 oder 3 Einheiten) direkt ans große Feuer verdampft wirkungslos, es braucht mindestens 6 auf einmal. Wer zuerst die kleine Flamme löscht, verbraucht 2 Einheiten, danach reichen beide zusammen nicht mehr. Wer die Pfützen auf flachem Boden zusammenlegt, verliert das Wasser an den Boden.
- **Lösung:** Eine Pfütze in die kleine Mulde in der Mitte tragen, die andere dazugießen (7 Einheiten), das gesammelte Wasser ans große Feuer.
- **Hebel:** Knappheit (7 Einheiten reichen genau, 5 nicht mehr), Reihenfolge (erst sammeln, dann gießen), Irreversibilität (verdampftes und versickertes Wasser ist weg).
- **Kennzahlen:** 3 Mindestzüge · 2 Lösungen · 75 % Sackgassen (6 von 8 ersten Zügen) · der naheliegende Zug (Pfütze ans Feuer) führt nicht zur Lösung · Köder: die kleine Flamme (fest, kein bewegliches Objekt).

## p_03 „Verdrängung“

![Start](screenshots/pruefung/p_03.png)

- **Einsicht:** Ein Stein kann Wasser heben: was im Teich versinkt, schiebt das Wasser über den Rand in die höhere Mulde.
- **Falle:** Wer zuerst einen Stein in den Teich wirft, schickt den Überlauf an das große Feuer, das ihn verdampft; danach reicht das Wasser nicht mehr. Der Bimsstein im Teich schwimmt nur und hebt nichts. Ein Stein im Spalt vor dem Feuer fehlt danach im Teich.
- **Lösung:** Erst den Bimsstein in den Spalt zwischen Mulde und Feuer: er hält dort das Wasser und schirmt die Hitze ab. Dann beide Steine in den Teich.
- **Hebel:** Reihenfolge (Schild vor dem ersten Überlauf), Irreversibilität (verdampftes Wasser ist weg), Umnutzung (der Bimsstein, der nichts hebt, wird zum Hitzeschild).
- **Kennzahlen:** 3 Mindestzüge · 8 Lösungen (Reihenfolge der Steine und welches der beiden Felder über dem Teich) · 33 % Sackgassen (4 von 12) · der naheliegende Zug (ein Ding auf das Feld über der Mulde) führt nicht zur Lösung.
- **Abweichung vom Leitfaden:** Ein Stein kann ein Feuer in dieser Engine nicht ersticken (es gibt keine solche Regel). Stattdessen neutralisiert ein Gegenstand im Spalt das Feuer, weil feste Dinge die Strahlungshitze des großen Feuers aufhalten. Die Einsicht und die Falle bleiben gleich.

## p_04 „Tragbares Wasser“

![Start](screenshots/pruefung/p_04.png)

- **Einsicht:** Gefrorenes Wasser kann man mitnehmen: Süßwasser, das am Frostkristall zu Eis wird, ist ein Block, den man über jede Mauer tragen und erst am Ziel wieder schmelzen kann.
- **Falle:** Der Samen liegt direkt am Meer, und seit p_03 weiß man, dass ein Stein Wasser hebt. Ein Stein im Meer hebt aber Salzwasser an den Samen und versalzt ihn. Eine Flamme direkt neben dem Samen verbrennt ihn, im Brunnen oder im Meer verlischt sie.
- **Lösung:** Den Stein in den Brunnen werfen, damit das Süßwasser bis unter das Feld steigt. Den Frost auf dieses Feld setzen: das oberste Wasser gefriert. Das Eis neben den Samen tragen und die Flamme auf der anderen Seite des Eises ablegen.
- **Hebel:** Reihenfolge (Stein und Frost brauchen dasselbe Feld über dem Brunnen; wer zuerst den Frost setzt, muss ihn wieder wegnehmen), Irreversibilität (verbrannter oder versalzener Samen, verloschene Flamme), Nebenwirkung (der Stein im Meer hebt das falsche Wasser), Umnutzung (der Stein hebt hier Wasser für den Frost, nicht für das Ziel).
- **Kennzahlen:** 4 Mindestzüge · 4 Lösungen (wann die Flamme kommt) · 33 % Sackgassen (4 von 12 ersten Zügen: Flamme in den Brunnen, ins Meer oder neben den Samen, Stein ins Meer) · der naheliegende Zug (ein Ding auf das Feld neben dem Samen) führt nicht zur Lösung · Köder: keiner (erst ab Level 5 gefordert).
- **Abweichung vom Leitfaden:** Der Frost steht nicht schon neben einem vollen Wasserfeld; erst der Stein hebt das Süßwasser im Brunnen bis an den Frost. Das ergibt den vierten Zug und die Falle mit dem Meer. Das Eis liegt neben statt über dem Samen: Schmilzt es über ihm, hebt das Wasser den gekeimten Samen an, und er treibt in die Flamme daneben (Samen schwimmen in dieser Engine).
