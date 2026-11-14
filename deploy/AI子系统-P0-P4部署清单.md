# AI 子系统（P0–P4）部署清单

一次部署覆盖四批改动：Agent 化（P0/P1）→ 模型注册表与后台两页（P2）→ 联网搜索（P3）→ 降级链与预算熔断（P4）。
**本次不部署鸿蒙 App**（用户已确认暂停，两个隐私政策文件留在本地不动）。

> 本机已打好 jar：`blog-server/blog-front-api/target/blog-front-api-1.0.0.jar`（80 MB，已核实含 `AiBudgetService`）。
> 本机**不能**打包前端（`nuxi build` 被系统策略拦），前端必须在服务器上构建。

---

## 0. 动手前先确认三件事（别猜路径）

```bash
# ① 三个服务的单元、工作目录、启动命令
systemctl cat blog-api blog-web rag 2>/dev/null | grep -E "^# |WorkingDirectory|ExecStart"

# ② 代码仓库在哪（后面所有 cd 都用它）
ls -d /opt/blog && find / -maxdepth 3 -name "blog-server" -type d 2>/dev/null | head

# ③ 记下当前版本，回滚要用
md5sum /opt/blog/blog-api.jar
ls -ld /opt/blog/web/.output
```

把 ①② 的输出对一下。本机已有服务器实测输出，**这三条 unit 结构是对的**：

```
blog-api : WorkingDirectory=/opt/blog
           ExecStart=/usr/bin/java -jar /opt/blog/blog-api.jar --spring.profiles.active=prod --spring.config.location=/opt/blog/application-prod.yml
blog-web : WorkingDirectory=/opt/blog/web/.output
           ExecStart=/bin/sh -c 'exec /usr/bin/node server/index.mjs >> /opt/blog/logs/blog-web.log 2>&1'
rag      : WorkingDirectory=/opt/blog/rag/backend
           ExecStart=/opt/blog/rag/backend/.venv/bin/python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

> ⚠️ **本文所有形如 `<REPO>` 的尖括号都是占位符，不能原样粘贴执行**（会报 `syntax error near unexpected token '&&'`）。
> 下文的 `<REPO>` 一律替换成下面这一步 `echo` 出来的真实路径。

生产路径全部写死，只剩一个需要现场确认：**git 仓库根目录**（不是 `/opt/blog`，两个可能不同）。先看服务器上到底有没有仓库：

```bash
# ① 先看 /opt/blog 下面有什么
ls -1 /opt/blog

# ② 找 .git 目录（有 .git 才谈得上 git pull）
find /opt /root /home -maxdepth 4 -name .git -type d 2>/dev/null

# ③ 顺手记下 venv 大小（决定备份要不要排除它）
du -sh /opt/blog/rag/backend/.venv 2>/dev/null
```

- **② 有输出**（比如 `/opt/blog/blog-src/.git`）→ `REPO=/opt/blog/blog-src`，可以用 `git pull`。
- **② 没输出**（服务器上压根没有仓库，代码是 Xftp 传的）→ 只能用 Xftp 上传。
  这时跳过 `git pull`，改成从本机传这两个包（下面第 1 节末尾给了打包命令），`REPO` 概念就不需要了。

确认后自己填一遍，后面就不用再改：

```bash
REPO=/opt/blog/blog-src          # ← 改成 ② 的真实结果，没有仓库就忽略这一行
cd "$REPO" && git log --oneline -1
```

| 用途 | 实际路径 |
|---|---|
| RAG 生产代码目录 | `/opt/blog/rag/backend` |
| RAG 虚拟环境 | `/opt/blog/rag/backend/.venv/bin/python` |
| RAG 数据目录 | `/opt/blog/rag/backend/data/`（`chroma/` 向量库 + `uploads/` PDF + `rag.db`） |

> ⚠️ **两条红线，先记住再往下看**
> 1. **`/opt/blog/rag/backend` ≠ 仓库里的 `rag_system/backend`**。这俩是两套目录，`git pull` 只更新仓库，生产跑的是前者，必须额外拷 `.py`（第 4 节给了命令）。
> 2. **`data/` 在代码目录里面**，所以「删掉整个 backend 再还原」= 连向量库一起删。第 9 节回滚命令已改成只回滚 `.py`，千万别手抖 `rm -rf` 整个目录。

先确认数据到底落在哪（万一服务器 `.env` 里设了 `RAG_DATA_DIR` 指到别处）：

```bash
sudo grep -E '^RAG_DATA_DIR=' /opt/blog/rag/backend/.env || echo "未设置 RAG_DATA_DIR → 数据在 /opt/blog/rag/backend/data"
sudo du -sh /opt/blog/rag/backend/data 2>/dev/null
```

> 已实测：服务器上 `RAG_DATA_DIR` **未设置**，数据在 `/opt/blog/rag/backend/data`，**5.7M**。
> 体积不大，但它是唯一的向量库副本（重新索引要把所有文章重跑一遍 embedding，费钱又费时），
> 所以下面所有命令都按"`data/` 绝对不能碰"来写。

---

## 1. 同步代码

> **本次实际情况（已核实）**：服务器上 `/opt /root /home` 下**没有任何 `.git` 目录**，代码是上传的，所以
> `git pull` 这条路不存在。**直接跳到 1.2**，用打好的包上传。1.1 那节只在将来服务器建了仓库时才有用。

方式自选：`git pull`，或 Xftp 覆盖上传。**只同步这些，不要整仓覆盖**（会冲掉服务器上的 `.env`、`application-prod.yml`）：

| 路径 | 说明 |
|---|---|
| `blog-server/blog-front-api/` `blog-server/blog-common/` | Java 源码（若直接在服务器跑 Maven） |
| `blog-server/sql/agent.sql`、`ai-model.sql` | 本次要执行的两个建表脚本 |
| `rag_system/backend/*.py`（**只拷 py 文件**，见下方命令） | `web_search.py` 是新增，`model_registry.py`、`usage_reporter.py` 也是 |
| `blog-web/`（**保留 `.env`**） | 源码 + `package.json` / `package-lock.json`（新增了 echarts） |
| `deploy/nginx.conf` | P0 加的 `/internal/` deny，不更新就少一层防护 |

同步完自检（四个文件必须都在仓库里）：

```bash
cd <REPO>
git log --oneline -1
ls -l blog-server/sql/agent.sql blog-server/sql/ai-model.sql \
      rag_system/backend/web_search.py \
      blog-server/blog-front-api/src/main/java/com/blog/front/service/AiBudgetService.java
```

### 1.1 仓库 → 生产目录（RAG 专用，别跳过）

生产 RAG 跑在 `/opt/blog/rag/backend`，仓库里是 `rag_system/backend`，**必须显式同步一次**。
用 `rsync` 白名单式地只推 `.py`，`.env` / `data/` / `.venv` 一个字都不碰：

```bash
cd <REPO>
sudo rsync -av --include='*.py' --exclude='*' \
     rag_system/backend/ /opt/blog/rag/backend/

# 核对：新文件到位了没
ls -l /opt/blog/rag/backend/web_search.py /opt/blog/rag/backend/model_registry.py
# 核对：数据没被动过（记录条数，部署后再比一次；数字变化是正常的，路径丢光才是事故）
sudo du -sh /opt/blog/rag/backend/data 
sudo ls /opt/blog/rag/backend/data
```

> 两个细节：
> 1. **`--include` 必须写在 `--exclude` 前面**。rsync 按顺序匹配，`--exclude='*'` 放前面会把一切先排掉，结果什么也不传。
> 2. `-a` 已含递归（`-r`），真正的保护来自 `--exclude='*'`：它连 `data/`、`.venv/` 这两个**目录名本身**都匹配掉，
>    rsync 不会进入它们内部。所以向量库和虚拟环境一个字节都不会被动。
>
> 想先看会传哪些、不实际动手（强烈建议先跑一遍）：
> ```bash
> rsync -avn --include='*.py' --exclude='*' rag_system/backend/ /opt/blog/rag/backend/ | head -30
> ```
>
> **没有 `rsync` 也能装**（阿里云 Linux 3：`sudo dnf install -y rsync`），或者用下面的 1.2 直接上传。

### 1.2 服务器上没有 git 仓库怎么办（Xftp 上传）

如果第 0 步 ② 没找到 `.git`，说明代码是传上去的，那就**只在本地打包、只传改动的文件**。
本机已经打好了两个包，直接用 Xftp 拖：

| 本地文件 | 大小 | 传到服务器后 |
|---|---|---|
| `D:\cxdownload\_deploy\rag-py.tgz` | 38 KB | `/root/` 下解压到 `/opt/blog/rag/backend/` |
| `D:\cxdownload\_deploy\web-src.tgz` | 152 KB | 覆盖到前端源码目录（就是 `npm run build` 的目录） |
| `blog-server/blog-front-api/target/blog-front-api-1.0.0.jar` | 80 MB | `/opt/blog/blog-api.jar` |
| `blog-server/sql/agent.sql`、`ai-model.sql` | 各几 KB | 随便放，`mysql <` 执行完就删 |

**RAG 包**只含 13 个 `.py`，**不含 `data/`、`.venv/`、`.env`**（已核对过包内容）：

```bash
# 上传后在服务器执行
cd /opt/blog/rag/backend
sudo tar -xzf /root/rag-py.tgz            # 平铺解压，覆盖同名 .py
ls -l web_search.py model_registry.py usage_reporter.py agent.py agent_tools.py
sudo du -sh data                          # 应仍是 5.7M（本机已知值），没变就说明没误伤
```

**前端包**含 15 项（7 改 + 8 新）：

```bash
cd <前端源码目录>          # 里面有 blog-web/package.json 的那一层
tar -xzf /root/web-src.tgz
```

> 两个包**都不含 `.env`**，所以服务器上的配置不会被冲掉。
> 重新打包命令（万一要重来）：
> ```bash
> # 本机 Git Bash
> cd /d/cxdownload/Blog/rag_system/backend && tar -czf /d/cxdownload/_deploy/rag-py.tgz $(ls *.py | grep -v agent_selftest)
> cd /d/cxdownload/Blog && tar -czf /d/cxdownload/_deploy/web-src.tgz blog-web/package.json blog-web/package-lock.json blog-web/components/{AiFloat,ArticleDetail,AiEcharts}.vue blog-web/composables/useAiChat.ts blog-web/layouts/admin.vue blog-web/pages/privacy.vue blog-web/pages/admin/ai blog-web/utils/{aiChartOptions,chartTheme,echarts,externalLink}.ts
> ```
> 为什么排除 `agent_selftest.py`：那是 34 条断言的自测脚本，生产用不上，传上去只是多一个文件。

---

## 2. 数据库（两个脚本，顺序无关，都幂等）

```bash
cd <REPO>
mysql -uroot -p blog < blog-server/sql/agent.sql      # agent_trace + ai_usage_daily
mysql -uroot -p blog < blog-server/sql/ai-model.sql   # ai_model + ai_setting + 种子模型
```

验证：

```bash
mysql -uroot -p blog -e "
SELECT id, display_name, model_id, api_key_env, input_price, output_price FROM ai_model;
SELECT id, default_model_id, fallback_model_id, global_daily_budget, daily_limit FROM ai_setting;"
```

期望：`ai_model` 有一行 `DeepSeek 对话`（`api_key_env=DEEPSEEK_API_KEY`），`ai_setting` 有一行 `id=1`。

---

## 3. 后端

```bash
# 备份（回滚用）；-a 保留属主/权限，别用裸 cp
sudo cp -a /opt/blog/blog-api.jar /opt/blog/blog-api.jar.bak.$(date +%F-%H%M)
ls -l /opt/blog/blog-api.jar.bak.* | tail -3

# 方式 A：上传本机已打好的 jar（推荐，快且不占服务器 Maven）
#   本机执行：scp blog-server/blog-front-api/target/blog-front-api-1.0.0.jar root@47.116.138.65:/tmp/
sudo mv /tmp/blog-front-api-1.0.0.jar /opt/blog/blog-api.jar
# 属主跟随旧 jar，不写死用户名（服务器上没有 ecs-user 这个账号就报错）
LATEST=$(ls -t /opt/blog/blog-api.jar.bak.* | head -1)
sudo chown --reference="$LATEST" /opt/blog/blog-api.jar
ls -l /opt/blog/blog-api.jar

# 方式 B：在服务器上自己打（需要 Maven + JDK17）
#   cd <REPO> && sudo bash deploy/deploy.sh   ← 注意这条会连带构建+重启前端，见第 5 步

sudo systemctl restart blog-api
sleep 5
sudo systemctl status blog-api --no-pager -l | head -15
```

**这一步最容易挂**，三种典型：

| 现象 | 原因 |
|---|---|
| 启动报 `circular dependency` | 依赖环（本次已人工核对无环，出现请贴日志） |
| 日志出现 `Failed to configure a DataSource` | `/opt/blog/blog.env` 里的库密码不对 |
| 80 MB jar 传了但行为没变 | jar 名或路径不对 —— `blog-api.service` 的 `ExecStart` 必须指向 `/opt/blog/blog-api.jar` |

健康检查：

```bash
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/article/list?page=1
```

---

## 4. RAG（Python）

```bash
cd /opt/blog/rag/backend      # = rag.service 的 WorkingDirectory

# ---- ① 备份：只备 .py 和 .env，**绝对不要 cp -r .**
# 实测 .venv = 561M、data = 5.7M，整目录备份纯属浪费，而且 cp 途中出错更难收拾
sudo mkdir -p /opt/blog/rag-backup-$(date +%F)
sudo cp -a /opt/blog/rag/backend/*.py /opt/blog/rag-backup-$(date +%F)/
sudo cp -a /opt/blog/rag/backend/.env /opt/blog/rag-backup-$(date +%F)/.env.bak
du -sh /opt/blog/rag-backup-$(date +%F)     # 几十 KB 才对

# ---- ② 覆盖新代码 ----
# 本次走 1.2 的 tar 包（服务器无仓库）；将来有仓库了换成 1.1 的 rsync
cd /opt/blog/rag/backend
sudo tar -xzf /root/rag-py.tgz
ls -l web_search.py agent.py agent_tools.py model_registry.py usage_reporter.py

# ---- ③ 确认数据目录没被碰（必须是 5.7M，目录里应有 chroma/ uploads/ rag.db）----
du -sh data && ls -1 data

# ---- ④ 语法预检：不 import、不连库、不启动服务，只验证能不能编译 ----
# 在重启前跑，把"起不来"的损失降到 0
.venv/bin/python -m py_compile *.py && echo "COMPILE_OK"
```

> ④ 是本次新增的前置检查。`py_compile` 只做语法编译，不会触发 `import chromadb` 之类的重活，
> 几秒钟出结果。若报 `SyntaxError`，说明文件传坏了，**这时候别重启**，直接 `ls -l` 核对大小或重传。

```bash
# ---- 环境变量：只补缺，不覆盖已有 ----
grep -q '^RAG_API_TOKEN=' .env || echo 'RAG_API_TOKEN=改成与Java侧完全一致的长随机串' >> .env

grep -q '^AGENT_TOOLS_ENABLED=' .env || cat >> .env <<'EOF'
# ---- Agent 循环（P0/P1）----
AGENT_TOOLS_ENABLED=1
AGENT_MAX_STEPS=5
AGENT_TOTAL_MS=240000
AGENT_TOOL_TIMEOUT=10
AGENT_TEMPERATURE_DECIDE=0.2
# ---- 联网搜索（P3）----
WEB_SEARCH_ENABLED=1
WEB_SEARCH_PROVIDERS=tavily,bocha
WEB_SEARCH_API_KEY=
WEB_SEARCH_TIMEOUT=3
WEB_SEARCH_CACHE_SECONDS=1800
WEB_SEARCH_MAX_CALLS=2
EOF
```

**重启前必须先对一次 token**（两边不一致 = Java 调 RAG 全部 401，而两个服务状态都是 `active`，极难查）：

```bash
grep RAG_API_TOKEN /opt/blog/blog.env
grep RAG_API_TOKEN /opt/blog/rag/backend/.env
```

两个值必须逐字符相同。任何一边没有这个变量时，`grep` 不会输出 —— 说明该侧未启用鉴权，
只要**两边同时为空**也是自洽的；一边有一边没有才是问题。

**先探联网能不能通**（还没有 key 时就能测，401 = 通、000 = 不通）：

```bash
curl -s -o /dev/null -w "tavily=%{http_code}\n" --max-time 10 \
  -X POST https://api.tavily.com/search -H "Content-Type: application/json" -d '{}'
```

- `401` → 可以继续用 tavily，去申请 key 填进 `WEB_SEARCH_API_KEY`
- `000` → 国内不通，把 `WEB_SEARCH_PROVIDERS` 改成 `bocha` 并申请博查的 key

> ⚠️ **不填 key 不会报错**，只是 `web_search` 工具永远不下发、助手永远不联网。
> 这是"静默不生效"，只能靠下面的 `/health` 判断。

```bash
sudo systemctl restart rag
sleep 4
curl -s localhost:8000/health
```

期望响应里有 `"webSearch":true`（没填 key 是 `false`）。

---

## 5. 前端（必须在服务器构建）

```bash
cd <REPO>/blog-web

# package.json 新增了 echarts，必须走 ci 而不是 install，否则依赖树可能不是锁文件那份
npm ci
npm run build

# ⚠️ 符号链接检查：必须为 0。Nitro 会建绝对路径软链，
# Windows 上打得出来、Linux 上全断，表现是 SSR 500 但 systemctl 显示 active
find .output -type l | wc -l

# 备份并替换
sudo rm -rf /opt/blog/web/.output.bak
sudo mv /opt/blog/web/.output /opt/blog/web/.output.bak
sudo mv .output /opt/blog/web/.output
# 属主跟随原 web 目录，不写死用户名
OWNER=$(stat -c '%U:%G' /opt/blog/web)
sudo chown -R "$OWNER" /opt/blog/web
ls -ld /opt/blog/web/.output

sudo systemctl restart blog-web
sleep 4
curl -s -o /dev/null -w "nuxt=%{http_code}\n" localhost:3000
```

`npm run build` 已内置 `scripts/fix-output-symlinks.mjs`，正常情况下 `find` 结果是 0。

---

## 6. Nginx（P0 的 `/internal/` 拦截）

```bash
# 先看现网配置在哪
nginx -T 2>/dev/null | grep -n "codeup.asia" | head -3

# 备份 → 对比 → 应用
sudo cp /etc/nginx/conf.d/blog.conf /etc/nginx/conf.d/blog.conf.bak.$(date +%F) 2>/dev/null
diff /etc/nginx/conf.d/blog.conf <REPO>/deploy/nginx.conf

# 确认 diff 只有 /internal/ 与 SSE 相关改动后再覆盖
sudo cp <REPO>/deploy/nginx.conf /etc/nginx/conf.d/blog.conf
sudo nginx -t && sudo nginx -s reload
```

外网必须拒绝（这两条都要 404/403）：

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://codeup.asia/internal/ai/model
```

---

## 7. 后台配置（3 项，漏一项功能就是死的）

登录 `/admin/ai/models`：

1. **填单价**（input / output，元每百万 token）。
   ⚠️ 单价填 0 → 所有用量金额恒为 0 → **预算熔断永远不会触发**（它读的是金额）。这是本次最容易漏的联动。
2. **新增第二个模型**（建议选**不同厂商**），对它点「设为降级」。
   同厂商一起挂的概率高，降级就白做了。
3. 打开 `/admin/ai/usage` → 设「全站日预算」。建议先按最近 7 天的日均花费 × 3~5 倍设，跑一周再收。

若要用联网：把 Tavily/博查的 key 填进 RAG 的 `.env` 并重启 RAG。

### 7.1 两个「不配也能跑，但配了才完整」的地方

**(1) `AI_KEY_SECRET` —— 决定后台能不能"直接填 Key"**

生产用的是 `--spring.config.location=/opt/blog/application-prod.yml`，这是**替换式**加载，
`application.yml` 里那批新配置（`ai.key-secret`、`ai.price`、`ai.limits`、`ai.model-code`）在生产**读不到**。

已逐个核对过：`AiModelService` / `AiQuotaService` / `AiUsageService` / `AiController` / `InternalAiController`
里 13 处 `@Value` **全部带默认值**（`${ai.key-secret:}`、`${ai.limits.minute:5}` ……），
所以**不会启动失败**，只是走代码默认值。

但默认值是空，后果是：`AiKeyCipher.available("")` = false → 后台点保存密钥会报
`未配置 ai.key-secret，无法加密密钥`。

- 只想用环境变量里的 key（`api_key_env=DEEPSEEK_API_KEY` 那种）→ **不用管**，照常能用。
- 想在后台直接贴 key（尤其是新增第二个厂商模型时）→ 必须先加这一行：

```bash
# 生成 32 字节 hex，并写进 blog.env（blog-api.service 的 EnvironmentFile 就是它）
grep -q '^AI_KEY_SECRET=' /opt/blog/blog.env || \
  echo "AI_KEY_SECRET=$(openssl rand -hex 32)" | sudo tee -a /opt/blog/blog.env
sudo systemctl restart blog-api
```

> ⚠️ **这个值设了就别再改**。密钥用它对入库数据做 AES-256-GCM 加密，换了以后已存的 key 全部解不开
> （`decrypt` 返回 null），后台得重新填一遍。

**(2) 顺手确认 `EnvironmentFile` 还在**

你贴的 `systemctl cat | grep ExecStart|WorkingDirectory` 把这两行滤掉了，但它们才是配置注入的命门：

```bash
systemctl cat blog-api | grep -E 'EnvironmentFile|^User='
# 期望：EnvironmentFile=/opt/blog/blog.env
#       User=ecs-user
```

`RAG_API_TOKEN`、`AI_KEY_SECRET`、`JWT_SECRET` 全从 `blog.env` 注入。这一行丢了，
Java 侧 `ai.rag-token` 就是空，前端 AI 直接 401，但服务状态照样 `active` —— 很难查。

> 顺带：`blog-api.service` 跑的是 `User=ecs-user`，不是 root。所以第 3 步替换 jar 后必须保证属主是 `ecs-user`，
> 否则 `Permission denied`。文档里已经改成 `chown --reference` 跟随旧 jar，不会写错。

---

## 8. 验收（8 条，按顺序）

| # | 操作 | 期望 |
|---|---|---|
| 1 | 问「你写过关于 RAG 的文章吗」 | 出现工具轨迹 → 正文流式 → 来源卡片可点 |
| 2 | 来源卡片 | 站内=主题色 📄；站外=灰色 ↗ + 只显示域名 |
| 3 | 文章里选一段 → 「搜一下」 | 抽屉打开并回答；出现站外来源（前提是 key 已配） |
| 4 | 文章页「AI 总结本文」（挑一篇长的） | 不再失败；过长时开头注明「本次仅基于前 18000 字」 |
| 5 | `/admin/ai/usage` | 4 张卡有数；折线是陶土色；有金额（不是全 0） |
| 6 | 把主模型 key 改错再提问 | 仍能回答，下方出现「主模型暂时不可用，本条回答由备用模型生成」 |
| 7 | 预算设 `0.01` 连问两次 | 第二次被拒：「今天的 AI 服务总额度已用完，明天 0 点恢复。」 |
| 8 | 打开 `/privacy` | 版本 v2，有「七、AI 助手与第三方服务」 |

### 8.1 跑评测集（基线与每次改动前对比）

P5 的 20 题评测集，用来防「改 A 修好、B 悄悄变差」。**首次上线必须存一份基线**，
以后每次改 AI 代码都 `--compare` 这份基线。

```bash
# 传 eval 包（9.4 KB）
# 本机：scp D:\cxdownload\_deploy\eval.tgz root@47.116.138.65:/root/
cd /opt/blog/rag && tar -xzf /root/eval.tgz && ls eval/

cd /opt/blog/rag/eval
../backend/.venv/bin/python run_eval.py 2>&1 | tail -40
```

20 题大约跑 3~6 分钟。判读：

| 指标 | 目标 | 不达标说明 |
|---|---|---|
| 通过率 | 16/20 以上 | 失败的题看报告里的 `fails`，逐条对 |
| P50 首 token | < 3000 ms | 慢多半是检索或首轮决策拖的 |
| 平均步数 | < 2.2 | 偏高说明工具描述让模型在乱试 |
| 工具失败率 | < 3% | 偏高查工具网关和参数 schema |

⚠️ **评测不记账**（不传 `X-AI-User-Id`，`_settle` 直接 return），所以不会污染
`ai_usage_daily`，也不会触发预算熔断。但它**确实消耗 token**，20 题约 3~8 万，
不到 0.5 元。

首次基线存好后，以后对比：

```bash
../backend/.venv/bin/python run_eval.py --compare reports/eval-<基线时间>.json
```

`web` 两题在没配 `WEB_SEARCH_API_KEY` 时会自动跳过，不判失败。

---

## 9. 回滚

```bash
# 后端
sudo cp /opt/blog/blog-api.jar.bak.<时间戳> /opt/blog/blog-api.jar
sudo systemctl restart blog-api

# 前端
sudo rm -rf /opt/blog/web/.output
sudo mv /opt/blog/web/.output.bak /opt/blog/web/.output
sudo systemctl restart blog-web

# RAG：只覆盖 .py，绝不 rm 整个目录
# ⚠️ data/ 就在 /opt/blog/rag/backend 里面（chroma 向量库 + uploads + rag.db），
#    `rm -rf /opt/blog/rag/backend` = 所有已索引的 PDF 全部丢失，且不可恢复
sudo systemctl stop rag
sudo rsync -av --include='*.py' --exclude='*' \
     /opt/blog/rag-backup-<日期>/ /opt/blog/rag/backend/
# 如果第 4 节改过 .env，再把它还原回去：
# sudo cp /opt/blog/rag-backup-<日期>/.env.bak /opt/blog/rag/backend/.env
sudo systemctl start rag
```

回滚后目录里会残留本次**新增**的几个文件（`web_search.py`、`model_registry.py`、`usage_reporter.py`）。
**不用管它们**：旧代码不会 import，留着无害；删了也不影响，纯属洁癖：

```bash
# 可选，确认回滚成功后再执行
cd /opt/blog/rag/backend && sudo rm -f web_search.py model_registry.py usage_reporter.py
```

验证数据还在（这一步比看服务状态更重要）：

```bash
curl -s localhost:8000/health
sudo du -sh /opt/blog/rag/backend/data/chroma
```

---

**数据库不需要回滚**：本次只新增表（`agent_trace`、`ai_usage_daily`、`ai_model`、`ai_setting`），
旧代码不读它们，留着不影响运行。

---

## 附：本次涉及的配置项一览

| 位置 | 变量 | 必需 | 说明 |
|---|---|---|---|
| `/opt/blog/blog.env` | `AI_KEY_SECRET` | 建议 | 后台填 API Key 时需要（32 字节 hex）。**一旦设定不要再改**，改了已存密钥全部解不开 |
| `/opt/blog/blog.env` | `RAG_API_TOKEN` | 是 | 必须与 RAG `.env` 完全一致 |
| RAG `.env` | `RAG_API_TOKEN` | 是 | 同上 |
| RAG `.env` | `WEB_SEARCH_API_KEY` | 可选 | 不填 = 不联网（不报错） |
| RAG `.env` | `AGENT_TOOLS_ENABLED` | 否 | 设 0 可一键退回纯检索问答（排障用） |
| 后台 | 模型单价 | 是 | 不填 → 金额恒 0 → 预算熔断失效 |
| 后台 | 降级模型 | 否 | 不配 → 主模型失败直接报错 |
| 后台 | 全站日预算 | 否 | 0 = 不限 |
