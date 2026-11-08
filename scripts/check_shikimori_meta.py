import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -E "^__[a-zA-Z0-9_]+__" /data/user/0/com.radolyn.ayugram/files/plugins/shikimori_search.py']
res = subprocess.run(cmd, capture_output=True, text=True)
print("Shikimori meta:\n", res.stdout)
