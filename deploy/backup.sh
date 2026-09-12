#!/usr/bin/env bash
#
# 好啵博客 —— 每日备份脚本
# 一次备份三样：MySQL 数据库 + 上传目录 + RAG 向量数据
#
# 用法：
#   sudo ./backup.sh
#   # 或用配置文件覆盖默认值（推荐，避免密码出现在命令行/ps 里）：
#   sudo bash -c 'set -a; . /opt/blog/backup.env; set +a; ./backup.sh'
#
# 建议由 root 的 cron 调度（见文件末尾注释）。

set -euo pipefail

# ---------------- 可配置项（同名环境变量可覆盖） ----------------
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-blog}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

UPLOAD_DIR="${UPLOAD_DIR:-/opt/blog/uploads}"
RAG_DATA_DIR="${RAG_DATA_DIR:-/opt/blog/rag/data}"

BACKUP_ROOT="${BACKUP_ROOT:-/opt/blog/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"

# 可选：MySQL 凭据文件。存在且未显式给密码时优先用它（比 -p 更安全）。
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-/opt/blog/.my.cnf}"
# -----------------------------------------------------------

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] 错误：$*" >&2; exit 1; }

# 安全护栏：绝不允许把备份根目录指到系统关键路径
case "$BACKUP_ROOT" in
  ""|"/"|"/opt"|"/opt/blog"|"$HOME") die "BACKUP_ROOT 配置不安全：'$BACKUP_ROOT'" ;;
esac
case "$BACKUP_ROOT" in
  /*) : ;;
  *) die "BACKUP_ROOT 必须是绝对路径：'$BACKUP_ROOT'" ;;
esac

command -v mysqldump >/dev/null 2>&1 || die "未找到 mysqldump，请安装 mysql-client"
command -v gzip      >/dev/null 2>&1 || die "未找到 gzip"
command -v tar       >/dev/null 2>&1 || die "未找到 tar"

STAMP="$(date +%F_%H%M%S)"
DEST="${BACKUP_ROOT}/${STAMP}"
mkdir -p "$DEST"
chmod 700 "$DEST"

# 组装 mysqldump 连接参数。
# 注意：--defaults-extra-file 必须是命令行的第一个选项，否则 mysqldump 直接报错；
# 因此连接参数（MYSQL_CONN）与行为参数分开拼装，最后按「连接参数在前」合并。
MYSQL_CONN=()
if [ -z "$DB_PASSWORD" ] && [ -f "$MYSQL_DEFAULTS_FILE" ]; then
  MYSQL_CONN+=("--defaults-extra-file=${MYSQL_DEFAULTS_FILE}")
else
  if [ -z "$DB_PASSWORD" ]; then
    log "警告：未提供 DB_PASSWORD 且未找到 ${MYSQL_DEFAULTS_FILE}；mysqldump 可能要求交互输入（cron 下会失败）"
  fi
  MYSQL_CONN+=("-h" "$DB_HOST" "-P" "$DB_PORT" "-u" "$DB_USER")
  [ -n "$DB_PASSWORD" ] && MYSQL_CONN+=("-p${DB_PASSWORD}")
fi
MYSQL_ARGS=("${MYSQL_CONN[@]}" --single-transaction --quick --routines --events --default-character-set=utf8mb4)

log "开始备份 → ${DEST}"

# 1) 数据库
log "[1/3] 导出数据库 ${DB_NAME} ..."
mysqldump "${MYSQL_ARGS[@]}" "$DB_NAME" | gzip -c > "${DEST}/db-${DB_NAME}.sql.gz"
[ -s "${DEST}/db-${DB_NAME}.sql.gz" ] || die "数据库备份产出为空：${DEST}/db-${DB_NAME}.sql.gz"

# 2) 上传目录
log "[2/3] 打包上传目录 ${UPLOAD_DIR} ..."
if [ -d "$UPLOAD_DIR" ]; then
  tar -czf "${DEST}/uploads.tar.gz" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
else
  log "跳过：${UPLOAD_DIR} 不存在（若图片已全部上 OSS，属正常）"
fi

# 3) RAG 向量数据
log "[3/3] 打包 RAG 数据 ${RAG_DATA_DIR} ..."
if [ -d "$RAG_DATA_DIR" ]; then
  tar -czf "${DEST}/rag-data.tar.gz" -C "$(dirname "$RAG_DATA_DIR")" "$(basename "$RAG_DATA_DIR")"
else
  log "跳过：${RAG_DATA_DIR} 不存在（RAG 未部署或路径不同，可用 RAG_DATA_DIR 覆盖）"
fi

# 清单
{
  echo "备份时间: $(date '+%F %T')"
  echo "主机名  : $(hostname)"
  echo "数据库  : ${DB_NAME}@${DB_HOST}:${DB_PORT}"
  echo "上传目录: ${UPLOAD_DIR}"
  echo "RAG 数据: ${RAG_DATA_DIR}"
  echo "--- 产出文件 ---"
  ls -lh "$DEST"
} > "${DEST}/MANIFEST.txt"

log "备份完成，总大小：$(du -sh "$DEST" | cut -f1)"

# 清理过期备份（只删符合时间戳命名的目录，避免误删）
log "清理 ${KEEP_DAYS} 天前的备份 ..."
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d \
  -name '20[0-9][0-9]-[0-9][0-9]-[0-9][0-9]_*' -mtime "+${KEEP_DAYS}" \
  -print -exec rm -rf {} + || true

log "当前保留的备份："
ls -1t "$BACKUP_ROOT" | head -20

# ------------------------------------------------------------------
# 加入 cron（root 身份，每天 03:17 执行；错开整点避免与其他任务挤在一起）：
#
#   sudo cp backup.sh /opt/blog/backup.sh && sudo chmod 700 /opt/blog/backup.sh
#   sudo crontab -e
#   # 加入下面一行（日志另存，方便回溯）：
#   17 3 * * * bash -c 'set -a; . /opt/blog/backup.env; set +a; /opt/blog/backup.sh' >> /opt/blog/backups/backup.log 2>&1
#
# backup.env 内容示例（chmod 600）：
#   DB_NAME=blog
#   DB_USER=root
#   DB_PASSWORD=你的库密码
#   BACKUP_ROOT=/opt/blog/backups
#   KEEP_DAYS=7
#   RAG_DATA_DIR=/opt/blog/rag/data
# ------------------------------------------------------------------
