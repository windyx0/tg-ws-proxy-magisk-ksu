import subprocess

cmd = ["adb", "-s", "192.168.31.47:40543", "shell", "su", "-mm", "-c", "grep -rn 'def get_setting' /data/user/0/com.radolyn.ayugram/files/"]
res = subprocess.run(cmd, capture_output=True, text=True)
print("STDOUT:", res.stdout)
print("STDERR:", res.stderr)
