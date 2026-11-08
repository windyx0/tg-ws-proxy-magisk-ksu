import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import scipy.ndimage as ndimage

# 1. Load user reference to extract the exact 12-lobed scalloped shape
ref_path = r"C:/Users/ALEX/.gemini/antigravity/brain/822df415-3fca-470a-a9ce-5e9ce13c873b/.user_uploaded/media_1794151299705_1593f9f6.png"
ref_im = Image.open(ref_path).convert("RGBA")
ref_arr = np.array(ref_im)

# Extract scallop mask
alpha = ref_arr[:, :, 3]
is_outer_lavender = (ref_arr[:, :, 0] > 200) & (ref_arr[:, :, 1] > 200) & (ref_arr[:, :, 2] > 230)
scallop_mask = (alpha > 50) & (~is_outer_lavender)
filled_scallop = ndimage.binary_fill_holes(scallop_mask).astype(np.uint8) * 255

# Resize scallop mask to high resolution (1024x1024)
scallop_mask_img = Image.fromarray(filled_scallop, mode='L')
scallop_1024 = scallop_mask_img.resize((1024, 1024), Image.Resampling.LANCZOS)
# Threshold and slight smooth to ensure clean anti-aliasing
scallop_1024_arr = np.array(scallop_1024)

# Colors chosen by user:
# Outer circle: graphite slate #1E222A
COLOR_OUTER = (30, 34, 42, 255)
# Scalloped shape: Telegram Cyan #00A3E0
COLOR_SCALLOP = (0, 163, 224, 255)
# Glyph: Pure White #FFFFFF
COLOR_GLYPH = (255, 255, 255, 255)

# High-res supersampled canvas (2048x2048 for perfect anti-aliasing)
SCALE = 2
W, H = 1024 * SCALE, 1024 * SCALE
out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
draw = ImageDraw.Draw(out)

# 1. Outer circle
cx, cy = W // 2, H // 2
outer_radius = int(470 * SCALE)
draw.ellipse(
    [cx - outer_radius, cy - outer_radius, cx + outer_radius, cy + outer_radius],
    fill=COLOR_OUTER
)

# 2. Scalloped badge
scallop_hi = scallop_mask_img.resize((W, H), Image.Resampling.LANCZOS)
scallop_color = Image.new("RGBA", (W, H), COLOR_SCALLOP)
out.paste(scallop_color, (0, 0), mask=scallop_hi)

# 3. Draw Telegram Paper Airplane Glyph in rounded outline style
# Geometry scaled to fit comfortably inside the 12-lobed container
# Center is cx, cy.
# The stroke width should match the reference house outline: ~40px at 1024, so ~80px at 2048
stroke_w = int(42 * SCALE)

# Points for Telegram Paper Plane outline
# Nose at top-right, left wing, keel bottom, fold notch
# Let's define the points relative to center
nose = (cx + int(185 * SCALE), cy - int(175 * SCALE))
left_wing = (cx - int(210 * SCALE), cy - int(15 * SCALE))
keel = (cx - int(35 * SCALE), cy + int(215 * SCALE))
notch = (cx - int(55 * SCALE), cy + int(45 * SCALE))

# Draw the paper airplane using Pillow Draw with rounded joints
# Outer outline polygon: nose -> left_wing -> keel -> notch -> (connect to nose or wings)
# In Telegram logo:
# Top edge: left_wing to nose
# Right edge: nose to keel
# Keel to notch
# Underwing fold: notch to left_wing

# Draw thick lines with rounded caps/joints
def draw_thick_line(p1, p2, width, color):
    draw.line([p1, p2], fill=color, width=width)
    r = width // 2
    draw.ellipse([p1[0] - r, p1[1] - r, p1[0] + r, p1[1] + r], fill=color)
    draw.ellipse([p2[0] - r, p2[1] - r, p2[0] + r, p2[1] + r], fill=color)

# Outer triangle
draw_thick_line(left_wing, nose, stroke_w, COLOR_GLYPH)
draw_thick_line(nose, keel, stroke_w, COLOR_GLYPH)
draw_thick_line(keel, notch, stroke_w, COLOR_GLYPH)
draw_thick_line(notch, left_wing, stroke_w, COLOR_GLYPH)

# Center fold line: from notch towards nose (giving the classic paper plane fold)
fold_end = (cx + int(90 * SCALE), cy - int(95 * SCALE))
draw_thick_line(notch, fold_end, stroke_w, COLOR_GLYPH)

# Downscale to 1024x1024 with LANCZOS for super crisp anti-aliased output
final_1024 = out.resize((1024, 1024), Image.Resampling.LANCZOS)

out_path = r"C:\Users\ALEX\.gemini\antigravity\brain\822df415-3fca-470a-a9ce-5e9ce13c873b\tgbypass_md3_icon_preview.png"
final_1024.save(out_path, "PNG")
print(f"Icon generated and saved to {out_path}")
