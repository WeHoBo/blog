#!/usr/bin/env bash
# rag-sync.sh - 每日把站内文章同步进 RAG 向量库
#
# 部署位置：/opt/blog/rag/backend/rag-sync.sh（脚本内全是绝对路径，放哪都行）
# crontab：  0 3 * * * /bin/bash /opt/blog/rag/backend/rag-sync.sh >/dev/null 2>&1
#
# 设计取舍：
#   1. 用**全量重灌**（import_blog.py 的现有能力）而不是增量。
#      34 篇 / 1055 块，约 1~2 分钟、几十次 embedding 调用，成本可忽略；
#      增量需要额外维护「上次同步到哪」的状态，并单独处理「文章被删/转草稿」，
#      为省这点钱引入一个会出错的状态机不划算。
#   2. 跑之前先探 /health 的 embedding。key 失效时直接跳过并记一行日志 ——
#      否则会白跑一轮，还往日志里灌 34 条 failed。
#   3. 用 flock 防叠跑：上一轮没结束（大库可能跑几分钟）就跳过，不重复 embedding。
#   4. 密码从独立的 .sync.env 读，不写在命令行参数里（会出现在 ps 和 shell history）。
#
# ⚠️ 已知局限（不是 bug，写在这里免得以后踩）：
#   /ingest-articles 是「删掉这篇的旧块再重新写」，没有传进来的文章（已删除、转草稿、
#   进回收站）的旧块会**留在库里**，形成指向不存在文章的"幽灵引用"。
#   目前站内文章量少、没有大规模删过，暂时不影响；真要清理需要后端加一个 prune 接口。
set -uo pipefail

APP_DIR="${APP_DIR:-/opt/blog/rag/backend}"
PY="$APP_DIR/.venv/bin/python"
ENV_FILE="$APP_DIR/.env"
SYNC_ENV="$APP_DIR/.sync.env"
LOG_DIR="${LOG_DIR:-/opt/blog/logs}"
LOG="$LOG_DIR/rag-sync.log"
LOCK="/tmp/rag-sync.lock"

mkdir -p "$LOG_DIR" || exit 1
log() { echo "$(date '+%F %T') $*" >> "$LOG"; }

# 日志别无限长：超过 5MB 归档一份（只留上一轮，够排查就行）
if [ -f "$LOG" ] && [ "$(stat -c%s "$LOG" 2>/dev/null || echo 0)" -gt 5242880 ]; then
  mv -f "$LOG" "$LOG.1"
fi

# 防叠跑：拿不到锁就说明上一轮还在
exec 9>"$LOCK"
if ! flock -n 9; then
  log "[跳过] 上一轮同步还在运行（锁 $LOCK）"
  exit 0
fi

if [ ! -x "$PY" ]; then
  log "[失败] 找不到解释器 $PY"
  exit 1
fi

# 令牌与数据库密码：都给「读不到」留出明确日志，不要静默用空值跑
RAG_TOKEN="$(sed -n 's/^RAG_API_TOKEN=//p' "$ENV_FILE" 2>/dev/null | head -1 | tr -d '"\r')"
DB_PASS=""
if [ -f "$SYNC_ENV" ]; then
  DB_PASS="$(sed -n 's/^BLOG_DB_PASSWORD=//p' "$SYNC_ENV" 2>/dev/null | head -1 | tr -d '"\r')"
fi
if [ -z "$DB_PASS" ]; then
  log "[失败] 读不到数据库密码：请在 $SYNC_ENV 写一行 BLOG_DB_PASSWORD=xxx，并 chmod 600"
  exit 1
fi

# 先探活 embedding：key 失效时语义检索本来就是全废的，同步也没有意义
# ⚠️ 请求头必须用数组，不能写成 ${RAG_TOKEN:+-H "X-RAG-Token: $TOKEN"} ——
#    那种展开里的引号不会生效，curl 会收到带引号的字面参数，最终 401。
CURL=(curl -s --noproxy '*' --max-time 20)
if [ -n "$RAG_TOKEN" ]; then
  CURL+=(-H "X-RAG-Token: $RAG_TOKEN")
fi
HEALTH="$("${CURL[@]}" 'http://127.0.0.1:8000/health?probe=1' 2>/dev/null || true)"
if [ -z "$HEALTH" ]; then
  log "[失败] /health 无响应，RAG 服务可能没起；本轮跳过"
  exit 1
fi
# 用 JSON 解析而不是 grep 字符串：字段顺序、空格、换行都不该让判断失效
EMBED_OK="$(printf '%s' "$HEALTH" | "$PY" -c \
  'import json,sys;print(1 if (json.load(sys.stdin).get("embedding") or {}).get("ok") is True else 0)' \
  2>/dev/null || echo 0)"
if [ "$EMBED_OK" != "1" ]; then
  log "[跳过] embedding 不可用，本轮不同步（先用 /health?probe=1 看清楚，多半是 EMBEDDING_API_KEY）"
  log "       当前 /health: $(printf '%s' "$HEALTH" | head -c 300)"
  exit 0
fi

cd "$APP_DIR" || exit 1
log "===== 开始同步 ====="

OUT="$(BLOG_DB_PASSWORD="$DB_PASS" "$PY" "$APP_DIR/import_blog.py" 2>&1)"
RC=$?
printf '%s\n' "$OUT" >> "$LOG"

if [ "$RC" -ne 0 ]; then
  # import_blog.py 在 failed 非空时以 1 退出，所以这里同时覆盖"跑挂"和"有篇失败"
  log "[失败] 退出码 $RC —— 有文章没入库，往上翻 failed / 报错原文"
  exit 1
fi

# 正常结束时最后一行长这样：[完成] 共 34 篇文章, 1055 个文本块已入库, 失败 0 篇
log "$(printf '%s\n' "$OUT" | tail -1)"
exit 0
