import os
from PIL import Image, ImageDraw

src_path = "telegram_3d.png"
res_dir = r"D:\Downloads\tg-ws-proxy\upstream\tgbypass\src\main\res"

im = Image.open(src_path).convert("RGBA")

# Ensure it's centered and nicely padded
bbox = im.getbbox()
im_cropped = im.crop(bbox)

# Create a master square 512x512
master_size = 512
master = Image.new("RGBA", (master_size, master_size), (0, 0, 0, 0))
# Scale cropped 3D icon to ~420x420 to give natural margins
target_icon_size = 420
scaled_icon = im_cropped.resize((target_icon_size, target_icon_size), Image.Resampling.LANCZOS)
offset = (master_size - target_icon_size) // 2
master.paste(scaled_icon, (offset, offset), mask=scaled_icon)

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
    
    # 3D icon
    resized = master.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(out_dir, "ic_launcher.png"), "PNG")
    
    # Round icon
    round_img = make_round(resized)
    round_img.save(os.path.join(out_dir, "ic_launcher_round.png"), "PNG")
    
    # Adaptive icon foreground (108dp, inner 72dp icon)
    fg_size = int(size * 108 / 48)
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    # Icon inside foreground takes ~66% of fg_size
    inner_size = int(fg_size * 0.72)
    inner_icon = master.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
    fg_offset = (fg_size - inner_size) // 2
    fg.paste(inner_icon, (fg_offset, fg_offset), mask=inner_icon)
    fg.save(os.path.join(out_dir, "ic_launcher_foreground.png"), "PNG")

print("All 3D icons successfully generated and saved to all densities!")
