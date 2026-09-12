# 好啵博客 部署指南

## 服务器要求

- CentOS 7+ / Ubuntu 20.04+
- Java 17+
- Node.js 20+
- MySQL 8.0
- Redis 7
- Nginx
- Maven 3.9+
- 1核2G 最低配置

## 部署步骤

### 1. 安装基础环境

```bash
# CentOS
sudo yum install -y java-17-openjdk nginx redis mysql-server

# Ubuntu
sudo apt install -y openjdk-17-jdk nginx redis-server mysql-server
```

### 2. 安装 Node.js 20

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

### 3. 启动 Docker 中间件（推荐）

```bash
docker-compose up -d mysql redis
```

或手动安装启动 MySQL/Redis。

### 4. 初始化数据库

**全新安装**（`blog.sql` 已是整合后的完整 schema，包含全部列与索引）：

```bash
mysql -u root -p < blog-server/sql/blog.sql
```

**已经跑起来的库**（只需补新增的索引 / 唯一约束）：

```bash
# 先让应用正常启动过一次（WordCountMigrator 会自动补 article.word_count、user.huawei_id）
mysql -u root -p < blog-server/sql/perf-index.sql
```

> - 原 `encrypt-alter.sql` / `visibility-alter.sql` 已随「私密文章功能下线」删除，**不要再执行**。
> - `perf-index.sql` 可重复执行，报 `Duplicate key name` 属预期，忽略即可。
> - `oauth-alter.sql` / `series-alter.sql` / `operation-log.sql` 仅用于很旧的历史库，
>   内容已全部并入 `blog.sql`，重复执行会报「列已存在」。

### 5. 创建应用目录

```bash
sudo mkdir -p /opt/blog/uploads
sudo chmod 755 /opt/blog
```

### 6. 构建项目

```bash
# 后端
cd blog-server
mvn clean package -DskipTests -pl blog-front-api -am

# 前端
cd blog-web
npm install
npm run build
```

### 7. 部署后端

```bash
# 复制 JAR
cp blog-server/blog-front-api/target/blog-front-api-1.0.0.jar /opt/blog/blog-api.jar

# 复制生产配置
cp deploy/application-prod.yml /opt/blog/application-prod.yml

# 注册系统服务
sudo cp deploy/blog-api.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable blog-api
sudo systemctl start blog-api
```

### 7.1 生产配置与环境变量（重要）

生产启动命令是
`java -jar /opt/blog/blog-api.jar --spring.profiles.active=prod --spring.config.location=/opt/blog/application-prod.yml`。

注意用的是 `spring.config.location`（**替换**默认位置，不是追加），
所以 jar 内的 `application.yml` 在生产**不会**被读取 —— 所有配置都必须写在 `/opt/blog/application-prod.yml`。

敏感值走 `/opt/blog/blog.env`（由 `EnvironmentFile` 注入），至少包含：

| 变量 | 说明 |
| --- | --- |
| `DB_PASSWORD` | MySQL 密码 |
| `JWT_SECRET` | JWT 密钥，**必须 ≥ 32 字节**（HS256 要求，过短启动即抛 WeakKeyException） |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth |
| `GITEE_CLIENT_ID` / `GITEE_CLIENT_SECRET` | Gitee OAuth |
| `HUAWEI_CLIENT_ID` / `HUAWEI_CLIENT_SECRET` | 华为 OAuth |
| `RAG_API_TOKEN` | **新增**，需与 RAG 侧 `.env` 完全一致 |
| `CORS_ALLOWED_ORIGINS` | 可选，逗号分隔；不配则用代码内默认值 |

对应 key 需写进 `/opt/blog/application-prod.yml`：

```yaml
ai:
  rag-url: http://127.0.0.1:8000
  rag-token: ${RAG_API_TOKEN:}

app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:https://codeup.asia,https://www.codeup.asia}
```

> 这三个 key 在 Java 侧都有内联默认值，不配也能启动；
> 但 **不配 `ai.rag-token` 就等于 RAG 没有鉴权**（见下一步）。

### 7.2 部署 RAG 子系统（与后端同机时）

```bash
cd rag_system/backend
# 在 .env 中加上与上面完全一致的令牌
echo 'RAG_API_TOKEN=你的随机长串' >> .env
# uvicorn 已改为绑定 127.0.0.1:8000、reload=False，只允许同机 Java 调用
python main.py
```

> - Python 侧 `RAG_API_TOKEN` 为空时会**跳过校验**并打印启动警告 → 必须两侧配同一个值才真正生效。
> - 绑 `127.0.0.1` 意味着 **RAG 必须与 Java 后端同机**；若需分机部署，请改回内网地址并自行加网络层防护。

### 8. 部署前端

前端是 **Nuxt SSR**：`nginx.conf` 的 `location /` 反代到 `127.0.0.1:3000`，
真正对外服务的是 `blog-web.service` 拉起的 Nuxt 进程，所以要部署的是整个 `.output` 目录。

```bash
# 1) 部署 SSR 产物
sudo mkdir -p /opt/blog/web
sudo rm -rf /opt/blog/web/.output
sudo cp -r blog-web/.output /opt/blog/web/.output
sudo chown -R ecs-user:ecs-user /opt/blog/web

# 2) 注册并启动前端服务
sudo cp deploy/blog-web.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable blog-web
sudo systemctl restart blog-web

# 3) Nginx 配置
sudo cp deploy/nginx.conf /etc/nginx/conf.d/blog.conf
sudo nginx -t   # 检查配置
sudo systemctl restart nginx
```

> ⚠️ 只把 `.output/public` 拷到 nginx 静态目录是**无效的**：`nginx.conf` 里没有任何
> `root` / 静态目录引用，请求全部走反代 → 前端改动不会生效（这是旧脚本的历史坑）。
> `deploy/deploy.sh` 已按正确方式重写（含 `blog-web` 的部署与重启）。

### 9. 配置 OAuth 回调地址

到你申请的 GitHub/Gitee OAuth App 里，把回调地址从 `localhost:8080` 改成：

```
https://你的域名/api/auth/oauth/github/callback
https://你的域名/api/auth/oauth/gitee/callback
```

### 10. 验证

```bash
# 检查后端
curl http://localhost:8080/api/article/list

# 检查前端
curl http://localhost/

# 查看日志
sudo journalctl -u blog-api -f
```

## 常用维护命令

```bash
sudo systemctl status blog-api     # 查看后端状态
sudo systemctl status blog-web     # 查看前端状态
sudo systemctl restart blog-api    # 重启后端
sudo systemctl restart blog-web    # 重启前端
sudo systemctl restart nginx       # 重启 Nginx
sudo journalctl -u blog-api -f     # 实时查看后端日志
sudo journalctl -u blog-web -f     # 实时查看前端日志

# 一键更新部署（构建后端 + 构建前端 + 部署 .output + 重启两个服务）
cd ~/Blog
git pull
./deploy/deploy.sh
```

> ⚠️ **`deploy/` 目录被 `.gitignore` 忽略**，`git pull` **不会**更新 `deploy.sh` /
> `nginx.conf` / `*.service`。这些文件改动后必须从本机手工拷到服务器（或直接改服务器上的文件）。

## 防火墙

```bash
sudo firewall-cmd --add-port=80/tcp --permanent
sudo firewall-cmd --add-port=443/tcp --permanent
sudo firewall-cmd --reload
```

## HTTPS 配置（推荐）

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```
