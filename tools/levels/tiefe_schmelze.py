"""Level 3, Tiefe Schmelze (Mine, Obergrenze): ground and anchor mask.

A mine under a hill. From the surface a shaft leads down to the gallery. In the gallery a heap of salt, a
little sand, a seep spring in a hollow, ore rubble and, at its end, a glowing vent: a small pool of lava over
magma. In the floor of the gallery the shaft to the deep chamber, stopped with ice. In the deep chamber a bed
of sand, and beside it a pocket of lava over magma behind a plug of earth. Veins of ore run through the tuff. The tools only reach what is open
to the sky: until the ice is gone, the deep chamber is out of reach.
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from paint import Canvas, fbm, ROOT

W, H = 190, 160
c = Canvas(W, H)
OUT = os.path.join(ROOT, 'level', 'src', 'main', 'resources', 'levels', 'tiefe_schmelze')
SURF = 16             # the surface of the hill
ENTRY = (24, 27)      # the way down from the surface, x from..to
GALLERY = (20, 134, 38, 45)   # x from..to, rows from..to; its floor is the row below
SHAFT = (82, 87)      # the shaft to the deep chamber, x from..to
PLUG = (46, 74)       # the ice in the shaft, rows from..to
CHAMBER = (56, 109, 118, 133)   # the deep chamber: x from..to, rows from..to (air)
SUMP = (62, 90, 134, 143)       # a sump under the shaft, where meltwater gathers
SAND = (92, 109, 134, 141)     # the bed of sand on a step beside the sump, in front of the pocket
POCKET = (122, 146, 116, 133)  # the lava pocket (magma round it), x from..to, rows from..to
MOUTH = (110, 121, 129, 133)  # the tunnel from the pocket into the chamber, stopped with earth
VENT = (30, 40, 46, 49)       # the glowing vent near the way down (its heat goes up there): lava rows, magma below

def surface(x):
    return SURF + int((fbm(x / 18, 0.7, 4) - 0.5) * 6)

# under a skin of earth with grass the hill is tuff (anchored ash): it carries heat only slowly, so the warmth
# of the vent and the pocket stays where it is and the ice keeps
for x in range(W):
    s = surface(x)
    for y in range(s, H):
        c.put(x, y, 'earth' if y < s + 4 else 'ash' if 10 <= x < W - 10 and y < H - 6 else 'rock', True)
    if fbm(x / 7, 2.2, 6) > 0.35:
        c.put(x, s - 1, 'grass', False)

# the way down and the gallery
c.rect(ENTRY[0], 0, ENTRY[1], GALLERY[2], 'air', False)
for x in range(ENTRY[0], ENTRY[1] + 1):
    for y in range(0, surface(x) + 1):
        c.put(x, y, 'air', False)
c.rect(GALLERY[0], GALLERY[2], GALLERY[1], GALLERY[3], 'air', False)
floor = GALLERY[3] + 1

# in the gallery: a heap of salt, a little sand, a hollow with a seep spring, ore rubble
for x in range(43, 67):
    for y in range(floor - 1 - min(3, x - 43, 66 - x), floor):
        c.put(x, y, 'salt', False)
for x in range(90, 98):
    for y in range(floor - 1 - min(x - 90, 97 - x) // 2, floor):
        c.put(x, y, 'sand', False)
c.rect(68, floor, 74, floor + 2, 'air', False)
for x in range(69, 74):
    c.put(x, floor + 3, 'spring', True)
for x in range(98, 108):
    for y in range(floor - 1 - min(x - 98, 107 - x) // 2, floor):
        c.put(x, y, 'rubble', False)
for x in range(120, 128):
    for y in range(floor - 1 - min(x - 120, 127 - x) // 2, floor):
        c.put(x, y, 'ore_rubble', False)

# the glowing vent: a pool of lava over magma, in a bowl of tuff (anchored ash) that keeps the heat in
for x in range(VENT[0] - 3, VENT[1] + 4):
    for y in range(floor, VENT[3] + 9):
        c.put(x, y, 'ash', True)
c.rect(VENT[0], VENT[2], VENT[1], VENT[3], 'lava', False)
c.rect(VENT[0], VENT[3] + 1, VENT[1], VENT[3] + 4, 'magma', True)

# the shaft down to the deep chamber, the ice in its upper part
c.rect(SHAFT[0], floor, SHAFT[1], CHAMBER[2] - 1, 'air', False)
c.rect(SHAFT[0], PLUG[0], SHAFT[1], PLUG[1], 'ice', False)

# the deep chamber and its bed of sand
c.rect(CHAMBER[0], CHAMBER[2], CHAMBER[1], CHAMBER[3], 'air', False)
c.rect(SUMP[0], SUMP[2], SUMP[1], SUMP[3], 'air', False)
c.rect(SAND[0], SAND[2], SAND[1], SAND[3], 'sand', False)

# the lava pocket beside it, magma all round it, in a thick coat of tuff (the chamber stays cool), and a tunnel through it into the chamber, stopped with earth that spills out at its end
for x in range(MOUTH[0], POCKET[1] + 12):
    for y in range(POCKET[2] - 10, POCKET[3] + 10):
        if c.inside(x, y) and c.get(x, y) in ('rock', 'ash'):
            c.put(x, y, 'ash', True)
c.rect(POCKET[0] - 2, POCKET[2] - 2, POCKET[1] + 2, POCKET[3] + 2, 'magma', True)
c.rect(POCKET[0], POCKET[2], POCKET[1], POCKET[3], 'lava', False)
# the tunnel is lined with magma up to three cells before the chamber: the lava stays liquid on its way out
c.rect(MOUTH[0] + 3, MOUTH[2] - 1, POCKET[0] - 1, MOUTH[2] - 1, 'magma', True)
c.rect(MOUTH[0] + 3, MOUTH[3] + 1, POCKET[0] - 1, MOUTH[3] + 1, 'magma', True)
c.rect(MOUTH[0], MOUTH[2], POCKET[0] - 1, MOUTH[3], 'earth', False)
for k in range(1, 5):
    for y in range(MOUTH[3] - k + 1, MOUTH[3] + 1):
        c.put(MOUTH[0] - k, y, 'earth', False)

# veins of ore in the tuff, out of reach and away from the heat: what the ore rubble in the gallery came from
for x0, y0, x1, y1, seed in ((142, 30, 172, 36, 1.3), (98, 60, 118, 70, 2.9), (44, 84, 66, 94, 4.1)):
    for x in range(x0, x1 + 1):
        t = (x - x0) / max(1, x1 - x0)
        yc = y0 + (y1 - y0) * t + (fbm(x / 6, seed, 3) - 0.5) * 4
        for y in range(int(yc), int(yc) + 2):
            if c.get(x, y) == 'ash':
                c.put(x, y, 'ore', True)

c.save(OUT)
print('saved')

if len(sys.argv) > 1:
    x0, y0, x1, y1 = map(int, sys.argv[1:5])
    key = {'air': '.', 'rock': '#', 'earth': 'e', 'grass': 'g', 'salt': '+', 'sand': 's', 'spring': 'S', 'ore_rubble': 'o',
           'ash': 'a', 'lava': 'L', 'magma': 'M', 'ice': 'I', 'trunk': 'T', 'ore': 'O'}
    for y in range(y0, y1 + 1):
        print('%3d ' % y + ''.join(key.get(c.get(x, y), '?') for x in range(x0, x1 + 1)))
