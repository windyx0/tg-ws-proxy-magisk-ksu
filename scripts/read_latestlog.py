import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'cat /data/user/0/com.radolyn.ayugram/files/kpm/latestlog.txt | grep -i "tg_ws" -B 5 -A 10']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
print("Latestlog tg_ws matches:\n", res.stdout)
