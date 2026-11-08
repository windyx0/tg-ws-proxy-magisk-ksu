import subprocess

payload = '{"backend": "zasto_wss_edge", "backend_idx": 1, "timestamp": 1234567, "source": "test"}'
# write to temporary file locally and push
with open("temp_sync.json", "w", encoding="utf-8") as f:
    f.write(payload)

subprocess.run(["adb", "-s", "192.168.31.47:40543", "push", "temp_sync.json", "/data/local/tmp/tgbypass_sync.json"])
subprocess.run(["adb", "-s", "192.168.31.47:40543", "shell", "su", "-mm", "-c", "cp /data/local/tmp/tgbypass_sync.json /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json && chmod 666 /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json"])
res = subprocess.run(["adb", "-s", "192.168.31.47:40543", "shell", "su", "u0_a334", "-c", "cat /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json"], capture_output=True, text=True)
print("Read as u0_a334:", res.stdout)
res2 = subprocess.run(["adb", "-s", "192.168.31.47:40543", "shell", "su", "u0_a334", "-c", "echo '{\"backend\": \"zasto_wss_node\"}' > /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json && cat /data/user/0/com.radolyn.ayugram/files/plugins/tgbypass_sync.json"], capture_output=True, text=True)
print("Write as u0_a334:", res2.stdout)

