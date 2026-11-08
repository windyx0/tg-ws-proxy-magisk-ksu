import os
from PIL import Image, ImageDraw, ImageFilter
import numpy as np
from scipy.ndimage import binary_fill_holes

def generate_icons():
    ref_path = r'C:\Users\ALEX\.gemini\antigravity\brain\822df415-3fca-470a-a9ce-5e9ce13c873b\.user_uploaded\media_1794151299705_1593f9f6.png'
    src = Image.open(ref_path).convert('RGBA')
    arr = np.array(src)

    # 1. Circle & Flower badge masks from reference
    alpha = arr[:, :, 3]
    circle_mask = (alpha > 128)
    diff_circle = np.abs(arr[:, :, :3] - np.array([234, 221, 255])).sum(axis=2)
    badge_raw = (alpha > 128) & (diff_circle > 25)
    badge_filled = binary_fill_holes(badge_raw)

    # Material 3 Expressive Telegram Colors:
    # Outer circle: Pastel Sky/Cyan Container (RGB: 214, 235, 255) #D6EBFF
    # Flower badge: Telegram Deep Royal Blue (RGB: 0, 136, 204) #0088CC
    base = np.zeros_like(arr)
    base[circle_mask] = [214, 235, 255, 255]
    base[badge_filled] = [0, 136, 204, 255]
    base_img = Image.fromarray(base)

    # Draw centered Telegram paper airplane
    high_res = Image.new('RGBA', (2000, 2000), (0, 0, 0, 0))
    h_draw = ImageDraw.Draw(high_res)
    cx4, cy4 = 1000, 1000
    scale4 = 3.25 * 4
    ox4, oy4 = cx4 - 50 * scale4 + 18, cy4 - 50 * scale4 - 10
    def pt4(x, y): return (ox4 + x * scale4, oy4 + y * scale4)

    poly_main4 = [
        pt4(84, 25),
        pt4(22, 50),
        pt4(44, 58),
        pt4(69, 39),
        pt4(50, 61),
        pt4(49, 75),
        pt4(58, 67),
        pt4(74, 77),
    ]
    h_draw.polygon(poly_main4, fill=(255, 255, 255, 255))
    poly_shade4 = [
        pt4(44, 58),
        pt4(49, 75),
        pt4(56, 64)
    ]
    h_draw.polygon(poly_shade4, fill=(160, 222, 255, 255))
    h_draw.line(poly_main4 + [poly_main4[0]], fill=(255, 255, 255, 255), width=16, joint='round')

    plane_small = high_res.resize((500, 500), Image.Resampling.LANCZOS)
    base_img.alpha_composite(plane_small)

    # Save master icon 512x512
    master_512 = base_img.resize((512, 512), Image.Resampling.LANCZOS)
    master_path = r'C:\Users\ALEX\.gemini\antigravity\brain\822df415-3fca-470a-a9ce-5e9ce13c873b\tgbypass_md3_master.png'
    master_512.save(master_path)
    print(f'Saved master icon to {master_path}')

    # Save to mipmaps in upstream/tgbypass/src/main/res/
    res_dir = r'D:\Downloads\tg-ws-proxy\upstream\tgbypass\src\main\res'
    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    # For adaptive foreground: Android requires 108dp (108*1=108, 108*1.5=162, 108*2=216, 108*3=324, 108*4=432)
    # The visible area is central 66% (72dp)
    fg_densities = {
        'mipmap-mdpi': 108,
        'mipmap-hdpi': 162,
        'mipmap-xhdpi': 216,
        'mipmap-xxhdpi': 324,
        'mipmap-xxxhdpi': 432,
    }

    for folder, size in densities.items():
        target_dir = os.path.join(res_dir, folder)
        os.makedirs(target_dir, exist_ok=True)
        img_resized = master_512.resize((size, size), Image.Resampling.LANCZOS)
        img_resized.save(os.path.join(target_dir, 'ic_launcher.png'))
        img_resized.save(os.path.join(target_dir, 'ic_launcher_round.png'))
        img_resized.save(os.path.join(target_dir, 'ic_launcher_tgwsproxy_v2.png'))
        img_resized.save(os.path.join(target_dir, 'ic_launcher_tgwsproxy_round_v2.png'))

    # Generate adaptive foreground with safe margin (badge inside central 72dp)
    for folder, size in fg_densities.items():
        target_dir = os.path.join(res_dir, folder)
        fg_canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        # Central size is 72/108 = 0.666 of canvas
        inner_size = int(size * 0.72)
        inner_img = master_512.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
        offset = (size - inner_size) // 2
        fg_canvas.paste(inner_img, (offset, offset), inner_img)
        fg_canvas.save(os.path.join(target_dir, 'ic_launcher_foreground.png'))

    print('Successfully generated all mipmap icons for TgBypass!')

if __name__ == '__main__':
    generate_icons()
