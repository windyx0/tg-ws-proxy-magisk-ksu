import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -rn "Plugin metadata must contain" /data/user/0/com.radolyn.ayugram/files/plugins/ElyxPlugins/']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
for line in res.stdout.splitlines():
    if "metadata must contain" in line:
        print(line.encode("ascii", errors="replace").decode())
