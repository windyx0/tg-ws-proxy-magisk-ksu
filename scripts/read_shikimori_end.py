import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'head -n 35 /data/user/0/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
print("Temp plugin head:\n", res.stdout)
