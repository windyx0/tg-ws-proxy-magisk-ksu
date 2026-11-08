#!/system/bin/sh
# ==============================================================================
# Telegram WS Proxy - Root Network Turbo & Kernel Accelerator
# High-speed low-latency TCP stack tuning, BBR congestion control, and QoS
# ==============================================================================

LOG_TAG="TgWsProxy-NetTurbo"
CONF_DIR="/data/adb/tg-ws-proxy"
LOG_FILE="$CONF_DIR/net_turbo.log"

log_msg() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg"
    /system/bin/log -t "$LOG_TAG" "$1" 2>/dev/null
    mkdir -p "$CONF_DIR" 2>/dev/null
    echo "$msg" >> "$LOG_FILE" 2>/dev/null
}

log_msg "================================================="
log_msg "Applying Root Network Acceleration..."
log_msg "================================================="

# ------------------------------------------------------------------------------
# 1. TCP Congestion Control (BBR priority with graceful fallback)
# ------------------------------------------------------------------------------
AVAILABLE_CC=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
log_msg "Available congestion control algorithms: $AVAILABLE_CC"

if echo "$AVAILABLE_CC" | grep -q "bbr"; then
    sysctl -w net.ipv4.tcp_congestion_control=bbr 2>/dev/null
    sysctl -w net.core.default_qdisc=fq 2>/dev/null
    log_msg "[+] Enabled TCP BBR with FQ pacing qdisc"
elif echo "$AVAILABLE_CC" | grep -q "cubic"; then
    sysctl -w net.ipv4.tcp_congestion_control=cubic 2>/dev/null
    sysctl -w net.core.default_qdisc=fq_codel 2>/dev/null || sysctl -w net.core.default_qdisc=fq 2>/dev/null
    log_msg "[+] Enabled TCP CUBIC with fair-queuing qdisc"
elif echo "$AVAILABLE_CC" | grep -q "westwood"; then
    sysctl -w net.ipv4.tcp_congestion_control=westwood 2>/dev/null
    log_msg "[+] Enabled TCP Westwood (optimized for lossy mobile networks)"
fi

# ------------------------------------------------------------------------------
# 2. TCP Fast Open (TFO) - Eliminate 1-RTT latency on reconnection
# ------------------------------------------------------------------------------
# 3 = enable TFO for both incoming listeners and outgoing client sockets
sysctl -w net.ipv4.tcp_fastopen=3 2>/dev/null
log_msg "[+] Enabled TCP Fast Open (client & server)"

# ------------------------------------------------------------------------------
# 3. Prevent TCP Congestion Window Collapsing During Chat Idle Periods
# ------------------------------------------------------------------------------
# Default is 1: drops cwnd to 10 packets after connection goes idle for a few sec.
# Setting to 0 keeps the window open, giving instant full-speed message sending!
sysctl -w net.ipv4.tcp_slow_start_after_idle=0 2>/dev/null
log_msg "[+] Disabled tcp_slow_start_after_idle (instant chat responsiveness)"

# ------------------------------------------------------------------------------
# 4. High-Throughput Buffer Tuning (16MB max buffers for 4K video & media)
# ------------------------------------------------------------------------------
sysctl -w net.core.rmem_max=16777216 2>/dev/null
sysctl -w net.core.wmem_max=16777216 2>/dev/null
sysctl -w net.core.rmem_default=1048576 2>/dev/null
sysctl -w net.core.wmem_default=1048576 2>/dev/null

# min, default, max buffer sizes
sysctl -w net.ipv4.tcp_rmem="4096 87380 16777216" 2>/dev/null
sysctl -w net.ipv4.tcp_wmem="4096 65536 16777216" 2>/dev/null

# Backlog queue
sysctl -w net.core.netdev_max_backlog=10000 2>/dev/null
sysctl -w net.core.somaxconn=4096 2>/dev/null
sysctl -w net.ipv4.tcp_max_syn_backlog=4096 2>/dev/null
log_msg "[+] Expanded network socket buffers to 16MB"

# ------------------------------------------------------------------------------
# 5. Socket Latency & Fast Dead Connection Detection
# ------------------------------------------------------------------------------
# Reuse TIME_WAIT sockets safely
sysctl -w net.ipv4.tcp_tw_reuse=1 2>/dev/null

# Reduce FIN-WAIT-2 timeout from 60s to 15s
sysctl -w net.ipv4.tcp_fin_timeout=15 2>/dev/null

# Keepalive: send probe at 60s instead of default 7200s (2 hours)
# Detect dropped cellular connections within ~30s instead of waiting forever
sysctl -w net.ipv4.tcp_keepalive_time=60 2>/dev/null
sysctl -w net.ipv4.tcp_keepalive_intvl=10 2>/dev/null
sysctl -w net.ipv4.tcp_keepalive_probes=3 2>/dev/null

# Reduce SYN retry delay for faster fallback switching
sysctl -w net.ipv4.tcp_syn_retries=2 2>/dev/null
sysctl -w net.ipv4.tcp_synack_retries=2 2>/dev/null

# Minimize TCP send bufferbloat for interactive chats (notsent_lowat = 16KB)
sysctl -w net.ipv4.tcp_notsent_lowat=16384 2>/dev/null
log_msg "[+] Optimized TCP keepalive and low-latency buffer limits"

# ------------------------------------------------------------------------------
# 6. Cellular MTU Black-Hole Workaround
# ------------------------------------------------------------------------------
# Mobile operators often fragment packets or drop ICMP Fragmentation-Needed.
# tcp_mtu_probing dynamically discovers maximum payload size without hanging.
sysctl -w net.ipv4.tcp_mtu_probing=1 2>/dev/null
sysctl -w net.ipv4.tcp_base_mss=1024 2>/dev/null
log_msg "[+] Enabled TCP MTU Path Probing for mobile data reliability"

# ------------------------------------------------------------------------------
# 7. Quality of Service (QoS) & Local Loopback Fast-Path (iptables/nftables)
# ------------------------------------------------------------------------------
PROXY_PORT=1444
if [ -f "$CONF_DIR/port" ]; then
    PROXY_PORT=$(cat "$CONF_DIR/port" 2>/dev/null)
fi

# Clean previous rules to prevent duplication
iptables -w 2 -t mangle -D OUTPUT -p tcp --dport "$PROXY_PORT" -j DSCP --set-dscp-class AF41 2>/dev/null
iptables -w 2 -t mangle -D OUTPUT -p tcp --dport "$PROXY_PORT" -j TOS --set-tos 0x10 2>/dev/null
iptables -w 2 -t mangle -D OUTPUT -p tcp --sport "$PROXY_PORT" -j TOS --set-tos 0x10 2>/dev/null
iptables -w 2 -t raw -D PREROUTING -d 127.0.0.1 -p tcp --dport "$PROXY_PORT" -j NOTRACK 2>/dev/null
iptables -w 2 -t raw -D OUTPUT -s 127.0.0.1 -p tcp --sport "$PROXY_PORT" -j NOTRACK 2>/dev/null

# Prioritize MTProto Proxy traffic with Low Delay TOS and AF41 DSCP
iptables -w 2 -t mangle -A OUTPUT -p tcp --dport "$PROXY_PORT" -j DSCP --set-dscp-class AF41 2>/dev/null
iptables -w 2 -t mangle -A OUTPUT -p tcp --dport "$PROXY_PORT" -j TOS --set-tos 0x10 2>/dev/null
iptables -w 2 -t mangle -A OUTPUT -p tcp --sport "$PROXY_PORT" -j TOS --set-tos 0x10 2>/dev/null

# Bypass connection tracking (NOTRACK) on localhost loopback to save CPU and RAM
iptables -w 2 -t raw -A PREROUTING -d 127.0.0.1 -p tcp --dport "$PROXY_PORT" -j NOTRACK 2>/dev/null
iptables -w 2 -t raw -A OUTPUT -s 127.0.0.1 -p tcp --sport "$PROXY_PORT" -j NOTRACK 2>/dev/null

log_msg "[+] Applied DSCP/TOS QoS and localhost NOTRACK acceleration for port $PROXY_PORT"
log_msg "[✓] Root Network Turbo successfully active!"
exit 0
