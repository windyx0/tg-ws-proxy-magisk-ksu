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

# 2. Dynamic scanner: Auto-check TG clients for plugins vs Safe Mode / Vector
APP_ID="com.windyx0.tgbypass"
if [ -x /data/adb/lspd/cli ]; then
    /data/adb/lspd/cli modules enable "$APP_ID" >/dev/null 2>&1
fi

TG_CANDIDATES="com.radolyn.ayugram app.exteragram.messenger com.exteragram.messenger org.zastogram.messenger.dev org.telegram.messenger org.telegram.messenger.web org.thunderdog.challegram org.telegram.plus tw.nekomimi.nekogram cc.modery.cherrygram nekox.messenger"

for pkg in $TG_CANDIDATES; do
    if pm path "$pkg" >/dev/null 2>&1; then
        DATA_DIR="/data/data/$pkg"
        [ ! -d "$DATA_DIR" ] && DATA_DIR="/data/user/0/$pkg"

        if [ -d "$DATA_DIR/files/plugins" ] || [ -f "$DATA_DIR/files/python_version" ] || [ -f "$DATA_DIR/files/chaquopy" ]; then
            if [ -f "$MODDIR/scripts/tg_ws_vector_bridge.py" ]; then
                P_UID=$(stat -c %u:%g "$DATA_DIR" 2>/dev/null || echo "1000:1000")
                mkdir -p "$DATA_DIR/files/plugins"
                cp "$MODDIR/scripts/tg_ws_vector_bridge.py" "$DATA_DIR/files/plugins/tg_ws_vector_bridge.py"
                cp "$MODDIR/scripts/tg_ws_vector_bridge.py" "$DATA_DIR/files/plugins/.temp_tg_ws_vector_bridge.plugin"
                chown -R $P_UID "$DATA_DIR/files/plugins"
                chmod 600 "$DATA_DIR/files/plugins/tg_ws_vector_bridge.py" "$DATA_DIR/files/plugins/.temp_tg_ws_vector_bridge.plugin"

                PREFS="$DATA_DIR/shared_prefs/plugin_settings.xml"
                if [ -f "$PREFS" ] && ! grep -q "plugin_enabled_tg_ws_vector_bridge" "$PREFS"; then
                    sed -i 's|</map>|    <boolean name="plugin_enabled_tg_ws_vector_bridge" value="true" />\n</map>|' "$PREFS"
                    chown $P_UID "$PREFS"
                fi
            fi

            SAFE_MODE="0"
            PLUGINS_OFF="0"
            ALL_PREFS=$(cat "$DATA_DIR/shared_prefs/exteraconfig.xml" "$DATA_DIR/shared_prefs/ayuconfig.xml" "$DATA_DIR/shared_prefs/mainconfig.xml" 2>/dev/null)
            if echo "$ALL_PREFS" | grep -q 'name="pluginsSafeMode" value="true"'; then
                SAFE_MODE="1"
            fi
            if echo "$ALL_PREFS" | grep -q 'name="pluginsEngine" value="false"'; then
                PLUGINS_OFF="1"
            fi

            if [ "$SAFE_MODE" = "1" ] || [ "$PLUGINS_OFF" = "1" ]; then
                [ -x /data/adb/lspd/cli ] && /data/adb/lspd/cli scope add "$APP_ID" "$pkg/0" >/dev/null 2>&1
            else
                [ -x /data/adb/lspd/cli ] && /data/adb/lspd/cli scope rm "$APP_ID" "$pkg/0" >/dev/null 2>&1
            fi
        else
            [ -x /data/adb/lspd/cli ] && /data/adb/lspd/cli scope add "$APP_ID" "$pkg/0" >/dev/null 2>&1
        fi
    fi
done

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
