import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -rn "non-empty" /data/user/0/com.radolyn.ayugram/files/ 2>/dev/null | grep -v cache4.db']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
with open("non_empty_matches.txt", "w", encoding="utf-8") as f:
    f.write(res.stdout)
print("Saved non_empty_matches.txt")
