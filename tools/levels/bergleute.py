"""Level 6, Bergleute (Mine, Kernniveau, Phase 5c): ground and anchor mask.

Four miners are shut in a gallery deep in the mountain. The adit that leads out (up a ramp, then level into the
cliff above the village) is buried by a fall of rubble. Firedamp seeps from cracks in the gallery's roof and has
gathered where the adit is highest, behind the rubble: a cushion of gas down the top of the ramp.

In the mouth of the adit, right in front of the rubble, the shepherds keep a fire out of the wind: a pit in the
floor full of burning tar, which burns for many minutes. Firedamp let out through the rubble meets it at once, and
the whole cloud goes off. The miners do not walk past a fire either. In the village below a well and a heap of
sand.

The mouth of the adit is a ledge in the cliff, four cells over the valley: the miners drop out of it, nobody
walks in. In front of the rubble it is two cells higher than the adit, so that a bucket of sand on the fire still
leaves room for the miners to walk over it.
"""
import sys, os, math
sys.path.insert(0, os.path.dirname(__file__))
from paint import Canvas, fbm, ROOT

W, H = 200, 130
c = Canvas(W, H)
OUT = os.path.join(ROOT, 'level', 'src', 'main', 'resources', 'levels', 'bergleute')

VALLEY = 70            # ground row of the valley with the village
CLIFF = 56             # the cliff face: first column of the mountain
CLIFF_TOP = 44         # where the cliff ends and the mountain slope begins
SUMMIT = (124, 22)     # the mountain top: column, row
MOUTH = (56, 66, 74)   # the level part of the adit: from column, floor row, to column
GALLERY = (112, 104, 172)  # the gallery: from column, floor row, to column
TUNNEL = 6             # the height of the adit and the gallery
PORTAL = 2             # the mouth of the adit, in front of the rubble, is this much higher
PLUG = (63, 73)        # the fall of rubble in the level part of the adit: columns
CUSHION = 96           # the firedamp gathered behind the rubble reaches down the ramp to this column
VENTS = (144, 150)     # the cracks in the gallery roof the firedamp seeps from: columns
FIRE = (58, 61, 2)     # the shepherds' fire in the mouth of the adit, a pit of burning tar: columns, depth
WELL = (14, 19, 5)     # the well: columns, depth
SAND = (3, 10, 3)      # the heap of sand at the far end of the village: columns, height

def surface(x):
    if x < CLIFF:
        return VALLEY
    sx, sy = SUMMIT
    if x <= sx:
        t = (x - CLIFF) / (sx - CLIFF)
        return int(CLIFF_TOP + (sy - CLIFF_TOP) * t + (fbm(x / 9, 0.5, 21) - 0.5) * 3)
    t = (x - sx) / (W - 1 - sx)
    return int(sy + (64 - sy) * t + (fbm(x / 9, 1.5, 21) - 0.5) * 3)

# rock under everything, earth on the valley floor
for x in range(W):
    s = surface(x)
    soil = 3 + int(fbm(x / 7, 2.5, 23) * 3)
    for y in range(s, H):
        c.put(x, y, 'earth' if y < s + soil and x < CLIFF else 'rock', True)
# the adit: level into the cliff, then a ramp down to the gallery
fx0, fy, fx1 = MOUTH
for x in range(fx0, fx1 + 1):
    for y in range(fy - TUNNEL - (PORTAL if x < PLUG[0] else 0), fy):
        c.put(x, y, 'air', False)
gx0, gy, gx1 = GALLERY
for x in range(fx1, gx0 + 1):
    floor = fy + (x - fx1)
    for y in range(floor - TUNNEL, floor):
        c.put(x, y, 'air', False)
# the gallery
for x in range(gx0, gx1 + 1):
    for y in range(gy - TUNNEL, gy):
        c.put(x, y, 'air', False)
# cracks in its roof
for x in range(VENTS[0], VENTS[1] + 1):
    c.put(x, gy - TUNNEL - 1, 'gas_vent', True)
# the fall of rubble in the adit
for x in range(PLUG[0], PLUG[1] + 1):
    for y in range(fy - TUNNEL, fy):
        c.put(x, y, 'rubble', False)
# the firedamp gathered behind it: the level part of the adit and the top of the ramp
for x in range(PLUG[1] + 1, CUSHION + 1):
    floor = fy if x <= fx1 else fy + (x - fx1)
    for y in range(floor - TUNNEL, floor):
        c.put(x, y, 'firedamp', False)
# the fire in the mouth of the adit: a pit in the floor full of tar (lit by its heat, set in level.json)
for x in range(FIRE[0], FIRE[1] + 1):
    for y in range(fy, fy + FIRE[2]):
        c.put(x, y, 'tar', False)
# the well in the village, and a heap of sand
c.rect(WELL[0], VALLEY, WELL[1], VALLEY + WELL[2] - 1, 'water', False)
for x in range(SAND[0], SAND[1] + 1):
    h = SAND[2] - abs(2 * x - SAND[0] - SAND[1]) // 4
    for y in range(VALLEY - h, VALLEY):
        c.put(x, y, 'sand', False)

if __name__ == '__main__':
    c.save(OUT)
    c.preview('/tmp/bergleute_preview.png' if len(sys.argv) < 2 else sys.argv[1], scale=4)
    print('saved', OUT, 'tar', FIRE[0], fy, FIRE[1], fy + FIRE[2] - 1)
