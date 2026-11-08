import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'head -n 25 /data/user/0/com.radolyn.ayugram/files/plugins/Fork_Round_Videos_Configurator.py']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
print(res.stdout)
