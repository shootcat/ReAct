"""Level 7, Winternacht (Berg, Kernniveau, Phase 5c): ground and anchor mask.

A village on a ledge high in the mountains, a night of hard frost. Two wooden houses with snow on their roofs, and
beside them the village square with the fire: a low stone hearth with a fire pit, wood burning on a bed of charcoal.
It glows a while, but not through the night; a crag closes the square to the east. Wood is scarce: two logs behind
the first house. Up the slope, on a terrace, a seep of cold pitch and the charcoal burner's heap.
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from paint import Canvas, fbm, ROOT

W, H = 200, 100
c = Canvas(W, H)
OUT = os.path.join(ROOT, 'level', 'src', 'main', 'resources', 'levels', 'winternacht')

LEDGE = 78             # ground row of the ledge with the village
SLOPE = 150            # where the mountain rises
SNOW = 2               # snow on the mountain (the ledge is blown free of it)
HOUSES = (10, 34)      # left wall of each house
HOUSE_W = 20           # outer width of a house
HOUSE_H = 9            # height of its walls
ROOF_SNOW = 3          # snow on the roofs
HEARTH = (70, 88, 1)   # the stone hearth in the square: columns, height
PIT = (74, 84, 4)      # the fire pit in its middle: columns, depth (wood on top, lit in level.json, charcoal below)
LOGS = (2, 2)          # the woodpile behind the first house: first column, how many logs of five
CRAG = (108, 113, 10)  # a crag of rock closing the square to the east: columns, height
TERRACE = (162, 182)   # the terrace on the slope: columns
SEEP = (164, 171, 3)   # the seep of cold pitch on it: columns, depth
KILN = (176, 4)        # the charcoal heap on it: middle column, height

def ground(x):
    if x < SLOPE:
        return LEDGE
    t = (x - SLOPE) / (W - 1 - SLOPE)
    g = int(LEDGE - 50 * t + (fbm(x / 9, 1.5, 33) - 0.5) * 3)
    if TERRACE[0] <= x <= TERRACE[1]:
        g = int(LEDGE - 50 * (TERRACE[0] - SLOPE) / (W - 1 - SLOPE))
    return g

for x in range(W):
    g = ground(x)
    for y in range(g, H):
        c.put(x, y, 'rock', True)
    if x >= SLOPE:
        for y in range(g - SNOW, g):
            c.put(x, y, 'snow', False)

def house(x0):
    """A closed wooden house on the ledge with snow on its roof."""
    x1 = x0 + HOUSE_W - 1
    floor = LEDGE - 1
    roof = floor - HOUSE_H
    for x in range(x0, x1 + 1):
        c.put(x, floor, 'wood', True)
    for x in range(x0 - 1, x1 + 2):
        c.put(x, roof, 'wood', True)
        for y in range(roof - ROOF_SNOW, roof):
            c.put(x, y, 'snow', False)
    for y in range(roof, floor):
        c.put(x0, y, 'wood', True)
        c.put(x1, y, 'wood', True)

for x0 in HOUSES:
    house(x0)

# the hearth: a low platform of stone, the fire pit in its middle, down into the rock: a row of wood on a bed of
# charcoal (the charcoal of a fire in it before: with a lid of wood burning on it, it glows on from the top down)
hx0, hx1, hh = HEARTH
for x in range(hx0, hx1 + 1):
    for y in range(LEDGE - hh, LEDGE):
        c.put(x, y, 'stone', True)
px0, px1, pd = PIT
for x in range(px0, px1 + 1):
    for y in range(LEDGE - hh, LEDGE - hh + pd):
        c.put(x, y, 'wood' if y == LEDGE - hh else 'charcoal', False)

# the crag closing the square to the east
cx0, cx1, ch = CRAG
for x in range(cx0, cx1 + 1):
    for y in range(LEDGE - ch + abs(2 * x - cx0 - cx1) // 4, LEDGE):
        c.put(x, y, 'rock', True)

# the woodpile behind the first house
lx, ln = LOGS
for k in range(ln):
    y = LEDGE - 1 - k
    for x in range(lx, lx + 5):
        c.put(x, y, 'wood', False)

# the terrace: the seep of cold pitch in a hollow, and the charcoal heap
gy = ground(TERRACE[0])
for x in range(TERRACE[0], TERRACE[1] + 1):
    for y in range(gy - SNOW, gy):
        c.put(x, y, 'snow', False)
sx0, sx1, sd = SEEP
for x in range(sx0, sx1 + 1):
    for y in range(gy - SNOW, gy + sd):
        c.put(x, y, 'tar' if y >= gy else 'air', False)
kx, kh = KILN
for x in range(kx - kh * 2, kx + kh * 2 + 1):
    h = kh - abs(x - kx) // 2
    for y in range(gy - SNOW - h, gy - SNOW):
        c.put(x, y, 'charcoal', False)

if __name__ == '__main__':
    c.save(OUT)
    c.preview('/tmp/winternacht_preview.png' if len(sys.argv) < 2 else sys.argv[1], scale=4)
    print('pit', PIT, 'top row', LEDGE - hh, 'terrace row', gy, 'seep', sx0, gy, sx1, gy + sd - 1, 'kiln top', gy - SNOW - kh)
