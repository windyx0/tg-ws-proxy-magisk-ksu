import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'cat /data/user/0/com.radolyn.ayugram/files/plugins/scam_marker.py']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
lines = res.stdout.splitlines()
for i in range(15, min(65, len(lines))):
    print(f"{i+1}: {lines[i]}")
