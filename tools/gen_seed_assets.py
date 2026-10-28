#!/usr/bin/env python3
"""
生成种子素材（模板封面 / 预置结果图 / 演示用示例照片）。

所有图片均由本脚本程序化生成，不使用任何第三方素材，故无版权问题。
R-13 要求说明素材来源：来源即本文件，重新生成命令见 README「素材准备」。

用法：
    python tools/gen_seed_assets.py

输出：
    seed/covers/{templateId}.jpg    模板封面（列表卡片 / 详情页示例图）
    seed/results/{templateId}.jpg   预置结果图（模拟生成结束时复制到 data/results）
    seed/samples/sample-{1,2,3}.jpg 演示时可直接上传的示例照片
"""

from __future__ import annotations

import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "seed")

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
]

# (templateId, 标题, 副标题, 主色, 副色, 点缀色, 图形母题)
TEMPLATES = [
    ("demo-postcard",    "旅行明信片",   "TRAVEL POSTCARD",   (26, 54, 84),   (226, 170, 104), (255, 246, 232), "peaks"),
    ("oil-portrait",     "油画肖像",     "OIL PORTRAIT",      (54, 30, 26),   (172, 112, 58),  (246, 231, 205), "rings"),
    ("watercolor-notes", "水彩手账",     "WATERCOLOR NOTES",  (104, 148, 158), (226, 186, 202), (252, 250, 246), "blobs"),
    ("pet-avatar",       "萌宠头像",     "PET AVATAR",        (28, 42, 36),   (146, 192, 142), (246, 250, 242), "frame"),
    ("pet-sticker",      "宠物卡通贴纸", "PET STICKER",       (238, 172, 84),  (52, 126, 172),  (255, 253, 248), "tiles"),
    ("wedding-invite",   "婚礼请柬",     "WEDDING INVITE",    (82, 70, 60),   (208, 186, 158), (252, 249, 242), "arch"),
    ("wedding-portrait", "婚纱写真",     "WEDDING PORTRAIT",  (44, 54, 70),   (194, 172, 144), (250, 248, 244), "rays"),
    ("santa-postcard",   "圣诞明信片",   "SANTA POSTCARD",    (14, 44, 38),   (172, 40, 46),   (248, 238, 224), "star"),
    ("newyear-poster",   "新年海报",     "NEW YEAR POSTER",   (116, 22, 26),  (230, 172, 68),  (255, 248, 234), "bands"),
]

SAMPLES = [
    ("sample-1", (116, 158, 188), (230, 222, 198), (58, 106, 116), "peaks"),
    ("sample-2", (196, 166, 136), (250, 236, 210), (146, 88, 68),   "blobs"),
    ("sample-3", (86, 106, 86),  (214, 224, 194), (58, 74, 50),    "bands"),
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


# ---------------------------------------------------------------------------
# 背景
# ---------------------------------------------------------------------------
def diagonal_gradient(w: int, h: int, c1, c2, accent, angle_deg: float = 38.0) -> Image.Image:
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    a = math.radians(angle_deg)
    t = xx * math.cos(a) + yy * math.sin(a)
    t = (t - t.min()) / max(t.max() - t.min(), 1e-6)
    t = t ** 0.9

    left = np.array(c1, dtype=np.float32)
    right = np.array(c2, dtype=np.float32)
    rgb = left[None, None, :] * (1 - t)[..., None] + right[None, None, :] * t[..., None]

    d = np.sqrt((xx / w) ** 2 + (yy / h) ** 2) / math.sqrt(2)
    glow = np.clip(1 - d * 1.7, 0, 1) ** 2 * 0.35
    rgb = rgb * (1 - glow[..., None]) + np.array(accent, dtype=np.float32)[None, None, :] * glow[..., None]

    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB")


def add_vignette(img: Image.Image, strength: float = 0.34) -> Image.Image:
    w, h = img.size
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((xx / w) - 0.5) ** 2 + ((yy / h) - 0.5) ** 2) / 0.7071
    mask = np.clip(1 - strength * d ** 2.0, 0, 1)[..., None]
    return Image.fromarray((np.asarray(img, dtype=np.float32) * mask).astype(np.uint8), "RGB")


def add_grain(img: Image.Image, amount: float = 3.2) -> Image.Image:
    arr = np.asarray(img, dtype=np.float32)
    noise = np.random.normal(0, amount, arr.shape[:2])[..., None]
    return Image.fromarray(np.clip(arr + noise, 0, 255).astype(np.uint8), "RGB")


# ---------------------------------------------------------------------------
# 图形母题：每个模板一个，避免九张封面长得一样
# ---------------------------------------------------------------------------
def motif_peaks(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    r = min(w, h) * 0.15
    cx, cy = w * 0.74, h * 0.34
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=accent + (78,))
    for i, alpha in enumerate((62, 92, 128)):
        base = h * (0.58 + i * 0.11)
        pts = [(-w * 0.15, h + 20)]
        for k in range(8):
            x = -w * 0.15 + k * (w * 1.3 / 7)
            y = base + math.sin(k * 1.6 + i * 2.1) * min(w, h) * 0.055 + rng.uniform(-2, 2) * min(w, h) * 0.012
            pts.append((x, y))
        pts.append((w * 1.15, h + 20))
        d.polygon(pts, fill=accent + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(min(w, h) * 0.004))


def motif_rings(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = w * 0.5, h * 0.44
    for i, k in enumerate(range(11)):
        r = min(w, h) * (0.06 + k * 0.042)
        width = max(2, int(min(w, h) * (0.016 - k * 0.0009)))
        off = math.sin(k * 0.9) * min(w, h) * 0.012
        d.arc([cx - r + off, cy - r, cx + r + off, cy + r], start=200 + k * 6, end=520 - k * 4,
              fill=accent + (int(120 - k * 7),), width=width)
    return layer.filter(ImageFilter.GaussianBlur(min(w, h) * 0.003))


def motif_blobs(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(9):
        r = rng.randint(int(min(w, h) * 0.10), int(min(w, h) * 0.30))
        cx = rng.randint(int(-r * 0.3), int(w + r * 0.3))
        cy = rng.randint(int(-r * 0.3), int(h * 0.75))
        d.ellipse([cx - r, cy - r * rng.uniform(0.6, 1.2), cx + r, cy + r * rng.uniform(0.6, 1.2)],
                  fill=accent + (rng.randint(34, 72),))
    return layer.filter(ImageFilter.GaussianBlur(min(w, h) * 0.055))


def motif_frame(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = min(w, h) * 0.52
    cx, cy = w * 0.5, h * 0.40
    d.rounded_rectangle([cx - box / 2, cy - box / 2, cx + box / 2, cy + box / 2], radius=box * 0.30,
                        outline=accent + (110,), width=max(3, int(min(w, h) * 0.012)))
    d.ellipse([cx - box * 0.26, cy - box * 0.30, cx + box * 0.26, cy + box * 0.22], outline=accent + (90,),
              width=max(2, int(min(w, h) * 0.008)))
    d.arc([cx - box * 0.34, cy + box * 0.02, cx + box * 0.34, cy + box * 0.46], start=180, end=360,
          fill=accent + (90,), width=max(2, int(min(w, h) * 0.008)))
    return layer


def motif_tiles(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cols, rows = 2, 3
    gw, gh = w * 0.30, h * 0.17
    gx, gy = w * 0.50 - gw, h * 0.34 - gh * 1.5
    for r in range(rows):
        for c in range(cols):
            x = gx + c * (gw + w * 0.07)
            y = gy + r * (gh + h * 0.035)
            off = math.sin((r * cols + c) * 1.3) * min(w, h) * 0.02
            d.rounded_rectangle([x + off, y, x + gw + off, y + gh], radius=gh * 0.24,
                                fill=accent + (rng.randint(46, 92),),
                                outline=accent + (140,), width=max(2, int(min(w, h) * 0.006)))
    return layer


def motif_arch(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    aw, ah = w * 0.38, h * 0.42
    cx, top = w * 0.5, h * 0.16
    d.rounded_rectangle([cx - aw / 2, top, cx + aw / 2, top + ah], radius=aw / 2,
                        outline=accent + (120,), width=max(3, int(min(w, h) * 0.010)))
    d.rounded_rectangle([cx - aw / 2 + aw * 0.10, top + ah * 0.30, cx + aw / 2 - aw * 0.10, top + ah],
                        radius=aw / 2, outline=accent + (70,), width=max(2, int(min(w, h) * 0.006)))
    d.line([w * 0.32, top + ah * 1.06, w * 0.68, top + ah * 1.06], fill=accent + (110,),
           width=max(2, int(min(w, h) * 0.005)))
    return layer


def motif_rays(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    origin = (-w * 0.15, -h * 0.10)
    for k in range(9):
        a1 = math.radians(18 + k * 7.5)
        a2 = math.radians(18 + k * 7.5 + 3.2)
        L = w + h
        d.polygon([
            origin,
            (origin[0] + math.cos(a1) * L, origin[1] + math.sin(a1) * L),
            (origin[0] + math.cos(a2) * L, origin[1] + math.sin(a2) * L),
        ], fill=accent + (rng.randint(16, 34),))
    return layer.filter(ImageFilter.GaussianBlur(min(w, h) * 0.012))


def motif_star(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def star(cx, cy, r, width, alpha):
        for k in range(6):
            a = math.radians(k * 30)
            d.line([cx - math.cos(a) * r, cy - math.sin(a) * r,
                    cx + math.cos(a) * r, cy + math.sin(a) * r],
                   fill=accent + (alpha,), width=width)

    star(w * 0.70, h * 0.26, min(w, h) * 0.17, max(2, int(min(w, h) * 0.010)), 120)
    star(w * 0.28, h * 0.44, min(w, h) * 0.10, max(2, int(min(w, h) * 0.007)), 80)
    star(w * 0.80, h * 0.52, min(w, h) * 0.07, max(1, int(min(w, h) * 0.005)), 62)
    return layer


def motif_bands(w, h, accent, rng):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    r = min(w, h) * 0.20
    d.ellipse([w * 0.60 - r, h * 0.26 - r, w * 0.60 + r, h * 0.26 + r], fill=accent + (86,))
    for k in range(4):
        y = h * (0.46 + k * 0.10)
        skew = h * 0.05
        d.polygon([(-20, y), (w * 0.70, y - skew), (w * 0.70, y - skew + h * 0.022), (-20, y + h * 0.022)],
                  fill=accent + (int(96 - k * 18),))
    return layer


MOTIFS = {
    "peaks": motif_peaks,
    "rings": motif_rings,
    "blobs": motif_blobs,
    "frame": motif_frame,
    "tiles": motif_tiles,
    "arch": motif_arch,
    "rays": motif_rays,
    "star": motif_star,
    "bands": motif_bands,
}


# ---------------------------------------------------------------------------
# 文字与合成
# ---------------------------------------------------------------------------
def add_scrim(img: Image.Image) -> Image.Image:
    """底部压暗，保证标题在浅色底上也读得清。"""
    w, h = img.size
    arr = np.asarray(img, dtype=np.float32)
    yy = np.arange(h, dtype=np.float32)[:, None] / h
    k = np.clip((yy - 0.52) / 0.48, 0, 1) ** 1.6 * 0.55
    arr = arr * (1 - k[..., None])
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def draw_label(img: Image.Image, title: str, subtitle: str, accent, scale: float = 1.0) -> Image.Image:
    w, h = img.size
    draw = ImageDraw.Draw(img, "RGBA")

    title_size = int(58 * scale)
    sub_size = int(21 * scale)
    f_title = load_font(title_size)
    f_sub = load_font(sub_size)

    x = int(w * 0.075)
    y = int(h * 0.775)

    draw.text((x, y), title, font=f_title, fill=accent + (255,))
    draw.text((x, y + int(title_size * 1.30)), subtitle, font=f_sub, fill=accent + (185,))

    bar_x = x - int(18 * scale)
    draw.rounded_rectangle([bar_x, y + int(10 * scale), bar_x + int(5 * scale),
                            y + int(title_size * 1.30) + int(sub_size * 1.1)],
                           radius=int(3 * scale), fill=accent + (170,))
    return img


def build(width: int, height: int, title, subtitle, c1, c2, accent, motif: str, seed_value: int) -> Image.Image:
    rng = random.Random(seed_value)
    img = diagonal_gradient(width, height, c1, c2, accent)
    over = MOTIFS[motif](width, height, accent, rng)
    img = Image.alpha_composite(img.convert("RGBA"), over).convert("RGB")
    img = add_vignette(img)
    img = add_scrim(img)
    scale = min(width, height) / 1000.0
    img = draw_label(img, title, subtitle, accent, scale=scale)
    img = add_grain(img)
    # 轻微提饱和与对比，抵消雾化和暗角带来的灰
    img = ImageEnhance.Color(img).enhance(1.22)
    img = ImageEnhance.Contrast(img).enhance(1.08)
    return img


def main() -> None:
    for sub in ("covers", "results", "samples"):
        os.makedirs(os.path.join(SEED, sub), exist_ok=True)

    np.random.seed(20261028)
    random.seed(20261028)

    for i, (tid, title, subtitle, c1, c2, accent, motif) in enumerate(TEMPLATES):
        cover = build(800, 1000, title, subtitle, c1, c2, accent, motif, seed_value=1000 + i)
        cover.save(os.path.join(SEED, "covers", f"{tid}.jpg"), quality=88, optimize=True)

        result = build(1200, 900, title, f"{subtitle} · RESULT", c1, c2, accent, motif, seed_value=2000 + i)
        result.save(os.path.join(SEED, "results", f"{tid}.jpg"), quality=88, optimize=True)

    for j, (name, c1, c2, accent, motif) in enumerate(SAMPLES):
        sample = build(1000, 750, f"示例照片 {j + 1}", "SAMPLE PHOTO", c1, c2, accent, motif, seed_value=3000 + j)
        sample.save(os.path.join(SEED, "samples", f"{name}.jpg"), quality=86, optimize=True)

    total = 0
    for dirpath, _, files in os.walk(SEED):
        for f in sorted(files):
            p = os.path.join(dirpath, f)
            size = os.path.getsize(p)
            total += size
            print(f"{os.path.relpath(p, ROOT):<44} {size / 1024:7.1f} KB")
    print(f"\n共 {total / 1024:.1f} KB")


if __name__ == "__main__":
    main()
