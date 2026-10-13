"""Level 5, Regenmacher (Küste, Kernniveau): ground and anchor mask.

On the left the sea, salt water, behind a headland of rock. Further in a hot spring: a dry basin of stone over
a pocket of magma, in tuff that keeps the heat in. In the middle a dry field with a few tufts of grass and some
seeds. On its right a cliff, part of a mountain that reaches high above the height where clouds form. The wind
blows steadily from the sea towards the mountain.

There is no fresh water. Salt water kills grass and seeds. Salt water boiled in the hot basin leaves its salt
behind; its steam rises, turns into cloud, the wind carries the cloud to the cliff, where it piles up and rains
fresh water onto the field.
"""
import sys, os, math
sys.path.insert(0, os.path.dirname(__file__))
from paint import Canvas, fbm, ROOT

W, H = 220, 130
c = Canvas(W, H)
OUT = os.path.join(ROOT, 'level', 'src', 'main', 'resources', 'levels', 'regenmacher')
SEA = (44, 92)          # the sea: up to x, its surface
BASIN = (72, 84, 80)    # the hot basin: inner x from..to, its rim row
FLOOR = 88              # the basin's floor of stone; magma under it
FIELD = (140, 186, 84)  # the field: x from..to, its surface row
CLIFF = 187             # the cliff rises from here, straight up
TOP = 10                # the mountain's top row

def surface(x):
    if x <= SEA[0]: return SEA[1] + 16 - int(fbm(x / 8, 0.5, 29) * 4)
    if x < 56: return int(SEA[1] + 14 - (x - SEA[0]) * 2.4)
    if x < BASIN[0] - 2: return 84 + int((fbm(x / 6, 1.5, 29) - 0.5) * 3)
    if x <= BASIN[1] + 2: return BASIN[2]
    if x < FIELD[0]: return 86 + int((fbm(x / 9, 2.5, 29) - 0.5) * 4)
    if x < CLIFF: return FIELD[2]
    return TOP + int(fbm(x / 6, 3.5, 29) * 5) + (x - CLIFF) // 6

for x in range(W):
    s = max(0, surface(x))
    for y in range(s, H):
        c.put(x, y, 'rock', True)
# a cleft through the headland down to the bottom of the world: the heat of the magma cannot creep through the
# rock to the sea (as the gorge in level 2)
# and a second one on the other side, between the hot spring and the plain that leads to the field
for cleft in ((58, 60), (95, 97)):
    for x in range(cleft[0], cleft[1] + 1):
        for y in range(surface(x), H):
            c.put(x, y, 'air', False)
# the sea
for x in range(0, 56):
    for y in range(SEA[1], H):
        if c.get(x, y) == 'air':
            c.put(x, y, 'saltwater', False)
# the hot ground: tuff (anchored ash) round the magma keeps its heat in
for x in range(BASIN[0] - 8, BASIN[1] + 9):
    for y in range(82, 100):
        if c.get(x, y) == 'rock':
            c.put(x, y, 'ash', True)
# the hot basin: stone walls and floor, magma under the floor
c.rect(BASIN[0] - 2, BASIN[2], BASIN[0] - 1, FLOOR, 'stone', True)
c.rect(BASIN[1] + 1, BASIN[2], BASIN[1] + 2, FLOOR, 'stone', True)
c.rect(BASIN[0], BASIN[2], BASIN[1], FLOOR - 1, 'air', False)
c.rect(BASIN[0] - 2, FLOOR, BASIN[1] + 2, FLOOR, 'stone', True)
c.rect(BASIN[0] - 1, FLOOR + 1, BASIN[1] + 1, FLOOR + 4, 'magma', True)
# the field: dry earth over the rock, a few tufts of grass, seeds lying on it
for x in range(FIELD[0], CLIFF):
    for y in range(FIELD[2], FIELD[2] + 5):
        c.put(x, y, 'earth', True)
for x0 in (146, 163, 178):
    for x in range(x0, x0 + 3):
        c.put(x, FIELD[2], 'grass', True)
for x in range(FIELD[0] + 3, CLIFF - 2, 6):
    if c.get(x, FIELD[2]) == 'earth':
        c.put(x, FIELD[2] - 1, 'seed', False)

c.save(OUT)
print('saved')

if len(sys.argv) > 1:
    x0, y0, x1, y1 = map(int, sys.argv[1:5])
    key = {'air': '.', 'rock': '#', 'earth': 'e', 'saltwater': '=', 'magma': 'M', 'ash': 'a', 'stone': 'S', 'seed': ':', 'grass': 'g'}
    for y in range(y0, y1 + 1):
        print('%3d ' % y + ''.join(key.get(c.get(x, y), '?') for x in range(x0, x1 + 1)))
