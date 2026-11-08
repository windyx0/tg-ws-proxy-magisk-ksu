import subprocess

cmd = "find /data/user/0/com.radolyn.ayugram/ -type f | xargs grep -s 'Plugin metadata' 2>/dev/null"
res = subprocess.run(['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', cmd], capture_output=True, text=True, errors="replace")
with open("find_type_f.txt", "w", encoding="utf-8") as f:
    f.write(res.stdout)
print("Saved find_type_f.txt, length:", len(res.stdout))
