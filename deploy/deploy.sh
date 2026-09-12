#!/bin/bash
set -euo pipefail

# 无论从哪个目录调用，都切到脚本所在目录（deploy/），路径全部基于仓库根目录推导
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SCRIPT_DIR"

echo "=== 好啵博客 部署脚本 ==="

API_NAME="blog-api"
WEB_NAME="blog-web"
APP_USER="ecs-user"
APP_DIR="/opt/blog"
WEB_DIR="/opt/blog/web"
JAR_SRC="blog-server/blog-front-api/target/blog-front-api-1.0.0.jar"
# 注意：jar 落地名必须与 blog-api.service 的 ExecStart 完全一致，
# 否则 deploy.sh 拷贝的新包不会被服务加载（历史 bug：曾出现 blog-api.jar 与
# blog-front-api-1.0.0.jar 不一致，导致部署静默失效）。
JAR_DST="$APP_DIR/blog-api.jar"

if [ ! -d "$APP_DIR" ]; then
  echo "错误：应用目录 $APP_DIR 不存在。" >&2
  echo "请先创建该目录，并放入 blog.env 与 application-prod.yml 后再执行部署。" >&2
  exit 1
fi

command -v mvn >/dev/null 2>&1 || { echo "错误：未找到 mvn，请先安装 Maven 并配置 PATH。" >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "错误：未找到 npm，请先安装 Node.js。" >&2; exit 1; }

# 1. 构建后端
echo "[1/6] 构建后端..."
cd "$REPO_DIR"
mvn -f blog-server/pom.xml clean package -DskipTests -pl blog-front-api -am
cp "$REPO_DIR/$JAR_SRC" "$JAR_DST"

# 2. 构建前端
echo "[2/6] 构建前端..."
cd "$REPO_DIR/blog-web"
npm install
npm run build

# 3. 部署前端（Nuxt SSR 产物）
# 关键：nginx 的 location / 是反代到 127.0.0.1:3000，真正对外服务的是 blog-web.service
# 起的 Nuxt SSR 进程，所以必须更新 /opt/blog/web/.output 并重启 blog-web。
# （历史问题：旧脚本只把 .output/public 拷到 nginx 静态目录，而那个目录根本没被任何
#   location 使用 → 前端改动实际上从未上线。）
echo "[3/6] 部署前端 SSR 产物到 $WEB_DIR ..."
sudo mkdir -p "$WEB_DIR"
sudo rm -rf "$WEB_DIR/.output"
sudo cp -r "$REPO_DIR/blog-web/.output" "$WEB_DIR/.output"
sudo chown -R "$APP_USER":"$APP_USER" "$WEB_DIR"

# 4. 同步 systemd unit（ExecStart / jar 名 / 工作目录 变更后必须重新安装并 daemon-reload）
echo "[4/6] 同步 systemd unit..."
# 前置：日志目录必须先存在（logback 写 /opt/blog/logs，blog-web 的 shell 重定向也指向它）。
# 目录若不存在，blog-web 会因重定向失败而起不来。
sudo mkdir -p "$APP_DIR/logs"
sudo chown -R "$APP_USER":"$APP_USER" "$APP_DIR/logs"
sudo cp "$SCRIPT_DIR/blog-api.service" /etc/systemd/system/blog-api.service
sudo cp "$SCRIPT_DIR/blog-web.service" /etc/systemd/system/blog-web.service
sudo systemctl daemon-reload

# 5. 重启后端
echo "[5/6] 重启后端 $API_NAME ..."
sudo systemctl restart "$API_NAME"
sudo systemctl --no-pager --lines=0 status "$API_NAME" || true

# 6. 重启前端
echo "[6/6] 重启前端 $WEB_NAME ..."
sudo systemctl restart "$WEB_NAME"
sudo systemctl --no-pager --lines=0 status "$WEB_NAME" || true

echo ""
echo "=== 部署完成 ==="
echo "  API jar : $JAR_DST"
echo "  Web SSR : $WEB_DIR/.output"
echo "提醒：数据库索引变更请手动执行 blog-server/sql/perf-index.sql（仅首次需要）。"
