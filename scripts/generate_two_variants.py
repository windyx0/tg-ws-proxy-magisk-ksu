import numpy as np
from PIL import Image, ImageDraw
import scipy.ndimage as ndimage

# 1. Load user reference to extract the exact 12-lobed scalloped shape
ref_path = r"C:/Users/ALEX/.gemini/antigravity/brain/822df415-3fca-470a-a9ce-5e9ce13c873b/.user_uploaded/media_1794151299705_1593f9f6.png"
ref_im = Image.open(ref_path).convert("RGBA")
ref_arr = np.array(ref_im)

alpha = ref_arr[:, :, 3]
is_outer_lavender = (ref_arr[:, :, 0] > 200) & (ref_arr[:, :, 1] > 200) & (ref_arr[:, :, 2] > 230)
scallop_mask = (alpha > 50) & (~is_outer_lavender)
filled_scallop = ndimage.binary_fill_holes(scallop_mask).astype(np.uint8) * 255
scallop_mask_img = Image.fromarray(filled_scallop)

COLOR_OUTER = (30, 34, 42, 255)       # Graphite slate #1E222A
COLOR_SCALLOP = (0, 163, 224, 255)    # Telegram Cyan #00A3E0
COLOR_GLYPH = (255, 255, 255, 255)    # White
COLOR_ACCENT = (112, 210, 255, 255)   # Light cyan for wing fold

SCALE = 2
W, H = 1024 * SCALE, 1024 * SCALE

def make_base():
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(out)
    cx, cy = W // 2, H // 2
    outer_radius = int(470 * SCALE)
    draw.ellipse([cx - outer_radius, cy - outer_radius, cx + outer_radius, cy + outer_radius], fill=COLOR_OUTER)
    scallop_hi = scallop_mask_img.resize((W, H), Image.Resampling.LANCZOS)
    scallop_color = Image.new("RGBA", (W, H), COLOR_SCALLOP)
    out.paste(scallop_color, (0, 0), mask=scallop_hi)
    return out, draw, cx, cy

# Variant 1: Pure Outline Paper Plane (thick rounded strokes matching reference house)
v1, draw1, cx, cy = make_base()
stroke_w = int(44 * SCALE)

def draw_thick_line(draw, p1, p2, width, color):
    draw.line([p1, p2], fill=color, width=width)
    r = width // 2
    draw.ellipse([p1[0] - r, p1[1] - r, p1[0] + r, p1[1] + r], fill=color)
    draw.ellipse([p2[0] - r, p2[1] - r, p2[0] + r, p2[1] + r], fill=color)

nose = (cx + int(180 * SCALE), cy - int(170 * SCALE))
left_wing = (cx - int(210 * SCALE), cy - int(10 * SCALE))
keel = (cx - int(25 * SCALE), cy + int(210 * SCALE))
notch = (cx - int(45 * SCALE), cy + int(45 * SCALE))

draw_thick_line(draw1, left_wing, nose, stroke_w, COLOR_GLYPH)
draw_thick_line(draw1, nose, keel, stroke_w, COLOR_GLYPH)
draw_thick_line(draw1, keel, notch, stroke_w, COLOR_GLYPH)
draw_thick_line(draw1, notch, left_wing, stroke_w, COLOR_GLYPH)
fold_target = (cx + int(85 * SCALE), cy - int(85 * SCALE))
draw_thick_line(draw1, notch, fold_target, stroke_w, COLOR_GLYPH)

v1_1024 = v1.resize((1024, 1024), Image.Resampling.LANCZOS)
v1_1024.save(r"C:\Users\ALEX\.gemini\antigravity\brain\822df415-3fca-470a-a9ce-5e9ce13c873b\icon_variant_outline.png", "PNG")

# Variant 2: Solid Filled Silhouette Telegram Plane (clean Material You silhouette with rounded corners)
v2, draw2, cx, cy = make_base()
# Official Telegram plane scaled & centered
# Main wing polygon:
p_nose = (cx + int(195 * SCALE), cy - int(185 * SCALE))
p_left = (cx - int(215 * SCALE), cy - int(15 * SCALE))
p_keel = (cx - int(30 * SCALE), cy + int(215 * SCALE))
p_notch = (cx - int(50 * SCALE), cy + int(45 * SCALE))
p_fold_tip = (cx + int(95 * SCALE), cy - int(95 * SCALE))

# Draw solid smooth plane with rounded joins
draw2.polygon([p_nose, p_left, p_notch, p_nose], fill=COLOR_GLYPH)
draw2.polygon([p_nose, p_notch, p_keel, p_nose], fill=(240, 248, 255, 255))
# Small wing shadow flap (underwing):
draw2.polygon([p_notch, p_left, (p_left[0] + int(70*SCALE), p_left[1] + int(40*SCALE)), p_notch], fill=COLOR_ACCENT)

# Round the corners using thick strokes on borders
draw_thick_line(draw2, p_left, p_nose, int(18*SCALE), COLOR_GLYPH)
draw_thick_line(draw2, p_nose, p_keel, int(18*SCALE), COLOR_GLYPH)
draw_thick_line(draw2, p_keel, p_notch, int(18*SCALE), COLOR_GLYPH)
draw_thick_line(draw2, p_notch, p_left, int(18*SCALE), COLOR_GLYPH)

v2_1024 = v2.resize((1024, 1024), Image.Resampling.LANCZOS)
v2_1024.save(r"C:\Users\ALEX\.gemini\antigravity\brain\822df415-3fca-470a-a9ce-5e9ce13c873b\icon_variant_filled.png", "PNG")

print("Both variants generated successfully!")
