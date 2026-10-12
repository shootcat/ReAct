"""Level 2, Signalfeuer (Vulkan, Kernniveau): ground and anchor mask.

On the left a volcano with a lava lake in its crater, kept liquid by the magma below. In the middle the sea,
down to the bottom of the world: the heat of the volcano cannot creep through the rock to the other side.
On the right a mountain with a small beach at its foot and the woodpile on its summit, under a hood of snow.
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from paint import Canvas, fbm, ROOT

W, H = 180, 110
c = Canvas(W, H)
OUT = os.path.join(ROOT, 'level', 'src', 'main', 'resources', 'levels', 'signalfeuer')
CRATER = (14, 44)     # the lava lake, x from..to
LAVA = 68             # its surface
MAGMA = 77            # the heat of the deep under the lake, from this row down
SEA = 74              # sea level
CHASM = (58, 63)      # a gorge down to the bottom of the world: the heat of the volcano stays on its side
GAP = (72, 100)       # where the sea goes down to the bottom of the world
BEACH = (102, 122)    # the beach at the foot of the mountain
SUMMIT = (133, 156, 24)  # the summit, a cell wider than the woodpile on each side: x from..to, ground row

def surface(x):
    if x < 8: return 52
    if x < CRATER[0]: return 58
    if x <= CRATER[1]: return LAVA
    if x < 50: return 58
    if x < CHASM[0]: return int(58 + (x - 50) * 1.4 + (fbm(x / 5, 1.5, 3) - 0.5) * 3)
    if x <= CHASM[1]: return H
    if x < GAP[0] - 6: return 66
    if x < GAP[0]: return int(66 + (x - GAP[0] + 6) * 6)
    if x <= GAP[1]: return H
    if x < BEACH[0]: return int(H - (x - GAP[1]) * 6)
    if x <= BEACH[1]: return 72 - (x - BEACH[0]) // 5
    # the summit falls away at 45 degrees on both sides of the woodpile, then steeper
    if SUMMIT[0] - 6 <= x < SUMMIT[0]: return SUMMIT[2] + SUMMIT[0] - x
    if SUMMIT[1] < x <= SUMMIT[1] + 6: return SUMMIT[2] + x - SUMMIT[1]
    if x < SUMMIT[0]: return int(68 + (SUMMIT[2] + 6 - 68) * (x - BEACH[1]) / (SUMMIT[0] - 6 - BEACH[1]) + (fbm(x / 5, 4.5, 3) - 0.5) * 3)
    if x <= SUMMIT[1]: return SUMMIT[2]
    return int(SUMMIT[2] + 6 + (x - SUMMIT[1] - 6) * 1.8 + (fbm(x / 5, 7.5, 3) - 0.5) * 3)

# rock under everything; the outside of the volcano in basalt
for x in range(W):
    s = max(0, surface(x))
    for y in range(s, H):
        c.put(x, y, 'rock', True)
for x in range(0, CHASM[0]):
    s = surface(x)
    for y in range(s, min(H, s + 3)):
        if not CRATER[0] - 2 <= x <= CRATER[1] + 2:
            c.put(x, y, 'basalt', True)

# the lava lake over the magma, in a bowl of tuff (anchored ash): it keeps the heat in, rock would carry it
# through the whole volcano to the sea
for x in range(CRATER[0] - 4, CRATER[1] + 5):
    for y in range(52, MAGMA + 10):
        if c.get(x, y) == 'rock':
            c.put(x, y, 'ash', True)
for x in range(CRATER[0], CRATER[1] + 1):
    for y in range(LAVA, MAGMA):
        c.put(x, y, 'lava', False)
    for y in range(MAGMA, MAGMA + 6):
        c.put(x, y, 'magma', True)

# the sea, and the beach: a thin layer of sand on the rock
for x in range(CHASM[1] + 1, BEACH[1] + 1):
    for y in range(SEA, H):
        if c.get(x, y) == 'air':
            c.put(x, y, 'saltwater', False)
for x in range(BEACH[0], BEACH[1] + 1):
    s = surface(x)
    for y in range(s - 3, s):
        if c.get(x, y) == 'air':
            c.put(x, y, 'sand', False)

# the woodpile on the summit: logs stacked as a pyramid with gaps between them
G = SUMMIT[2]
mid = SUMMIT[0] + 12
logs = []
for row, n in enumerate([4, 3, 2, 1]):
    y = G - 2 - row * 2
    x0 = mid - (n * 6 - 2) // 2
    for k in range(n):
        logs.append((x0 + k * 6, x0 + k * 6 + 3, y))
for x0, x1, y in logs:
    c.rect(x0, y, x1, y + 1, 'wood', False)
# under a hood of snow, two cells thick
pile = [(x, y) for x in range(W) for y in range(H) if c.get(x, y) == 'wood']
for x in range(SUMMIT[0] - 4, SUMMIT[1] + 5):
    for y in range(G - 14, G + 5):
        if c.get(x, y) != 'air':
            continue
        d = min(abs(x - px) + abs(y - py) for px, py in pile)
        # only above and beside the pile, not in the pockets under the logs
        roofed = any(c.get(x, yy) == 'wood' for yy in range(y - 1, G - 9, -1))
        # three cells thick
        top = y < G - 8
        if d <= 3 and not roofed:
            c.put(x, y, 'snow', False)

c.save(OUT)
print('saved')

if len(sys.argv) > 1:
    x0, y0, x1, y1 = map(int, sys.argv[1:5])
    key = {'air': '.', 'rock': '#', 'basalt': 'B', 'lava': 'L', 'magma': 'M', 'saltwater': '=', 'sand': 's',
           'wood': 'W', 'snow': '*'}
    for y in range(y0, y1 + 1):
        print('%3d ' % y + ''.join(key.get(c.get(x, y), '?') for x in range(x0, x1 + 1)))
