#!/usr/bin/env python3
"""
Render corpus/replay draws (and current template variants) to PNG contact sheets.

The promotion workflow requires eyeballing draws before they become template
variants (one malformed draw widens a sigil's accept region for good). This renders
them without any game launch or third-party deps — pure-stdlib PNG writer.

  python scripts/render_draws.py water --corpus run/logs/corpus_replay.jsonl --drawer sopas
  python scripts/render_draws.py water --templates          # render the shipped variants instead
  # output: run/render/<label>_draws.png (+ stdout legend mapping cell # -> record)

Each cell is one draw, bbox-normalized, strokes colored in drawing order
(black, red, blue, green, orange, purple, ...). The legend prints per cell: the
record's drawer, what the recognizer said, and OK/WRONG/UNK so misrecognized
candidates are easy to find on the sheet.
"""
import argparse
import json
import os
import struct
import zlib

CORPUS = "dataset/spell_corpus.jsonl"
TEMPLATE_DIR = "src/main/resources/data/witchhatateliermod/spell_templates"
OUT_DIR = "run/render"

CELL = 104          # drawable cell size (px)
PAD = 10            # inner margin inside a cell
COLS = 8
SEP = 2             # separator thickness between cells
STROKE_COLORS = [(20, 20, 20), (210, 40, 40), (40, 110, 220), (30, 150, 60),
                 (200, 130, 20), (140, 40, 180), (0, 150, 150), (150, 90, 60)]
BG = (255, 255, 255)
SEP_COLOR = (190, 190, 190)


def write_png(path, w, h, rgb):
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    raw = b"".join(b"\x00" + bytes(rgb[y * w * 3:(y + 1) * w * 3]) for y in range(h))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 6))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


class Sheet:
    def __init__(self, cells):
        self.cols = min(COLS, max(1, cells))
        self.rows = (cells + self.cols - 1) // self.cols
        self.w = self.cols * CELL + (self.cols + 1) * SEP
        self.h = self.rows * CELL + (self.rows + 1) * SEP
        self.px = bytearray(self.w * self.h * 3)
        for i in range(0, len(self.px), 3):
            self.px[i:i + 3] = bytes(SEP_COLOR)
        for r in range(self.rows):
            for c in range(self.cols):
                self.fill_cell(r, c, BG)

    def cell_origin(self, idx):
        r, c = divmod(idx, self.cols)
        return (SEP + r * (CELL + SEP), SEP + c * (CELL + SEP))

    def fill_cell(self, r, c, color):
        y0 = SEP + r * (CELL + SEP)
        x0 = SEP + c * (CELL + SEP)
        for y in range(y0, y0 + CELL):
            base = (y * self.w + x0) * 3
            for x in range(CELL):
                self.px[base + x * 3: base + x * 3 + 3] = bytes(color)

    def set(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 3
            self.px[i:i + 3] = bytes(color)

    def dot(self, x, y, color):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                self.set(x + dx, y + dy, color)

    def draw_record(self, idx, strokes):
        """strokes: list of list of (x, y) in any coordinate frame."""
        y0, x0 = self.cell_origin(idx)
        pts = [(p[0], p[1]) for s in strokes for p in s]
        if not pts:
            return
        minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
        miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
        span = max(maxx - minx, maxy - miny) or 1.0
        scale = (CELL - 2 * PAD) / span
        # center the bbox in the cell
        offx = x0 + PAD + ((CELL - 2 * PAD) - (maxx - minx) * scale) / 2
        offy = y0 + PAD + ((CELL - 2 * PAD) - (maxy - miny) * scale) / 2

        for si, stroke in enumerate(strokes):
            color = STROKE_COLORS[si % len(STROKE_COLORS)]
            sp = [(offx + (p[0] - minx) * scale, offy + (p[1] - miny) * scale) for p in stroke]
            if len(sp) == 1:
                self.dot(int(sp[0][0]), int(sp[0][1]), color)
                continue
            for (ax, ay), (bx, by) in zip(sp, sp[1:]):
                n = max(1, int(max(abs(bx - ax), abs(by - ay))))
                for k in range(n + 1):
                    t = k / n
                    self.set(int(ax + (bx - ax) * t), int(ay + (by - ay) * t), color)


def strokes_from_raw(raw_strokes):
    return [[(float(p["x"]), float(p["y"])) for p in s] for s in raw_strokes]


def strokes_from_template_points(points):
    by_stroke = {}
    for p in points:
        by_stroke.setdefault(int(p.get("stroke_id", 0)), []).append(
            (float(p["x"]), float(p["y"])))
    return [by_stroke[k] for k in sorted(by_stroke)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("label")
    ap.add_argument("--corpus", default=CORPUS)
    ap.add_argument("--drawer", default=None)
    ap.add_argument("--templates", action="store_true",
                    help="render the shipped template variants for the label instead of draws")
    ap.add_argument("--out", default=OUT_DIR)
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    if args.templates:
        path = os.path.join(TEMPLATE_DIR, args.label + ".json")
        tpl = json.load(open(path, encoding="utf-8"))
        items = [(v.get("name", f"variant_{i}"), strokes_from_template_points(v["points"]))
                 for i, v in enumerate(tpl.get("variants", []))]
        out = os.path.join(args.out, f"{args.label}_templates.png")
        sheet = Sheet(len(items))
        print(f"{args.label}: {len(items)} template variant(s)")
        for i, (name, strokes) in enumerate(items):
            sheet.draw_record(i, strokes)
            print(f"  cell {i:>2} = {name}  ({len(strokes)} stroke(s))")
    else:
        recs = [json.loads(l) for l in open(args.corpus, encoding="utf-8") if l.strip()]
        recs = [r for r in recs if r.get("intended") == args.label]
        if args.drawer:
            recs = [r for r in recs if r.get("player") == args.drawer]
        out = os.path.join(args.out, f"{args.label}_draws.png")
        sheet = Sheet(len(recs))
        print(f"{args.label}: {len(recs)} draw(s)"
              + (f" from drawer '{args.drawer}'" if args.drawer else ""))
        for i, r in enumerate(recs):
            sheet.draw_record(i, strokes_from_raw(r.get("rawStrokes", [])))
            res = (r.get("result") or {}).get("spell", "?")
            mark = "OK " if res == args.label else ("UNK" if res == "unknown" else "WRONG")
            print(f"  cell {i:>2} = {r.get('player', '?'):<10} -> {res:<12} {mark}")

    if not (args.templates and not items) :
        write_png(out, sheet.w, sheet.h, sheet.px)
        print(f"wrote {out}  ({sheet.w}x{sheet.h}, {sheet.cols} per row)")


if __name__ == "__main__":
    main()
