import subprocess

device = "192.168.31.47:40543"
content = '{"backend": "tgbypass", "backend_idx": 0, "timestamp": 1794156000000, "source": "tgbypass_app"}'

with open("valid_sync.json", "w", encoding="utf-8") as f:
    f.write(content)

subprocess.run(["adb", "-s", device, "push", "valid_sync.json", "/data/local/tmp/tgbypass_sync.json"])
subprocess.run(["adb", "-s", device, "shell", "su", "-mm", "-c", "cp /data/local/tmp/tgbypass_sync.json /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json && chown 10334:10334 /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json && chmod 666 /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json"])

res = subprocess.run(["adb", "-s", device, "shell", "su", "-mm", "-c", "cat /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json"], capture_output=True, text=True)
print("Sync JSON:", res.stdout)
