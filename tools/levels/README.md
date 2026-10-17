# Level malen

Der Boden eines Levels ist ein Bild: `terrain.png` (jede Farbe ein Material, die Farben aus `materials.json`, durchsichtig ist Luft) und `anchor.png` (hell = verankert). Man kann beide in jedem Malprogramm zeichnen.

Hier liegen die Skripte, die die Bilder der Level gemalt haben. Sie brauchen Python 3 mit Pillow und schreiben direkt in `level/src/main/resources/levels/<id>/`:

```bash
python3 tools/levels/trockener_teich.py               # Level 1
python3 tools/levels/signalfeuer.py                   # Level 2
python3 tools/levels/tiefe_schmelze.py                # Level 3
python3 tools/levels/gewitterfeuer.py                 # Level 4
python3 tools/levels/regenmacher.py                   # Level 5
python3 tools/levels/trockener_teich.py 40 30 100 80  # dazu ein Ausschnitt als Text
```

`paint.py` enthält die Hilfen: Rechtecke, Vielecke, Kleckse, Rauschen, Speichern.

Wärmequellen brauchen Abstand oder Dämmung: Magma hält seine Hitze unbegrenzt, und Fels leitet sie weit. Level 2 trennt deshalb Vulkan und Gipfel durch eine Schlucht und das Meer. Level 3 liegt in Tuff (verankerte Asche), der die Wärme nur langsam weitergibt. Level 5 hat links und rechts der heißen Quelle je eine Felsspalte bis zum Grund; ohne sie kochte nach drei Minuten das Meer am Ufer, und nach fünf starben die Pflanzen auf dem Feld an der Wärme aus dem Fels.

Wolken malt man mit der Farbe der Wolke (`cloud`); sie gehören an die Kondensationshöhe des Levels (`cloud_base`), sonst steigen oder sinken sie dorthin.

Seit Phase 4 sind lose Strukturen Körper, die kippen. Ein Pfosten von einer Zelle Breite steht nicht auf einer einzelnen Zelle: Level 4 hat deshalb oben im Holzstoß eine Steckfuge (eine Zelle breit, zwei tief) und im Fels daneben ein Pfostenloch. Holz schwimmt: Ein Plateau, auf dem sich Regen staut, braucht einen Abfluss (Level 4: eine Spalte in eine Höhle darunter), sonst treibt der Holzstoß auf.
