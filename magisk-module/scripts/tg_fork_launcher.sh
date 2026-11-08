#!/system/bin/sh
# ==============================================================================
# Telegram Fork Launcher & Intent Dispatcher
# Supports Official Telegram, exteraGram, AyuGram, Telegram X, Nekogram, etc.
# ==============================================================================

CONF_DIR="/data/adb/tg-ws-proxy"
mkdir -p "$CONF_DIR" 2>/dev/null

KNOWN_FORKS="
app.exteragram.messenger:exteraGram
com.radolyn.ayugram:AyuGram
org.telegram.messenger:Telegram
org.thunderdog.challegram:Telegram X
tw.nekomimi.nekogram:Nekogram
tw.nekomimi.nekogramx:Nekogram X
cc.modery.cherrygram:Cherrygram
org.telegram.plus:Plus Messenger
io.github.nextalone.nagram:Nagram
com.iMe.android:iMe Messenger
app.nicegram:Nicegram
ir.ilmili.telegraph:Graph Messenger
org.telegram.bgram:BGram
org.telegram.mdgram:MDGram
"

# Get list of installed forks
get_installed_forks() {
    local installed=""
    for item in $KNOWN_FORKS; do
        [ -z "$item" ] && continue
        local pkg="${item%%:*}"
        local name="${item##*:}"
        if pm path "$pkg" >/dev/null 2>&1; then
            installed="$installed $pkg:$name"
        fi
    done
    echo "$installed"
}

# Read settings or defaults
PORT=1444
[ -f "$CONF_DIR/port" ] && PORT=$(cat "$CONF_DIR/port" 2>/dev/null)

REAL_SECRET=""
for app_id in com.windyx0.twgsproxy io.github.regstar2.tgwsproxy; do
    pref_file="/data/user/0/$app_id/shared_prefs/ProxyPrefs.xml"
    if [ -f "$pref_file" ]; then
        raw_sec=$(sed -n 's/.*name="mtproto_secret_v1">\([^<]*\)<.*/\1/p' "$pref_file" 2>/dev/null)
        if [ -n "$raw_sec" ]; then
            REAL_SECRET="dd$raw_sec"
            echo "$REAL_SECRET" > "$CONF_DIR/secret" 2>/dev/null
            break
        fi
    fi
done

SECRET="dd7ee8902211954d7e8235f108a6313d42"
[ -n "$REAL_SECRET" ] && SECRET="$REAL_SECRET"
[ -f "$CONF_DIR/secret" ] && SECRET=$(cat "$CONF_DIR/secret" 2>/dev/null)

TARGET_SETTING="auto"
[ -f "$CONF_DIR/target_tg" ] && TARGET_SETTING=$(cat "$CONF_DIR/target_tg" 2>/dev/null)

# Override from command line argument if supplied
if [ -n "$1" ]; then
    TARGET_SETTING="$1"
fi

PROXY_URL="tg://proxy?server=127.0.0.1&port=$PORT&secret=$SECRET"

case "$TARGET_SETTING" in
    "list")
        echo "=== Installed Telegram Apps ==="
        INSTALLED=$(get_installed_forks)
        if [ -z "$INSTALLED" ]; then
            echo "No Telegram apps detected via package manager."
        else
            for entry in $INSTALLED; do
                local_pkg="${entry%%:*}"
                local_name="${entry##*:}"
                echo " - $local_name ($local_pkg)"
            done
        fi
        exit 0
        ;;
    "all")
        echo "[*] Applying proxy to ALL installed Telegram forks..."
        INSTALLED=$(get_installed_forks)
        applied=0
        for entry in $INSTALLED; do
            local_pkg="${entry%%:*}"
            local_name="${entry##*:}"
            echo " -> Sending configuration to $local_name ($local_pkg)..."
            am start -a android.intent.action.VIEW -d "$PROXY_URL" -p "$local_pkg" >/dev/null 2>&1
            applied=$((applied + 1))
            sleep 1
        done
        echo "[✓] Configured in $applied client(s)."
        exit 0
        ;;
    "auto"|"")
        INSTALLED=$(get_installed_forks)
        if [ -z "$INSTALLED" ]; then
            echo "[!] No known Telegram fork found, opening generic tg:// handler..."
            am start -a android.intent.action.VIEW -d "$PROXY_URL" >/dev/null 2>&1
            exit 0
        fi
        # Select first installed fork (exteraGram and AyuGram prioritized at top of list)
        first_entry=$(echo "$INSTALLED" | awk '{print $1}')
        TARGET_PKG="${first_entry%%:*}"
        TARGET_NAME="${first_entry##*:}"
        echo "[✓] Detected Telegram client: $TARGET_NAME ($TARGET_PKG)"
        am start -a android.intent.action.VIEW -d "$PROXY_URL" -p "$TARGET_PKG" >/dev/null 2>&1
        echo "[✓] Proxy applied in $TARGET_NAME!"
        ;;
    *)
        # Specific package specified (e.g. app.exteragram.messenger)
        TARGET_PKG="$TARGET_SETTING"
        echo "[*] Applying proxy to specific package: $TARGET_PKG"
        am start -a android.intent.action.VIEW -d "$PROXY_URL" -p "$TARGET_PKG" >/dev/null 2>&1
        echo "[✓] Intent sent to $TARGET_PKG!"
        ;;
esac

exit 0
