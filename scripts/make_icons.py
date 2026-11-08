import os
from PIL import Image, ImageDraw

src_path = r"C:\Users\ALEX\.gemini\antigravity\brain\822df415-3fca-470a-a9ce-5e9ce13c873b\icon_variant_outline.png"
res_dir = r"D:\Downloads\tg-ws-proxy\upstream\tgbypass\src\main\res"

im = Image.open(src_path).convert("RGBA")

densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

def make_round(img):
    size = img.size
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    result = img.copy()
    result.putalpha(mask)
    return result

for folder, size in densities.items():
    out_dir = os.path.join(res_dir, folder)
    os.makedirs(out_dir, exist_ok=True)
    
    # Square / standard MD3 squircle icon
    resized = im.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(out_dir, "ic_launcher.png"), "PNG")
    
    # Round icon
    round_img = make_round(resized)
    round_img.save(os.path.join(out_dir, "ic_launcher_round.png"), "PNG")
    
    # Foreground for adaptive icon
    fg_size = int(size * 108 / 48)
    fg = im.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
    fg.save(os.path.join(out_dir, "ic_launcher_foreground.png"), "PNG")

print("All icons successfully generated in pure MD3 outline style!")
