#!/usr/bin/env bash
# =============================================================================
# 换机收尾：清理迁移包 / 凭据轮换辅助 / 释放旧机前检查
# -----------------------------------------------------------------------------
# 背景：2026-09-20 从旧机 8.130.49.86 迁移到新机 47.116.138.65，
#       /tmp 下遗留的迁移包含有「全部密钥」，属于最高优先级清理项。
#
# 用法（在【新机】以 root 执行）：
#     bash migrate-cleanup.sh check      # 只做体检，不改动任何东西（建议先跑这个）
#     bash migrate-cleanup.sh clean      # 清理迁移包残留
#     bash migrate-cleanup.sh rotate     # 生成轮换用的新凭据（只生成，不自动替换）
#
# 设计原则：
#   - 幂等：重复执行安全
#   - 保守：默认只检查；破坏性动作必须显式传参
#   - 不泄密：任何输出都不回显密钥值（只显示键名/长度/指纹）
# =============================================================================
set -uo pipefail

APP_DIR="/opt/blog"
BLOG_ENV="${APP_DIR}/blog.env"
BACKUP_ENV="${APP_DIR}/backup.env"
PROD_YML="${APP_DIR}/application-prod.yml"
RAG_ENV="${APP_DIR}/rag/backend/.env"
OLD_IP="8.130.49.86"
NEW_IP="47.116.138.65"

c_ok()   { printf '\033[32m  [OK]\033[0m %s\n' "$*"; }
c_warn() { printf '\033[33m  [!!]\033[0m %s\n' "$*"; }
c_bad()  { printf '\033[31m  [XX]\033[0m %s\n' "$*"; }
c_info() { printf '\033[36m  [--]\033[0m %s\n' "$*"; }
hdr()    { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }

# ---------------------------------------------------------------- check
do_check() {
  hdr "1. 迁移包残留（含全部密钥，最高优先级）"
  local found=0
  for pat in /tmp/blog-migrate-*.tar.gz /tmp/blog-migrate-* /tmp/migrate; do
    for f in $pat; do
      [ -e "$f" ] || continue
      found=1
      local sz; sz=$(du -sh "$f" 2>/dev/null | cut -f1)
      c_bad "残留: $f  (大小 ${sz:-?})"
    done
  done
  [ "$found" -eq 0 ] && c_ok "未发现迁移包残留"

  hdr "2. 迁移过程可能残留的临时文件"
  for f in /tmp/blog.sql.gz /tmp/blog.sql /tmp/*.env /tmp/output.tar.gz; do
    [ -e "$f" ] || continue
    c_warn "留意: $f"
  done
  c_info "（/tmp/*.env 若不存在会跳过）"

  hdr "3. 关键文件权限（应 600，属主 root 或 ecs-user）"
  for f in "$BLOG_ENV" "$BACKUP_ENV" "$PROD_YML" "$RAG_ENV"; do
    if [ -f "$f" ]; then
      local p; p=$(stat -c '%a %U:%G' "$f" 2>/dev/null)
      case "$p" in
        600*) c_ok  "$f  ($p)" ;;
        *)    c_warn "$f  ($p) ← 建议 chmod 600" ;;
      esac
    else
      c_warn "缺失: $f"
    fi
  done

  hdr "4. 服务状态"
  for s in blog-api blog-web rag nginx mysqld redis; do
    local st; st=$(systemctl is-active "$s" 2>/dev/null)
    [ "$st" = "active" ] && c_ok "$s: $st" || c_bad "$s: ${st:-unknown}"
  done

  hdr "5. 健康检查"
  local api web
  api=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1:8080/api/article/list?pageSize=1")
  web=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1:3000/")
  [ "$api" = "200" ] && c_ok "API(8080): $api" || c_bad "API(8080): $api"
  [ "$web" = "200" ] && c_ok "Web(3000): $web" || c_bad "Web(3000): $web"
  if [ -f "$BLOG_ENV" ]; then
    local tk; tk=$(grep -E '^RAG_API_TOKEN=' "$BLOG_ENV" | cut -d= -f2-)
    if [ -n "${tk:-}" ]; then
      local rh; rh=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 \
        -H "X-RAG-Token: $tk" http://127.0.0.1:8000/health)
      [ "$rh" = "200" ] && c_ok "RAG(8000): $rh" || c_bad "RAG(8000): $rh"
    fi
  fi

  hdr "6. 凭据一致性（只比对指纹，不外显值）"
  fp() { printf '%s' "$1" | sha256sum | cut -c1-12; }
  local t1 t2
  t1=$(grep -E '^RAG_API_TOKEN=' "$BLOG_ENV" 2>/dev/null | cut -d= -f2-)
  t2=$(grep -E '^RAG_API_TOKEN=' "$RAG_ENV"  2>/dev/null | cut -d= -f2-)
  if [ -n "${t1:-}" ] && [ -n "${t2:-}" ]; then
    if [ "$t1" = "$t2" ]; then c_ok "RAG_API_TOKEN 两侧一致 (指纹 $(fp "$t1"))"
    else c_bad "RAG_API_TOKEN 两侧不一致！blog.env=$(fp "$t1") rag.env=$(fp "$t2")"; fi
  else
    c_warn "未能读取两处 RAG_API_TOKEN（检查文件是否存在）"
  fi

  hdr "7. cron 残留检查（旧机宝塔行）"
  local cr; cr=$(crontab -l 2>/dev/null || true)
  if printf '%s' "$cr" | grep -q '/www/server/cron'; then
    c_warn "发现宝塔 cron 残留行，建议删除："
    printf '%s\n' "$cr" | grep '/www/server/cron' | sed 's/^/        /'
  else
    c_ok "无宝塔 cron 残留"
  fi
  printf '%s' "$cr" | grep -q 'backup.sh' && c_ok "备份 cron 存在" || c_warn "未发现 backup.sh 的 cron 行"

  hdr "8. Nginx 残留配置（历史坑：旧机 conf.d/default.conf 曾致全站 500）"
  if [ -f /etc/nginx/conf.d/default.conf ]; then
    if grep -qE 'root\s+/home/ecs-user/dist' /etc/nginx/conf.d/default.conf 2>/dev/null; then
      c_bad "/etc/nginx/conf.d/default.conf 含废弃 root 指向 → 必须删除"
    else
      c_warn "/etc/nginx/conf.d/default.conf 存在，请确认非废弃残留"
    fi
  else
    c_ok "无 conf.d/default.conf"
  fi

  hdr "9. 旧机连通性（用于判断能否安全释放）"
  if command -v nc >/dev/null 2>&1; then
    if nc -z -w3 "$OLD_IP" 22 2>/dev/null; then c_warn "旧机 $OLD_IP:22 仍可连通"
    else c_ok "旧机 $OLD_IP:22 不可连通（可能已释放）"; fi
  else
    c_info "未装 nc，跳过（可手动: ssh root@$OLD_IP 'echo alive'）"
  fi

  hdr "体检结束"
  c_info "如有 [XX]/[!!]，处理后重跑： bash $0 check"
}

# ---------------------------------------------------------------- clean
do_clean() {
  hdr "清理迁移包残留"
  [ "$(id -u)" -eq 0 ] || { c_bad "需要 root 权限"; exit 1; }

  local n=0
  for f in /tmp/blog-migrate-*.tar.gz /tmp/blog-migrate-* /tmp/migrate \
           /tmp/blog.sql.gz /tmp/blog.sql /tmp/output.tar.gz; do
    [ -e "$f" ] || continue
    c_info "清理: $f"
    rm -rf -- "$f" && n=$((n+1))
  done

  # 覆盖写再删，降低磁盘层面的恢复可能（针对仍存在的迁移包）
  c_info "执行安全覆写（降低残留可恢复性）..."
  for f in /tmp/blog-migrate-*.tar.gz; do
    [ -e "$f" ] || continue
    shred -u -- "$f" 2>/dev/null || rm -f -- "$f"
  done

  if [ "$n" -gt 0 ]; then c_ok "已清理 $n 项"; else c_ok "无需清理（已干净）"; fi

  hdr "再次确认"
  local left=0
  for f in /tmp/blog-migrate-* /tmp/migrate /tmp/blog.sql.gz; do
    [ -e "$f" ] || continue; c_bad "仍存在: $f"; left=1
  done
  [ "$left" -eq 0 ] && c_ok "/tmp 已无迁移相关残留"
  c_warn "提醒：旧机 $OLD_IP 上的同类文件需在旧机上单独清理（见本脚本随附说明）"
}

# ---------------------------------------------------------------- rotate
do_rotate() {
  hdr "生成轮换用新凭据（只生成，不自动替换）"
  cat <<'TIP'
  轮换原则：
    1) 先改服务端配置 → 重启 → 验证；确认无误再作废旧值
    2) JWT_SECRET 一改，已签发的 token 全部失效（用户需重新登录）—— 属预期行为
    3) RAG_API_TOKEN 必须【同时】改两处：/opt/blog/blog.env 与 /opt/blog/rag/backend/.env
    4) DB 密码改了要同步三处：application-prod.yml、blog.env、backup.env
    5) OSS AK/SK 建议在阿里云控制台新建一对，再替换、验证、最后禁用旧的
TIP

  echo
  c_info "可用的生成命令（按需复制使用）："
  printf '\n'
  printf '  # JWT_SECRET（HS256 要求 >=32 字节）\n'
  printf '  openssl rand -base64 48\n\n'
  printf '  # RAG_API_TOKEN\n'
  printf '  openssl rand -hex 32\n\n'
  printf '  # DB 密码（无特殊字符，避免 shell/YAML 转义问题）\n'
  printf '  openssl rand -hex 24\n\n'
  printf '  # Redis 密码\n'
  printf '  openssl rand -hex 24\n\n'

  c_warn "注意：RAG_API_TOKEN 与 DB_PASSWORD 若含特殊字符，"
  c_warn "      需确认在 .env（不带引号）与 YAML 中的书写方式，hex 可完全规避该问题。"
}

# ---------------------------------------------------------------- main
case "${1:-check}" in
  check)  do_check  ;;
  clean)  do_clean  ;;
  rotate) do_rotate ;;
  *) echo "用法: bash $0 [check|clean|rotate]"; exit 1 ;;
esac
