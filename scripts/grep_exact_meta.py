import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -rn "Plugin metadata" /data/user/0/com.radolyn.ayugram/ 2>/dev/null']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
with open("ayugram_grep_meta.txt", "w", encoding="utf-8") as f:
    f.write(res.stdout)

count = 0
with open("ayugram_grep_meta.txt", "r", encoding="utf-8") as f:
    for line in f:
        if not 'cache4.db' in line and not 'file_to_path' in line:
            print(line.strip()[:200])
            count += 1
            if count > 20: break
