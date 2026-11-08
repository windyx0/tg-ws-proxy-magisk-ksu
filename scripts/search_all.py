import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -r "Plugin metadata" /data/user/0/com.radolyn.ayugram/files/ 2>/dev/null | grep -v cache4.db']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
print("Matches:")
for l in res.stdout.splitlines():
    print(l[:200].encode("ascii", errors="replace").decode())
