"""Level 4, Gewitterfeuer (Hochland, Kernniveau): ground and anchor mask.

A thunderstorm hangs over the whole valley (painted cloud, `storm_seconds` in level.json). On the left a bare
ridge of rock, then a hill with a tall tree, the highest thing under the cloud: the lightning goes there.
In the middle a lake. On the right a plateau of rock with the woodpile; at its left end an overhang, and
under it, out of the lightning's way, old fence posts of metal: one long, three short.

There is no fire in the level. A post stood on the woodpile is the highest metal under the cloud: the stroke
runs down it and goes to ground in the wood. A post stood in the rock beside the pile takes it away; one in
the heap of sand at the end of the plateau makes glass.
"""
import sys, os, math
sys.path.insert(0, os.path.dirname(__file__))
from paint import Canvas, fbm, ROOT

W, H = 200, 130
c = Canvas(W, H)
OUT = os.path.join(ROOT, 'level', 'src', 'main', 'resources', 'levels', 'gewitterfeuer')
CLOUD = (5, 20)        # rows of the thunder cloud; its bottom is the condensation height
RIDGE = (0, 40, 50)    # the ridge: x from..to, its peak
HILL = (40, 96, 66)    # the hill: x from..to, its top row (the tree stands on it)
TREE_X = 68
LAKE = (96, 132, 100)  # the lake: x from..to, its surface
PLATEAU = (134, 199, 80)  # the plateau: x from..to, its ground row
SHELTER = (136, 153, 58)  # the overhang: x from..to, the row of its roof
LONG = (142, 15)       # the long post: column, length
SHORT = [145, 148, 151]  # the short posts' columns
SHORT_LEN = 7
PILE = 162             # left end of the woodpile's bottom row
DUNE = (187, 197, 5)   # a heap of sand at the right end of the plateau: x from..to, height

def surface(x):
    if x <= RIDGE[1]:
        return int(RIDGE[2] + abs(x - 16) * 1.25 + (fbm(x / 6, 0.5, 11) - 0.5) * 4)
    if x < HILL[1]:
        t = (x - TREE_X) / 30
        return int(HILL[2] + t * t * 26 + (fbm(x / 9, 1.5, 11) - 0.5) * 3)
    if x <= LAKE[1]:
        return LAKE[2] + 14 - int(abs(x - (LAKE[0] + LAKE[1]) / 2) * 0.5)
    return PLATEAU[2]

# rock under everything, a skin of earth on the hill
for x in range(W):
    s = surface(x)
    soil = 4 + int(fbm(x / 7, 2.5, 13) * 3)
    for y in range(s, H):
        c.put(x, y, 'earth' if RIDGE[1] < x < HILL[1] and y < s + soil else 'rock', True)
# the lake, a narrow shore of sand on its right
for x in range(HILL[1] - 6, LAKE[1] + 1):
    for y in range(LAKE[2], H):
        if c.get(x, y) == 'air':
            c.put(x, y, 'water', False)
# the plateau's left wall falls to the lake; the overhang at its left end: a roof on a pillar, open to the right
for y in range(SHELTER[2], PLATEAU[2]):
    for x in range(SHELTER[0], SHELTER[0] + 4):
        c.put(x, y, 'rock', True)
for x in range(SHELTER[0], SHELTER[1] + 1):
    rough = int((fbm(x / 3, 4.5, 17) - 0.5) * 2)
    for y in range(SHELTER[2] - 3 + rough, SHELTER[2] + 1):
        c.put(x, y, 'rock', True)
# the posts under it, each on its own, a cell of air around it
c.rect(LONG[0], PLATEAU[2] - LONG[1], LONG[0], PLATEAU[2] - 1, 'metal', False)
for x in SHORT:
    c.rect(x, PLATEAU[2] - SHORT_LEN, x, PLATEAU[2] - 1, 'metal', False)
# the woodpile: logs stacked as a pyramid with gaps between them (as in level 2)
logs = []
for row, n in enumerate([4, 3, 2]):
    y = PLATEAU[2] - 2 - row * 2
    x0 = PILE + row * 3
    for k in range(n):
        logs.append((x0 + k * 6, x0 + k * 6 + 3, y))
for x0, x1, y in logs:
    c.rect(x0, y, x1, y + 1, 'wood', False)
# a heap of sand blown up against the right end of the plateau
mid = (DUNE[0] + DUNE[1]) / 2
for x in range(DUNE[0], DUNE[1] + 1):
    h = int(DUNE[2] * (1 - abs(x - mid) / ((DUNE[1] - DUNE[0]) / 2 + 1)) + 0.999)
    for y in range(PLATEAU[2] - h, PLATEAU[2]):
        c.put(x, y, 'sand', False)
# the thunder cloud over the whole valley, its edges ragged
for x in range(2, W - 2):
    top = CLOUD[0] + int(fbm(x / 8, 6.5, 19) * 4)
    edge = min(x - 2, W - 3 - x)
    if edge < 6:
        top += 6 - edge
    for y in range(top, CLOUD[1] + 1):
        c.put(x, y, 'cloud', False)

c.save(OUT)
print('saved; logs', logs, 'top of pile', min(l[2] for l in logs))

if len(sys.argv) > 1:
    x0, y0, x1, y1 = map(int, sys.argv[1:5])
    key = {'air': '.', 'rock': '#', 'earth': 'e', 'water': '~', 'wood': 'W', 'metal': 'M', 'cloud': 'c', 'sand': 's'}
    for y in range(y0, y1 + 1):
        print('%3d ' % y + ''.join(key.get(c.get(x, y), '?') for x in range(x0, x1 + 1)))
