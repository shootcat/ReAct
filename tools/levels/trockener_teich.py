"""Level 1, Trockener Teich (Wald, leicht): ground and anchor mask."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from paint import Canvas, fbm, ROOT

W, H = 220, 140
c = Canvas(W, H)
OUT = os.path.join(ROOT, 'level', 'src', 'main', 'resources', 'levels', 'trockener_teich')
LEFT = 16          # the pool's left wall
WALL = (46, 51)    # the pool's right wall of rock, x from..to; its top at y 30
WINDOW = (43, 47)  # a hole through that wall, rows from..to, stopped with earth
LEVEL = 36         # water level of the pool
RIM = 48           # the ledge in front of the pool's right wall
CLIFF = 82         # right edge of the ledge; the cliff below it
FOOT = 72          # where the hillside begins under the cliff
POND = (150, 195, 98, 110)
BANK = 88          # the flat bank right of the pond

def hill(x):
    return FOOT + (x - CLIFF - 1) * 0.39 + (fbm(x / 14, 0.5, 7) - 0.5) * 4

def surface(x):
    if x < LEFT: return 18 + int((LEFT - x) * 0.15)
    if x <= CLIFF: return RIM
    if x < POND[0]: return int(hill(x))
    if x <= POND[1]: return POND[2]
    return BANK

# rock under everything, a skin of anchored earth on the hillside and the bank
for x in range(W):
    s = surface(x)
    soil = 4 + int(fbm(x / 11, 2.5, 5) * 3)
    for y in range(s, H):
        soft = CLIFF < x < POND[0] or x > POND[1]
        c.put(x, y, 'earth' if soft and y < s + soil else 'rock', True)

# the spring pool: a basin in the hill top, springs in its floor and in its back wall (under water they
# rest, when the level falls they run)
for y in range(20, 62):
    for x in range(LEFT, WALL[0]):
        c.put(x, y, 'air', False)
for x in range(LEFT + 3, WALL[0] - 2, 5):
    c.put(x, 62, 'spring', True)
for y in range(LEVEL + 1, 61):
    c.put(LEFT - 1, y, 'spring', True)

# the pool's right wall of rock, with a hole through it at the foot, stopped with earth (the earth wall)
# that spills out onto the ledge in front at its natural slope
c.rect(WALL[0], 30, WALL[1], RIM, 'rock', True)
for y in range(WINDOW[0], WINDOW[1] + 1):
    for x in range(WALL[0], WALL[1] + 2 + (y - WINDOW[0])):
        c.put(x, y, 'earth', False)

# the creek: a channel from the pool under the ledge to a shaft at the cliff; in the shaft a plug of earth,
# held up by a fallen log wedged across; under the log a shallow niche open to the hillside, with the old
# campfire (smoke gets out at the front)
c.rect(WALL[0], 56, 80, 60, 'air', False)
c.rect(76, 61, 80, 64, 'earth', False)
c.rect(74, 65, CLIFF, 66, 'trunk', False)
c.rect(74, 67, CLIFF, FOOT - 1, 'air', False)
c.rect(74, FOOT - 1, CLIFF, FOOT - 1, 'ash', False)
for x in range(75, 79): c.put(x, FOOT - 2, 'ember', False)

# a dry branch on a bed of withered leaves on the hillside below (loose, so the hand can take it)
def ground(x):
    return next(y for y in range(c.h) if c.get(x, y) not in ('air', 'grass'))
flat = min(ground(x) for x in range(88, 96)) - 1
for x in range(88, 96):
    for y in range(flat, ground(x)):
        c.put(x, y, 'dead_plant', False)
for x in range(89, 95): c.put(x, flat - 1, 'deadwood', False)

# water in the pool and the channel
for y in range(LEVEL, 63):
    for x in range(LEFT, 81):
        inside = x < WALL[0] or 56 <= y <= 60
        if c.get(x, y) == 'air' and inside: c.put(x, y, 'water', False)

# on the bank right of the pond springs in a rock face: their water runs towards the pond but falls into a
# hollow first, whose gravel floor lets it seep through a window into a big cave under the bank and the
# pond; earth in the hollow lets it on
HOLLOW = (200, 204)
for x in range(HOLLOW[0], HOLLOW[1] + 1):
    for y in range(BANK, BANK + 4): c.put(x, y, 'air', False)
    for y in range(BANK + 4, BANK + 6): c.put(x, y, 'gravel', False)
c.rect(HOLLOW[1] + 1, BANK + 5, HOLLOW[1] + 3, BANK + 5, 'air', False)
c.rect(HOLLOW[1] + 3, BANK + 5, W - 2, BANK + 46, 'air', False)
c.rect(POND[0] + 4, POND[3] + 6, HOLLOW[1] + 3, BANK + 46, 'air', False)
for y in range(BANK + 5, BANK + 47):
    for x in range(POND[0] + 4, W - 1):
        if c.get(x, y) == 'air' and fbm(x / 5, y / 5, 21) > 0.72: c.put(x, y, 'rock', True)
c.rect(214, BANK - 12, W - 1, BANK - 1, 'rock', True)
for y in range(BANK - 9, BANK - 3): c.put(214, y, 'spring', True)

# the empty pond, lined with rock
c.rect(POND[0], POND[2], POND[1], POND[3], 'air', False)

# a heap of loose earth on the hill top
for x in range(2, 13):
    for y in range(surface(x) - 4 + abs(x - 7) // 2, surface(x)):
        c.put(x, y, 'earth', False)

# grass on the anchored earth of the hillside
for x in range(CLIFF + 1, POND[0]):
    s = surface(x)
    if c.get(x, s) == 'earth' and c.get(x, s - 1) == 'air' and fbm(x / 6, 3.5, 11) > 0.35:
        c.put(x, s - 1, 'grass', False)

c.save(OUT)
print('saved')

if len(sys.argv) > 1:
    x0, y0, x1, y1 = map(int, sys.argv[1:5])
    key = {'air': '.', 'rock': '#', 'earth': 'e', 'deadwood': 'd', 'dead_plant': 'l', 'ash': ',', 'ember': '*', 'trunk': 'T',
           'grass': 'g', 'water': '~', 'spring': 'S', 'gravel': ':'}
    for y in range(y0, y1 + 1):
        print('%3d ' % y + ''.join(key.get(c.get(x, y), '?') for x in range(x0, x1 + 1)))
