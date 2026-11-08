import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -rn "Plugin metadata" /data/user/0/com.radolyn.ayugram/files/plugins/ElyxPlugins/kangel_plugins_manager/']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
with open("kpm_matches.txt", "w", encoding="utf-8") as f:
    f.write(res.stdout)
print("Done writing kpm_matches.txt")
