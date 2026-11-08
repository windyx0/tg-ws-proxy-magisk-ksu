import subprocess, sys
sys.stdout.reconfigure(encoding='utf-8')

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'sed -n "1895,1930p" /data/data/com.radolyn.ayugram/files/plugins/music_dl.py']
out = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
print(out)
