# Level malen

Der Boden eines Levels ist ein Bild: `terrain.png` (jede Farbe ein Material, die Farben aus `materials.json`, durchsichtig ist Luft) und `anchor.png` (hell = verankert). Man kann beide in jedem Malprogramm zeichnen.

Hier liegen die Skripte, die die Bilder der Level gemalt haben. Sie brauchen Python 3 mit Pillow und schreiben direkt in `level/src/main/resources/levels/<id>/`:

```bash
python3 tools/levels/trockener_teich.py               # Level 1
python3 tools/levels/signalfeuer.py                   # Level 2
python3 tools/levels/trockener_teich.py 40 30 100 80  # dazu ein Ausschnitt als Text
```

`paint.py` enthält die Hilfen: Rechtecke, Vielecke, Kleckse, Rauschen, Speichern.
