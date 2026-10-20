#!/usr/bin/env bash
# 更新 nginx 配置里的后端隧道地址（线上 ECS 用）。
#
# 用法：
#   sudo bash update-backend-address.sh frp-box.com:64456
#   sudo bash update-backend-address.sh frp-box.com:64456 --dry-run
#
# 后端跑在本地笔记本上，经 SakuraFrp 隧道暴露。隧道地址（域名+端口）在
# nginx 配置里一共出现 9 处，漏改任意一处都会出问题（例如上游连上了，
# 但 SNI/Host 还带着老域名），所以统一用这个脚本改，不手改。
#
#   1. upstream cipherws_backend 的 server 行
#   2. location /say/    的 proxy_set_header Host
#   3. location /say/    的 proxy_ssl_name
#   4. location /audio/  的 proxy_set_header Host
#   5. location /audio/  的 proxy_ssl_name
#   6. location /api/    的 proxy_set_header Host      ← 笔记站接口
#   7. location /api/    的 proxy_ssl_name
#   8. location /uploads/ 的 proxy_set_header Host     ← 笔记站头像
#   9. location /uploads/ 的 proxy_ssl_name
#
# 笔记站与主站共用同一条隧道（笔记本只穿透 8080 一个端口），
# 所以这两组 location 也走 cipherws_backend，地址必须一起改。
#
# 脚本只动这种「孤立地址」行：
#   server <地址>;          proxy_set_header Host <地址>;
#   proxy_ssl_name <地址>;
# 改完先 nginx -t，通过才 reload；不通过则回滚，站点不受影响。
set -euo pipefail

CONF="${CONF:-/etc/nginx/conf.d/cipherws.conf}"
NEW="${1:-}"
DRY_RUN=0

for arg in "$@"; do
    [ "$arg" = "--dry-run" ] && DRY_RUN=1
done

if [ -z "$NEW" ]; then
    echo "用法: sudo bash $0 <域名:端口> [--dry-run]" >&2
    echo "示例: sudo bash $0 frp-box.com:64456" >&2
    exit 1
fi

# 形如 host:port，host 允许字母数字点横线，端口 1-5 位
if ! printf '%s' "$NEW" | grep -Eq '^[A-Za-z0-9.-]+:[0-9]{1,5}$'; then
    echo "地址格式不对：$NEW（应为 域名:端口，例如 frp-box.com:64456）" >&2
    exit 1
fi

if [ ! -f "$CONF" ]; then
    echo "找不到配置文件：$CONF" >&2
    exit 1
fi

HOST="${NEW%%:*}"
BAK="${CONF}.bak.$(date +%Y%m%d%H%M%S)"

echo "配置文件 : $CONF"
echo "新地址   : $NEW"
echo "备份     : $BAK"

# 1) 备份
cp -a "$CONF" "$BAK"

# 2) 先把行尾统一成 LF。本文件在 Windows 上编辑过，行尾可能是 CRLF 或混合的，
#    行尾的 \r 会让下面所有 `$` 锚点静默失配（命令跑完却什么都没改）。
if grep -qU $'\r' "$CONF" 2>/dev/null; then
    echo "检测到 CRLF 行尾，先统一转换为 LF"
    sed -i 's/\r$//' "$CONF"
fi

# 3) 替换。必须按「块上下文」来改，不能对所有 server 行一刀切 ——
#    本文件里 `upstream cipherws_frontend { server 127.0.0.1:8000; }` 也是一行
#    server，前端上游绝不能跟着改成隧道地址（否则静态站点直接 502）。
#
#    所以用 awk 跟踪当前处于哪个 upstream 块：
#      · 只在 upstream cipherws_backend 块内改 server 行
#      · proxy_set_header Host / proxy_ssl_name 在本文件里只出现在后端两个
#        location 中，可以全局改（前端用的是 $host 变量，不含字面地址）
#
#    每处仍容忍行尾 \r?，这样即使文件在别处被改回 CRLF 也不会失配。
awk -v new="$NEW" -v newhost="$HOST" '
    /^[[:space:]]*upstream[[:space:]]/ {
        in_backend = ($0 ~ /upstream[[:space:]]+cipherws_backend/)
    }
    {
        line = $0
        if (in_backend && line ~ /^[[:space:]]*server[[:space:]]+[A-Za-z0-9.-]+:[0-9]+;[[:space:]]*\r?$/) {
            sub(/[A-Za-z0-9.-]+:[0-9]+;/, new ";", line)
        }
        if (line ~ /^[[:space:]]*proxy_set_header[[:space:]]+Host[[:space:]]+[A-Za-z0-9.-]+:[0-9]+;[[:space:]]*\r?$/) {
            sub(/[A-Za-z0-9.-]+:[0-9]+;/, new ";", line)
        }
        if (line ~ /^[[:space:]]*proxy_ssl_name[[:space:]]+[A-Za-z0-9.-]+;[[:space:]]*\r?$/) {
            sub(/[A-Za-z0-9.-]+;/, newhost ";", line)
        }
        print line
    }
' "$CONF" > "${CONF}.tmp" && cat "${CONF}.tmp" > "$CONF" && rm -f "${CONF}.tmp"

echo
echo "改动后的地址行（不应包含 cipherws_frontend 的 127.0.0.1:8000）："
grep -nE "^[[:space:]]*(server[[:space:]]+[A-Za-z0-9.-]+:[0-9]+;|proxy_set_header[[:space:]]+Host[[:space:]]+[A-Za-z0-9.-]+:[0-9]+;|proxy_ssl_name[[:space:]]+[A-Za-z0-9.-]+;)" "$CONF" \
    | sed 's/^/  /' || true

echo
echo "前端上游（必须保持 127.0.0.1:8000）："
grep -A2 'upstream cipherws_frontend' "$CONF" | sed 's/^/  /' || true

echo
echo "与前一份备份的差异："
diff "$BAK" "$CONF" | sed 's/^/  /' || true

if [ "$DRY_RUN" -eq 1 ]; then
    echo
    echo "--dry-run：未做语法校验，也未 reload。已生成的备份保留在 $BAK"
    exit 0
fi

# 3) 语法校验，通过才 reload
echo
if nginx -t; then
    systemctl reload nginx
    echo
    echo "✅ 已 reload nginx，后端地址现在是 $NEW"
    echo "   验证：curl -s https://你的域名/say/saying?id=1"
else
    echo
    echo "❌ nginx -t 失败，正在回滚……" >&2
    cp -a "$BAK" "$CONF"
    echo "已回滚到改动前，nginx 未 reload。" >&2
    exit 1
fi
