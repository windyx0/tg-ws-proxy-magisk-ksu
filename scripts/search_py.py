import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -rn "Plugin metadata" /data/user/0/com.radolyn.ayugram/files/plugins/']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
for l in res.stdout.splitlines():
    if "Plugin metadata" in l:
        print(l.encode("ascii", errors="replace").decode())
