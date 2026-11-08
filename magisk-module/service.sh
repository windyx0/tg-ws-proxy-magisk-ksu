#!/system/bin/sh
# ==============================================================================
# TgBypass All-In-One - Magisk / KernelSU / APatch Late-Start Service
# Executed automatically after Android system boot completion
# ==============================================================================

MODDIR=${0%/*}
CONF_DIR="/data/adb/tg-ws-proxy"
mkdir -p "$CONF_DIR" 2>/dev/null

# Wait until Android has fully booted
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

# Short delay to allow mobile/Wi-Fi network stacks and package manager to settle
sleep 4

# 1. Apply kernel network acceleration tweaks (BBR, FQ, TFO, 16MB buffers, QoS)
if [ -f "$MODDIR/scripts/net_accelerator.sh" ]; then
    sh "$MODDIR/scripts/net_accelerator.sh" >/dev/null 2>&1
fi

# 2. Check and ensure Vector module is enabled & scoped (excluding AyuGram which uses bridge plugin)
if [ -x /data/adb/lspd/cli ]; then
    /data/adb/lspd/cli modules enable com.windyx0.tgbypass >/dev/null 2>&1
    /data/adb/lspd/cli scope add com.windyx0.tgbypass org.telegram.messenger/0 org.telegram.messenger.web/0 app.exteragram.messenger/0 org.telegram.plus/0 org.thunderdog.challegram/0 >/dev/null 2>&1
    /data/adb/lspd/cli scope rm com.windyx0.tgbypass com.radolyn.ayugram/0 >/dev/null 2>&1
fi

# Ensure AyuGram Bridge Plugin is installed if AyuGram exists
if [ -d /data/data/com.radolyn.ayugram/files/plugins ] && [ -f "$MODDIR/scripts/tg_ws_vector_bridge.py" ]; then
    UID_GID=$(stat -c %u:%g /data/data/com.radolyn.ayugram 2>/dev/null || echo "10334:10334")
    TARGET_DIR="/data/data/com.radolyn.ayugram/files/plugins"
    PREFS="/data/data/com.radolyn.ayugram/shared_prefs/plugin_settings.xml"
    cp "$MODDIR/scripts/tg_ws_vector_bridge.py" "$TARGET_DIR/tg_ws_vector_bridge.py"
    cp "$MODDIR/scripts/tg_ws_vector_bridge.py" "$TARGET_DIR/.temp_tg_ws_vector_bridge.plugin"
    chown $UID_GID "$TARGET_DIR/tg_ws_vector_bridge.py" "$TARGET_DIR/.temp_tg_ws_vector_bridge.plugin"
    chmod 600 "$TARGET_DIR/tg_ws_vector_bridge.py" "$TARGET_DIR/.temp_tg_ws_vector_bridge.plugin"
    if [ -f "$PREFS" ] && ! grep -q "plugin_enabled_tg_ws_vector_bridge" "$PREFS"; then
        sed -i 's|</map>|    <boolean name="plugin_enabled_tg_ws_vector_bridge" value="true" />\n</map>|' "$PREFS"
        chown $UID_GID "$PREFS"
    fi
fi

# 3. Check Autostart configuration
AUTOSTART="1"
if [ -f "$CONF_DIR/autostart" ]; then
    AUTOSTART=$(cat "$CONF_DIR/autostart" 2>/dev/null)
else
    echo "1" > "$CONF_DIR/autostart"
fi

if [ "$AUTOSTART" = "1" ]; then
    # Start proxy in silent module mode
    if [ -f "$MODDIR/scripts/proxy_ctl.sh" ]; then
        sh "$MODDIR/scripts/proxy_ctl.sh" start >/dev/null 2>&1
    fi
fi

# 4. Automatically suppress notifications
if [ -f "$MODDIR/scripts/proxy_ctl.sh" ]; then
    sh "$MODDIR/scripts/proxy_ctl.sh" kill-notification >/dev/null 2>&1
fi

# 5. Background watchdog to keep connection alive if autostart is on
(
    while true; do
        sleep 60
        if [ -f "$CONF_DIR/autostart" ] && [ "$(cat "$CONF_DIR/autostart")" = "1" ]; then
            PORT=1444
            [ -f "$CONF_DIR/port" ] && PORT=$(cat "$CONF_DIR/port" 2>/dev/null)
            hex_port=$(printf '%04X' "$PORT")
            if ! grep -q ":$hex_port " /proc/net/tcp 2>/dev/null; then
                # Proxy died or was killed, restart it silently
                sh "$MODDIR/scripts/proxy_ctl.sh" start >/dev/null 2>&1
            fi
        fi
    done
) &

exit 0
