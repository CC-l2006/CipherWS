#!/usr/bin/env bash
# =============================================================================
# CipherWS 前端一键更新（在服务器上运行）
#
#   ./deploy/update-frontend.sh                 拉代码 + 构建两个站点 + 重启主站服务
#   ./deploy/update-frontend.sh --site main     只更新主站
#   ./deploy/update-frontend.sh --site train    只更新云间列车
#   ./deploy/update-frontend.sh --skip-pull     不拉代码，只重新构建（改了 .env 后很有用）
#   ./deploy/update-frontend.sh --ci            依赖有变动时用 npm ci 做可复现安装
#   ./deploy/update-frontend.sh --no-restart    只构建，不动任何服务
#   ./deploy/update-frontend.sh --help
#
# 它依次做这些事：
#   1. 环境自检（git / node / npm / 仓库目录 / 工作区是否干净）
#   2. git pull --ff-only
#   3. 补齐缺失的 .env.production，并检查它是否配成了同源模式
#   4. 仅当 package.json / package-lock.json 变动或 node_modules 缺失时安装依赖
#   5. 构建选中的站点（frontend/main/dist、frontend/train/dist）
#   6. 主站重启 systemd 服务；nginx 配置有变动时「先备份、再校验、失败自动回滚」
#   7. 自检产物是否存在、主站本机端口与两个域名是否响应
#
# 可用环境变量覆盖默认值（一般不需要改）：
#   REPO_DIR=/opt/cipherws          BRANCH=main
#   MAIN_SERVICE=cipherws-web-main  MAIN_PORT=8000
#   NGINX_CONF=/etc/nginx/conf.d/cipherws.conf
# =============================================================================

set -euo pipefail

if [[ -z "${BASH_VERSION:-}" ]]; then
  printf '本脚本需要 bash，请用：bash %s\n' "$0" >&2
  exit 1
fi

# -----------------------------------------------------------------------------
# 可覆盖的配置
#
# SELF 在这里就先解析成绝对路径：脚本后面会 cd 到仓库根目录，
# 那时 BASH_SOURCE[0] 若是相对路径就会失效（自更新后重跑要用到它）。
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SELF="$SCRIPT_DIR/$(basename -- "${BASH_SOURCE[0]}")"
REPO_DIR="${REPO_DIR:-$(dirname -- "$SCRIPT_DIR")}"
BRANCH="${BRANCH:-main}"
MAIN_SERVICE="${MAIN_SERVICE:-cipherws-web-main}"
MAIN_PORT="${MAIN_PORT:-8000}"
NGINX_CONF="${NGINX_CONF:-/etc/nginx/conf.d/cipherws.conf}"
NGINX_SRC_REL="deploy/nginx/cipherws.conf"
ENV_DOC_REL="docs/2026-10-10/前端双站点结构.md"

# -----------------------------------------------------------------------------
# 命令行参数
# -----------------------------------------------------------------------------
SITE="all"
SKIP_PULL=0
USE_CI=0
DO_RESTART=1
DO_VERIFY=1

usage() {
  cat <<'EOF'
CipherWS 前端一键更新

用法：
  ./deploy/update-frontend.sh [选项]

选项：
  --site all|main|train   要更新的站点，默认 all
  --skip-pull             不拉代码，只重新构建（改了 .env.production 后用这个）
  --ci                    依赖有变动时用 npm ci 而非 npm install
  --no-restart            只构建，不重启服务、不同步 nginx 配置
  --no-verify             跳过构建后的自检
  -h, --help              显示本帮助

常用环境变量：
  REPO_DIR=/opt/cipherws       仓库目录（默认取脚本所在目录的上级）
  BRANCH=main                  要拉取的分支
  MAIN_SERVICE=cipherws-web-main   主站的 systemd 服务名
  NGINX_CONF=/etc/nginx/conf.d/cipherws.conf   nginx 站点配置路径

示例：
  ./deploy/update-frontend.sh                 更新两个站点
  ./deploy/update-frontend.sh --site train    只更新云间列车（不需要重启服务）
  ./deploy/update-frontend.sh --skip-pull     改完 .env.production 后重新构建

完整说明见 docs/2026-10-10/前端双站点结构.md
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --site)        [[ $# -ge 2 ]] || { printf -- '--site 后面要跟 all/main/train\n' >&2; exit 2; }
                   SITE="$2"; shift 2 ;;
    --site=*)      SITE="${1#*=}"; shift ;;
    --skip-pull)   SKIP_PULL=1; shift ;;
    --ci)          USE_CI=1; shift ;;
    --no-restart)  DO_RESTART=0; shift ;;
    --no-verify)   DO_VERIFY=0; shift ;;
    -h|--help)     usage ;;
    *) printf '未知参数：%s（用 --help 查看用法）\n' "$1" >&2; exit 2 ;;
  esac
done

case "$SITE" in
  all|main|train) ;;
  *) printf -- '--site 只能是 all / main / train，收到：%s\n' "$SITE" >&2; exit 2 ;;
esac

# -----------------------------------------------------------------------------
# 输出 helpers：只在终端上色，重定向到文件时不带转义码
# -----------------------------------------------------------------------------
if [[ -t 1 ]]; then
  C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_OFF=$'\033[0m'
else
  C_INFO=''; C_OK=''; C_WARN=''; C_ERR=''; C_OFF=''
fi
info() { printf '%s[信息]%s %s\n' "$C_INFO" "$C_OFF" "$*"; }
ok()   { printf '%s[完成]%s %s\n' "$C_OK"   "$C_OFF" "$*"; }
warn() { printf '%s[警告]%s %s\n' "$C_WARN" "$C_OFF" "$*" >&2; }
step() { printf '\n%s==> %s%s\n' "$C_INFO" "$*" "$C_OFF"; }
die()  { printf '%s[错误]%s %s\n' "$C_ERR" "$C_OFF" "$*" >&2; exit 1; }

site_wanted() { [[ "$SITE" == "all" || "$SITE" == "$1" ]]; }

# -----------------------------------------------------------------------------
# 0. 环境自检
# -----------------------------------------------------------------------------
step "环境自检"

for cmd in git node npm; do
  command -v "$cmd" >/dev/null 2>&1 || die "找不到 $cmd，请先安装"
done

[[ -d "$REPO_DIR/.git" ]] || die "$REPO_DIR 不是 Git 仓库（可用 REPO_DIR=... 指定）"
[[ -f "$REPO_DIR/frontend/package.json" ]] || die "$REPO_DIR/frontend/package.json 不存在，仓库结构不对"

cd "$REPO_DIR"

# root 不需要 sudo；否则必须有 sudo，不然重启服务那步会失败
if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
  SUDO=""
elif command -v sudo >/dev/null 2>&1; then
  SUDO="sudo"
else
  SUDO=""
  warn "当前非 root 且没有 sudo，后面同步 nginx 配置会失败"
fi

ok "git $(git --version | awk '{print $3}')、node $(node -v)、npm $(npm -v)"
ok "仓库目录：$REPO_DIR"
info "本次更新站点：$SITE"

if [[ -n "$(git status --porcelain)" ]]; then
  warn "工作区有未提交改动，git pull 可能因此失败："
  git status --short >&2
else
  ok "工作区干净（.env.* / dist/ / node_modules/ 已被忽略，不会出现在这里）"
fi

# -----------------------------------------------------------------------------
# 1. git pull
# -----------------------------------------------------------------------------
BEFORE="$(git rev-parse HEAD)"

if [[ "$SKIP_PULL" -eq 1 ]]; then
  info "已指定 --skip-pull，跳过拉取"
else
  step "拉取代码 origin/$BRANCH"
  if ! git pull --ff-only origin "$BRANCH"; then
    die "git pull 失败。常见原因：本地有未提交改动，或本地与远端已分叉（需手工 merge/rebase 后重试）"
  fi
  ok "当前提交 $(git rev-parse --short HEAD)"
fi

AFTER="$(git rev-parse HEAD)"

if [[ "$BEFORE" == "$AFTER" && "$SKIP_PULL" -eq 0 ]]; then
  info "代码已是最新（$(git rev-parse --short HEAD)），仍会重建所选站点"
fi

# 脚本自身在这次 pull 里被更新过 → 用新版本重跑一次，
# 避免"用旧逻辑去跑新代码"。REEXEC 防止无限递归。
if [[ "$BEFORE" != "$AFTER" && -z "${REEXEC:-}" && -f "$SELF" ]]; then
  if git diff --name-only "$BEFORE" "$AFTER" | grep -qx 'deploy/update-frontend.sh'; then
    info "检测到本脚本自身有更新，改用新版本重新执行"
    export REEXEC=1
    exec "$SELF" "$@"
  fi
fi

# -----------------------------------------------------------------------------
# 2. 环境变量文件
# -----------------------------------------------------------------------------
ensure_env_production() {
  # 注意：这里必须分开写。`local dir="$1" prod="$dir/..."` 会在赋值发生前
  # 就展开 $dir，配合 set -u 直接报 unbound variable。
  local dir="$1"
  local prod="$dir/.env.production"
  local val

  if [[ ! -f "$prod" ]]; then
    cat > "$prod" <<EOF
# 线上构建：留空 = 同源相对路径，由 nginx 把 /say/ 与 /audio/ 反代到后端
# 配置见 $NGINX_SRC_REL，说明见 $ENV_DOC_REL
#
# 「=」必须保留。整行删掉的话 import.meta.env.VITE_API_BASE_URL 会变成 undefined，
# 请求地址会被拼成 "undefined/say/saying"。
VITE_API_BASE_URL=
EOF
    ok "已创建 $prod（同源模式）"
    return 0
  fi

  val="$(grep -E '^[[:space:]]*VITE_API_BASE_URL[[:space:]]*=' "$prod" | tail -n1 | cut -d= -f2- | tr -d '[:space:]' || true)"
  if [[ -z "$val" ]]; then
    ok "$prod 已是同源模式（VITE_API_BASE_URL 留空）"
  else
    warn "$prod 里 VITE_API_BASE_URL=$val"
    warn "若线上已用 nginx 同源反代，这里应留空；否则浏览器会直连该地址，"
    warn "既跨域，又会撞上 SakuraFrp 对浏览器直连的安全认证限制。"
    warn "改完记得重跑本脚本：环境变量是构建时内联的，改文件不重新构建无效。"
  fi
}

step "检查环境变量文件"

if site_wanted main; then
  ensure_env_production "$REPO_DIR/frontend/main"
fi
if site_wanted train; then
  info "frontend/train 不访问后端接口，无需 .env 文件"
fi

# -----------------------------------------------------------------------------
# 3. 依赖按需安装
# -----------------------------------------------------------------------------
deps_changed() {
  local sub="$1"
  [[ "$BEFORE" == "$AFTER" ]] && return 1
  if git diff --name-only "$BEFORE" "$AFTER" -- \
       "frontend/$sub/package.json" "frontend/$sub/package-lock.json" | grep -q .; then
    return 0
  fi
  return 1
}

step "依赖检查"

for sub in main train; do
  site_wanted "$sub" || continue

  if [[ ! -d "$REPO_DIR/frontend/$sub/node_modules" ]]; then
    info "$sub 没有 node_modules，需要安装"
  elif deps_changed "$sub"; then
    info "$sub 的 package.json / package-lock.json 有变动，需要安装"
  else
    ok "$sub 依赖无变动，跳过安装"
    continue
  fi

  if [[ "$USE_CI" -eq 1 ]]; then
    info "在 frontend/$sub 执行 npm ci（可复现安装）"
    ( cd "$REPO_DIR/frontend/$sub" && npm ci ) || die "$sub 的 npm ci 失败"
  else
    info "在 frontend/$sub 执行 npm install"
    ( cd "$REPO_DIR/frontend/$sub" && npm install ) || die "$sub 的 npm install 失败"
  fi
  ok "$sub 依赖安装完成"
done

# -----------------------------------------------------------------------------
# 4. 构建
# -----------------------------------------------------------------------------
step "构建"

for sub in main train; do
  site_wanted "$sub" || continue
  info "构建 $sub ..."
  ( cd "$REPO_DIR/frontend" && npm run "build:$sub" ) || die "$sub 构建失败"
  [[ -f "$REPO_DIR/frontend/$sub/dist/index.html" ]] \
    || die "$sub 构建没有产出 frontend/$sub/dist/index.html"
  ok "$sub 构建完成 → frontend/$sub/dist"
done

# -----------------------------------------------------------------------------
# 5. 重启主站服务
# -----------------------------------------------------------------------------
if ! site_wanted main; then
  info "云间列车由 nginx 直接托管 dist，无需重启任何服务"
elif [[ "$DO_RESTART" -eq 0 ]]; then
  info "已指定 --no-restart，跳过重启主站服务"
else
  step "重启主站服务"

  if ! systemctl list-unit-files 2>/dev/null | grep -q "^${MAIN_SERVICE}\.service"; then
    warn "找不到 systemd 单元 ${MAIN_SERVICE}.service，跳过重启"
    warn "用 systemctl list-units --type=service | grep -i cipherws 确认实际服务名，"
    warn "再以 MAIN_SERVICE=实际名字 重跑本脚本"
  else
    $SUDO systemctl restart "$MAIN_SERVICE" || die "重启 $MAIN_SERVICE 失败"
    sleep 1
    if systemctl is-active --quiet "$MAIN_SERVICE"; then
      ok "$MAIN_SERVICE 已重启并处于 active"
    else
      die "$MAIN_SERVICE 重启后不是 active，用 journalctl -u $MAIN_SERVICE -n 50 看日志"
    fi
  fi
fi

# -----------------------------------------------------------------------------
# 6. nginx 配置同步：只在有变化时动手，且校验失败自动回滚
#
# 关键点：不能先把配置覆盖过去再校验 —— 那样一旦配置有问题，
# nginx -t 失败的同时 /etc/nginx/conf.d 里已经躺着坏文件，
# 下次 nginx 重启就起不来了。所以先备份、再覆盖、失败则还原。
# -----------------------------------------------------------------------------
if [[ "$DO_RESTART" -eq 1 && -f "$REPO_DIR/$NGINX_SRC_REL" ]]; then
  if [[ ! -d "$(dirname -- "$NGINX_CONF")" ]]; then
    info "未发现 nginx（$(dirname -- "$NGINX_CONF") 不存在），跳过配置同步"
  elif [[ -f "$NGINX_CONF" ]] && cmp -s "$REPO_DIR/$NGINX_SRC_REL" "$NGINX_CONF"; then
    ok "nginx 配置无变化"
  else
    step "同步 nginx 配置"

    BACKUP="$(mktemp)"
    [[ -f "$NGINX_CONF" ]] && cp "$NGINX_CONF" "$BACKUP"

    $SUDO cp "$REPO_DIR/$NGINX_SRC_REL" "$NGINX_CONF"

    if ! $SUDO nginx -t; then
      warn "nginx -t 校验未通过，回滚配置"
      if [[ -s "$BACKUP" ]]; then
        $SUDO cp "$BACKUP" "$NGINX_CONF"
        warn "已还原原有配置，线上服务不受影响"
      else
        $SUDO rm -f "$NGINX_CONF"
        warn "之前没有该配置文件，已删除，线上服务不受影响"
      fi
      rm -f "$BACKUP"
      die "nginx 配置无效，未加载。请修好 $NGINX_SRC_REL 后重试"
    fi

    rm -f "$BACKUP"
    $SUDO systemctl reload nginx || die "nginx reload 失败"
    ok "nginx 配置已更新并 reload"
  fi
fi

# -----------------------------------------------------------------------------
# 7. 自检
# -----------------------------------------------------------------------------
if [[ "$DO_VERIFY" -eq 1 ]]; then
  step "自检"

  for sub in main train; do
    site_wanted "$sub" || continue
    html="$REPO_DIR/frontend/$sub/dist/index.html"
    ok "产物存在：frontend/$sub/dist/index.html（$(du -h "$html" | cut -f1)）"
  done

  if ! command -v curl >/dev/null 2>&1; then
    info "没有 curl，跳过网络探活"
  else
    if site_wanted main; then
      if curl -fsS --max-time 5 "http://127.0.0.1:${MAIN_PORT}/" >/dev/null 2>&1; then
        ok "主站 http://127.0.0.1:${MAIN_PORT}/ 响应正常"
      else
        warn "主站 http://127.0.0.1:${MAIN_PORT}/ 无响应"
        warn "排查：systemctl status ${MAIN_SERVICE}；journalctl -u ${MAIN_SERVICE} -n 50"
      fi
    fi

    if [[ -f "$NGINX_CONF" ]]; then
      for host in cipherws.icu train.cipherws.icu; do
        code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
                -H "Host: $host" 'http://127.0.0.1/' || true)"
        if [[ "$code" == "200" ]]; then
          ok "nginx 本机探活 $host → 200"
        else
          warn "nginx 本机探活 $host → ${code:-无响应}"
          warn "  502 = 上游没起；404 = server_name 或 root 没配对；"
          warn "  000 = nginx 没在跑或没监听 80"
        fi
      done
    fi
  fi
fi

# -----------------------------------------------------------------------------
# 收尾
# -----------------------------------------------------------------------------
step "完成"

cat <<'EOF'
建议手动确认一遍：
  · 主站      http://cipherws.icu/
  · 云间列车  http://train.cipherws.icu/
  · 名言接口  http://cipherws.icu/say/saying?id=1
  · 音频流    http://cipherws.icu/audio/stream?audio=HOYO-MiX%20-%20Da%20Capo.mp3

如果 /say/ 或 /audio/ 返回 502，说明本地笔记本上的后端或 SakuraFrp 隧道掉线。
此时静态页面仍然正常，只是「每日一句」与音乐播放器取不到数据，属于预期情况。
EOF
