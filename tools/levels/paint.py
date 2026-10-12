"""Painting helpers for level ground: a grid of material names and an anchor mask, saved as PNGs."""
import json, math, os, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
MATS = json.load(open(os.path.join(ROOT, 'sim', 'src', 'main', 'resources', 'materials.json')))['materials']
COLOR = {m['id']: m['color'] for m in MATS}

def _hash(i, j, seed):
    h = (i * 374761393 + j * 668265263 + seed * 1442695041) & 0xffffffff
    h = ((h ^ (h >> 13)) * 1274126177) & 0xffffffff
    return ((h ^ (h >> 16)) & 0xffff) / 65535.0

def noise(x, y=0.0, seed=1):
    """Smooth value noise in 0..1."""
    xi, yi = math.floor(x), math.floor(y)
    tx, ty = x - xi, y - yi
    sx, sy = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = _hash(xi, yi, seed); b = _hash(xi + 1, yi, seed)
    c = _hash(xi, yi + 1, seed); d = _hash(xi + 1, yi + 1, seed)
    return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy

def fbm(x, y=0.0, seed=1, octaves=3):
    s, amp, tot = 0.0, 1.0, 0.0
    for o in range(octaves):
        s += noise(x * (2 ** o), y * (2 ** o), seed + o) * amp
        tot += amp
        amp *= 0.5
    return s / tot

class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.m = [['air'] * w for _ in range(h)]
        self.a = [[False] * w for _ in range(h)]

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def put(self, x, y, mat, anchor=None):
        if not self.inside(x, y): return
        self.m[y][x] = mat
        if anchor is not None: self.a[y][x] = anchor

    def get(self, x, y):
        return self.m[y][x] if self.inside(x, y) else None

    def fill(self, pred, mat, anchor=None, box=None):
        x0, y0, x1, y1 = box or (0, 0, self.w - 1, self.h - 1)
        for y in range(max(0, y0), min(self.h - 1, y1) + 1):
            for x in range(max(0, x0), min(self.w - 1, x1) + 1):
                if pred(x, y): self.put(x, y, mat, anchor)

    def rect(self, x0, y0, x1, y1, mat, anchor=None):
        self.fill(lambda x, y: True, mat, anchor, (x0, y0, x1, y1))

    def poly(self, pts, mat, anchor=None):
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        def inside(x, y):
            px, py = x + 0.5, y + 0.5
            c = False
            j = len(pts) - 1
            for k in range(len(pts)):
                if (ys[k] > py) != (ys[j] > py) and px < (xs[j] - xs[k]) * (py - ys[k]) / (ys[j] - ys[k]) + xs[k]:
                    c = not c
                j = k
            return c
        self.fill(inside, mat, anchor, (int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))))

    def blob(self, cx, cy, rx, ry, mat, anchor=None, rough=0.25, seed=3, only=None):
        def inside(x, y):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            ang = math.atan2(dy, dx)
            r = 1 + rough * (fbm(math.cos(ang) * 2 + cx * 0.01, math.sin(ang) * 2, seed) - 0.5) * 2
            return dx * dx + dy * dy <= r * r and (only is None or self.m[y][x] in only)
        self.fill(inside, mat, anchor, (int(cx - rx * 1.5), int(cy - ry * 1.5), int(cx + rx * 1.5), int(cy + ry * 1.5)))

    def save(self, folder):
        os.makedirs(folder, exist_ok=True)
        img = Image.new('RGB', (self.w, self.h))
        mask = Image.new('L', (self.w, self.h))
        px = img.load(); mk = mask.load()
        for y in range(self.h):
            for x in range(self.w):
                c = COLOR[self.m[y][x]]
                px[x, y] = (int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16))
                mk[x, y] = 255 if self.a[y][x] else 0
        img.save(os.path.join(folder, 'terrain.png'), optimize=True)
        mask.save(os.path.join(folder, 'anchor.png'), optimize=True)

    def preview(self, path, scale=4):
        img = Image.new('RGB', (self.w, self.h))
        px = img.load()
        for y in range(self.h):
            for x in range(self.w):
                c = COLOR[self.m[y][x]]
                r, g, b = int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16)
                if self.m[y][x] == 'air': r, g, b = 200, 225, 240
                if self.a[y][x]: r, g, b = int(r * 0.8), int(g * 0.8), int(b * 0.8)
                px[x, y] = (r, g, b)
        img.resize((self.w * scale, self.h * scale), Image.NEAREST).save(path)
