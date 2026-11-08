#!/system/bin/sh
# ==============================================================================
# Telegram WS Proxy - Daemon Controller & CLI Manager
# Controls proxy daemon, autostart, notification silencing, and fork selection
# ==============================================================================

MODDIR="/data/adb/modules/tg_ws_proxy"
CONF_DIR="/data/adb/tg-ws-proxy"
mkdir -p "$CONF_DIR" 2>/dev/null

PORT=1444
[ -f "$CONF_DIR/port" ] && PORT=$(cat "$CONF_DIR/port" 2>/dev/null)

PRIMARY_APP_ID="com.windyx0.tgbypass"
FALLBACK_APP_ID="com.windyx0.twgsproxy"

# Detect which app is installed
if pm path "$PRIMARY_APP_ID" >/dev/null 2>&1; then
    APP_ID="$PRIMARY_APP_ID"
    SERVICE_NAME="$APP_ID/com.windyx0.tgbypass.proxy.ProxyService"
elif pm path "$FALLBACK_APP_ID" >/dev/null 2>&1; then
    APP_ID="$FALLBACK_APP_ID"
    SERVICE_NAME="$APP_ID/com.amurcanov.tgwsproxy.ProxyService"
else
    APP_ID="$PRIMARY_APP_ID"
    SERVICE_NAME="$APP_ID/com.windyx0.tgbypass.proxy.ProxyService"
fi

# ------------------------------------------------------------------------------
# Helper: check if port is currently listening
# ------------------------------------------------------------------------------
is_port_listening() {
    local check_port=$1
    # Hex port representation for /proc/net/tcp
    local hex_port=$(printf '%04X' "$check_port")
    if grep -q ":$hex_port " /proc/net/tcp 2>/dev/null || grep -q ":$hex_port " /proc/net/tcp6 2>/dev/null; then
        return 0
    fi
    # Fallback to nc
    if toybox nc -z 127.0.0.1 "$check_port" >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

# ------------------------------------------------------------------------------
# Helper: check if process is running
# ------------------------------------------------------------------------------
is_proxy_running() {
    if pgrep -f "$APP_ID" >/dev/null 2>&1; then
        return 0
    fi
    if [ -f "$CONF_DIR/daemon.pid" ]; then
        local pid=$(cat "$CONF_DIR/daemon.pid" 2>/dev/null)
        if [ -n "$pid" ] && [ -d "/proc/$pid" ]; then
            return 0
        fi
    fi
    return 1
}

# ------------------------------------------------------------------------------
# Suppress annoying Android foreground notification automatically (Requirement 5)
# ------------------------------------------------------------------------------
kill_notification() {
    if pm path "$APP_ID" >/dev/null 2>&1; then
        appops set "$APP_ID" POST_NOTIFICATION ignore >/dev/null 2>&1
        appops set "$APP_ID" 11 1 >/dev/null 2>&1
        cmd notification set_bubbles "$APP_ID" 0 >/dev/null 2>&1
        pm revoke "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
        dumpsys deviceidle whitelist +"$APP_ID" >/dev/null 2>&1
    fi
}

# ------------------------------------------------------------------------------
# Start Proxy
# ------------------------------------------------------------------------------
start_proxy() {
    echo "[*] Starting Telegram WS Proxy on 127.0.0.1:$PORT..."
    
    # Apply root network acceleration
    if [ -f "$MODDIR/scripts/net_accelerator.sh" ]; then
        sh "$MODDIR/scripts/net_accelerator.sh" >/dev/null 2>&1 &
    fi

    local cf_domains="pclead.co.uk|offshor.co.uk|cakeisalie.co.uk|noskomnadzor.co.uk|lovetrue.co.uk|sorokdva.co.uk|pyatdesyatdva.co.uk|kartoshka.co.uk|sorokodin.co.uk|pyatdesyatodin.co.uk|notelega.co.uk|ebally.co.uk|nebally.co.uk|havegreatday.co.uk|pomogite.co.uk|fixtelega.co.uk|sadnews.co.uk|onedaychamp.co.uk|stopblocking.co.uk|nothingthere.co.uk"
    local dc_entries="1:149.154.175.50,2:149.154.167.51,4:149.154.167.91,203:91.105.192.100"
    local runtime_tokens="$dc_entries,@connection_mode=cf_first,@cfproxy=1,@cfproxy_priority=1,@cfproxy_only=0,@route_order=cf_proxy_ws|direct_ws|tcp_fallback,@route_cf_proxy_ws=1,@route_direct_ws=1,@route_tcp_fallback=1,@preferred_route=cf_proxy_ws,@route_fallback=1,@cf_cached_domains=$cf_domains"

    # Start proxy service with Cloudflare Turbo configuration
    am start-foreground-service -n "$SERVICE_NAME" -a "com.windyx0.tgbypass.START" --ei "EXTRA_PORT" "$PORT" --es "EXTRA_IPS" "$runtime_tokens" >/dev/null 2>&1 || \
    am start-foreground-service -n "$SERVICE_NAME" -a "com.amurcanov.tgwsproxy.START" --ei "EXTRA_PORT" "$PORT" --es "EXTRA_IPS" "$runtime_tokens" >/dev/null 2>&1 || \
    am startservice -n "$SERVICE_NAME" -a "com.windyx0.tgbypass.START" --ei "EXTRA_PORT" "$PORT" --es "EXTRA_IPS" "$runtime_tokens" >/dev/null 2>&1

    # Automatically suppress notification
    kill_notification

    # Wait up to 5 seconds for proxy port to bind
    local count=0
    while [ $count -lt 10 ]; do
        if is_port_listening "$PORT"; then
            echo "[✓] Proxy is UP and listening on 127.0.0.1:$PORT!"
            echo "1" > "$CONF_DIR/running"
            return 0
        fi
        sleep 0.5
        count=$((count + 1))
    done

    if is_proxy_running; then
        echo "[✓] Proxy process is active (warming up routes)..."
        echo "1" > "$CONF_DIR/running"
        return 0
    fi

    echo "[!] Proxy failed to start or listener timed out."
    return 1
}

# ------------------------------------------------------------------------------
# Stop Proxy
# ------------------------------------------------------------------------------
stop_proxy() {
    echo "[*] Stopping Telegram WS Proxy..."
    am startservice -n "$SERVICE_NAME" -a "com.windyx0.tgbypass.STOP" >/dev/null 2>&1 || \
    am startservice -n "$SERVICE_NAME" -a "com.amurcanov.tgwsproxy.STOP" >/dev/null 2>&1
    
    if [ -f "$CONF_DIR/daemon.pid" ]; then
        local pid=$(cat "$CONF_DIR/daemon.pid" 2>/dev/null)
        kill -9 "$pid" 2>/dev/null
        rm -f "$CONF_DIR/daemon.pid"
    fi
    
    rm -f "$CONF_DIR/running"
    echo "[✓] Proxy stopped."
}

# ------------------------------------------------------------------------------
# Status
# ------------------------------------------------------------------------------
show_status() {
    echo "================================================="
    echo "       Telegram WS Proxy - System Status         "
    echo "================================================="
    
    # Autostart state
    local auto="DISABLED"
    if [ -f "$CONF_DIR/autostart" ] && [ "$(cat "$CONF_DIR/autostart")" = "1" ]; then
        auto="ENABLED"
    fi
    echo " • Autostart on boot:  $auto"
    
    # Target fork
    local target="auto"
    [ -f "$CONF_DIR/target_tg" ] && target=$(cat "$CONF_DIR/target_tg")
    echo " • Preferred TG app:   $target"
    
    # Port & listener
    echo " • Configured port:    $PORT"
    if is_port_listening "$PORT"; then
        echo " • Listener status:    ONLINE (127.0.0.1:$PORT is open)"
    else
        echo " • Listener status:    OFFLINE (port $PORT closed)"
    fi
    
    # Process
    if is_proxy_running; then
        echo " • Daemon process:     RUNNING"
    else
        echo " • Daemon process:     STOPPED"
    fi
    
    # Network turbo
    local cc=$(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)
    local tfo=$(cat /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null)
    local ss=$(cat /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null)
    echo " • TCP Congestion:     $cc"
    echo " • TCP Fast Open:      $tfo"
    echo " • Slow-Start-Idle:    $ss (0 = turbo active)"
    echo " • Notification state: AUTOMATICALLY SILENCED (Module Mode)"
    echo "================================================="
}

# ------------------------------------------------------------------------------
# Autostart toggle (Requirement 2)
# ------------------------------------------------------------------------------
set_autostart() {
    case "$1" in
        "1"|"on"|"enable")
            echo "1" > "$CONF_DIR/autostart"
            echo "[✓] Autostart ENABLED! Proxy and Network Turbo will start on device boot."
            ;;
        "0"|"off"|"disable")
            echo "0" > "$CONF_DIR/autostart"
            echo "[✓] Autostart DISABLED."
            ;;
        *)
            if [ -f "$CONF_DIR/autostart" ] && [ "$(cat "$CONF_DIR/autostart")" = "1" ]; then
                echo "Autostart is currently ENABLED"
            else
                echo "Autostart is currently DISABLED"
            fi
            ;;
    esac
}

# ------------------------------------------------------------------------------
# Target Fork Selector (Requirement 4)
# ------------------------------------------------------------------------------
set_target() {
    if [ -z "$1" ]; then
        echo "Usage: tg-ws-proxy set-target <package|auto|all>"
        echo "Example: tg-ws-proxy set-target app.exteragram.messenger"
        return
    fi
    echo "$1" > "$CONF_DIR/target_tg"
    echo "[✓] Preferred Telegram app set to: $1"
}

# ------------------------------------------------------------------------------
# Command Dispatcher
# ------------------------------------------------------------------------------
case "$1" in
    "start")
        start_proxy
        ;;
    "stop")
        stop_proxy
        ;;
    "restart")
        stop_proxy
        sleep 1
        start_proxy
        ;;
    "status")
        show_status
        ;;
    "autostart")
        set_autostart "$2"
        ;;
    "set-target")
        set_target "$2"
        ;;
    "apply-tg")
        sh "$MODDIR/scripts/tg_fork_launcher.sh" "$2"
        ;;
    "tune-net")
        sh "$MODDIR/scripts/net_accelerator.sh"
        ;;
    "kill-notification")
        kill_notification
        echo "[✓] Foreground notification suppressed."
        ;;
    *)
        echo "Usage: tg-ws-proxy {start|stop|restart|status|autostart [on|off]|set-target [pkg]|apply-tg [pkg]|tune-net}"
        exit 1
        ;;
esac

exit 0
