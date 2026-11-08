#!/system/bin/sh
# ==============================================================================
# Telegram WS Proxy - Ultra-Fast KernelSU / APatch Action Button
# Instant execution (<200ms): immediately launches TG proxy dialog & tunes kernel
# ==============================================================================

MODDIR="/data/adb/modules/tg_ws_proxy"
CONF_DIR="/data/adb/tg-ws-proxy"
mkdir -p "$CONF_DIR" 2>/dev/null

PORT=1444
[ -f "$CONF_DIR/port" ] && PORT=$(cat "$CONF_DIR/port" 2>/dev/null)

# 1. Instantly resolve the active DD secret
SECRET="dd7ee8902211954d7e8235f108a6313d42"
for app_id in com.windyx0.twgsproxy io.github.regstar2.tgwsproxy; do
    pref_file="/data/user/0/$app_id/shared_prefs/ProxyPrefs.xml"
    if [ -f "$pref_file" ]; then
        raw_sec=$(sed -n 's/.*name="mtproto_secret_v1">\([^<]*\)<.*/\1/p' "$pref_file" 2>/dev/null)
        if [ -n "$raw_sec" ]; then
            SECRET="dd$raw_sec"
            echo "$SECRET" > "$CONF_DIR/secret" 2>/dev/null
            break
        fi
    fi
done
[ -f "$CONF_DIR/secret" ] && SECRET=$(cat "$CONF_DIR/secret" 2>/dev/null)

PROXY_URL="https://t.me/proxy?server=127.0.0.1&port=$PORT&secret=$SECRET"

echo "⚡ Telegram WS Proxy • Instant Action Trigger"
echo "[*] Connecting to 127.0.0.1:$PORT..."

# 2. INSTANT DISPATCH: Fire Telegram intent immediately!
TARGET_PKG=""
for pkg in com.radolyn.ayugram com.exteragram.messenger app.exteragram.messenger org.telegram.messenger org.thunderdog.challegram; do
    if pm path "$pkg" >/dev/null 2>&1; then
        TARGET_PKG="$pkg"
        break
    fi
done

if [ -n "$TARGET_PKG" ]; then
    am start -a android.intent.action.VIEW -d "$PROXY_URL" -p "$TARGET_PKG" >/dev/null 2>&1
    echo "[✓] Launched in $TARGET_PKG!"
else
    am start -a android.intent.action.VIEW -d "$PROXY_URL" >/dev/null 2>&1
    echo "[✓] Launched default Telegram handler!"
fi

# 3. BACKGROUND WORK: Start daemon if offline & apply kernel turbo asynchronously
(
    # Check if proxy listener is active
    hex_port=$(printf '%04X' "$PORT")
    if ! grep -q ":$hex_port " /proc/net/tcp 2>/dev/null; then
        if [ -f "$MODDIR/scripts/proxy_ctl.sh" ]; then
            sh "$MODDIR/scripts/proxy_ctl.sh" start >/dev/null 2>&1
        fi
    fi

    # Apply root network acceleration
    if [ -f "$MODDIR/scripts/net_accelerator.sh" ]; then
        sh "$MODDIR/scripts/net_accelerator.sh" >/dev/null 2>&1
    fi
    
    # Silence notifications
    if [ -f "$MODDIR/scripts/proxy_ctl.sh" ]; then
        sh "$MODDIR/scripts/proxy_ctl.sh" kill-notification >/dev/null 2>&1
    fi
) >/dev/null 2>&1 &

echo "[✓] Proxy & Network Turbo active!"
exit 0
