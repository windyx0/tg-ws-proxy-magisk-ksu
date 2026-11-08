import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -rn "Plugin metadata must contain" /data/user/0/com.radolyn.ayugram/']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
with open("find_plugin_error_out.txt", "w", encoding="utf-8") as f:
    f.write(res.stdout)
    if hasattr(res, 'stdout') and not res.stdout.strip():
        f.write(res2.stdout)
print("Saved to find_plugin_error_out.txt")
