# OAuth 修复部署手册（提交 15c5844）

> 适用场景：把 `15c5844`（OAuth code 编码修复 + 失败原因可见）发布到生产服务器。
> 服务器：`root@47.116.138.65`（新机）· 应用目录 `/opt/blog` · 服务单元 `blog-api` / `blog-web`

---

## 0. 这次要部署什么

| 侧 | 文件 | 改动 |
|---|---|---|
| 后端 | `GitHubOAuthProvider.java`<br>`GiteeOAuthProvider.java` | `code` 与 `redirect_uri` 做 URL 编码；补 try/catch；token 为空时抛出带原文错误的异常 |
| 后端 | `OAuthController.java` | 所有失败路径由「返回 JSON」改为 `302 → /oauth/callback?error=<原因>`；新增 `humanizeOAuthError()` 中文翻译 |
| 前端 | `pages/oauth/callback.vue` | 重写：读取 `?error=` 展示失败原因 + 「重新登录」/「返回首页」按钮 |

⚠️ **两侧都改了** ⇒ 后端 jar 和前端 SSR 产物**都必须重新发布**，只做一侧等于没上线。

---

## 1. 本机：构建后端 jar

> ⚠️ 本机 `mvn` 裸命令**跑不起来**（`JAVA_HOME` 未设置 → `ClassNotFoundException`）。
> 必须用下面这条 classworlds 方式，且**必须先 cd 到 `blog-server` 目录**。

```bash
cd /d/cxdownload/Blog/blog-server

java -classpath "D:\java\maven\apache-maven-3.9.14\boot\plexus-classworlds-2.9.0.jar" \
  "-Dclassworlds.conf=D:\java\maven\apache-maven-3.9.14\bin\m2.conf" \
  "-Dmaven.home=D:\java\maven\apache-maven-3.9.14" \
  "-Dmaven.multiModuleProjectDirectory=D:\cxdownload\Blog\blog-server" \
  org.codehaus.plexus.classworlds.launcher.Launcher \
  -B -o clean package -DskipTests -pl blog-front-api -am
```

期望结尾：

```
[INFO] BUILD SUCCESS
[INFO] Total time:  ~35 s
```

产物：`blog-server/blog-front-api/target/blog-front-api-1.0.0.jar`（约 80 MB）

### 校验产物里真的含新代码

```bash
cd /d/cxdownload/Blog
unzip -p blog-server/blog-front-api/target/blog-front-api-1.0.0.jar \
  BOOT-INF/classes/com/blog/front/controller/OAuthController.class \
  | strings | grep 'oauth/callback?error='
```

**有输出**（`/oauth/callback?error=`）⇒ 新代码在包里 ✅

---

## 2. 上传 jar 并重启后端

```bash
# 上传（⚠️ 落地名必须是 /opt/blog/blog-api.jar，要和 blog-api.service 的 ExecStart 一致）
scp /d/cxdownload/Blog/blog-server/blog-front-api/target/blog-front-api-1.0.0.jar \
    root@47.116.138.65:/opt/blog/blog-api.jar

# 重启 + 看状态
ssh root@47.116.138.65 "systemctl restart blog-api && sleep 5 && systemctl --no-pager --lines=0 status blog-api"

# 看启动日志（确认无异常）
ssh root@47.116.138.65 "sudo journalctl -u blog-api -n 40 --no-pager"
```

> ⚠️ **历史坑**：`blog-api.jar` 这个名字不能改。曾出现过 jar 落地名与 service 里写的不一致，
> 导致「部署成功但跑的还是旧包」。

---

## 3. 本机：构建前端

```bash
cd /d/cxdownload/Blog/blog-web

npm ci          # 只有 package.json/lock 变了才必须；本次没变，也可用 npm install
npm run build
```

> ⚠️ 如果本机沙箱拦住了完整构建，就**在服务器上构建**（见下方「方案 B」）。

产物：`blog-web/.output/`

---

## 4. 上传整个 .output 并重启前端

```bash
# 打包（在 blog-web 目录内，确保 .output 在包根）
cd /d/cxdownload/Blog/blog-web
tar czf web-output.tar.gz .output

# 上传
scp web-output.tar.gz root@47.116.138.65:/opt/blog/

# 服务器：替换 + 修权限 + 重启
ssh root@47.116.138.65 "
  cd /opt/blog/web &&
  rm -rf .output &&
  tar xzf ../web-output.tar.gz &&
  chown -R ecs-user:ecs-user /opt/blog/web &&
  systemctl restart blog-web &&
  sleep 3 &&
  systemctl --no-pager --lines=0 status blog-web
"
```

> ⚠️ **绝对不能只拷 `.output/public`**。
> nginx 的 `location /` 是反代到 `127.0.0.1:3000`，对外真正服务的是 `blog-web.service` 起的
> Nuxt SSR 进程。只拷 `public` 到 nginx 静态目录 = 前端改动从未上线（**这是踩过的坑**）。

---

## 方案 B：登录服务器一键部署（推荐，如果用得上）

仓库里有现成脚本 `deploy/deploy.sh`，它**设计为在服务器上运行**（因为要 `cp` 到 `/opt/blog`）。

在服务器上：

```bash
# 前提：服务器上有代码仓库 + 已装好 mvn / npm
cd /path/to/Blog
git pull            # 或按你的方式同步代码
sudo bash deploy/deploy.sh
```

脚本会依次：构建后端 → 拷 jar → 构建前端 → 替换 `.output` → 同步 systemd unit →
`daemon-reload` → 重启 `blog-api` 和 `blog-web`。

> 注意 `deploy.sh` 用的是裸 `mvn`，服务器上 `JAVA_HOME` 若没配好也会报同样的
> `ClassNotFoundException`。服务器上推荐先 `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`
> 之类的真实路径，或在 `/etc/profile.d/` 里配好。

---

## 5. 部署后自测（必做）

### ① 后端新代码是否生效

```bash
# 看 302 的 redirect_uri 是否被编码（编码 = 新代码）
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' \
  --noproxy '*' --resolve codeup.asia:443:47.116.138.65 \
  https://codeup.asia/api/auth/oauth/github
```

期望：`302`，且 URL 里 `redirect_uri=https%3A%2F%2F...`（**百分号编码**）。

### ② 前端失败提示是否生效

浏览器直接打开（故意触发失败分支）：

```
https://codeup.asia/oauth/callback?error=%E6%B5%8B%E8%AF%95%E5%A4%B1%E8%B4%A5%E5%8E%9F%E5%9B%A0
```

期望：页面显示 **「测试失败原因」** + 「重新登录」「返回首页」两个按钮。
若仍是空白转圈 ⇒ 前端产物没换成新的。

### ③ 完整走一遍真实登录

点登录 → GitHub 授权 → 应正常回到首页且已登录。

---

## 6. 回滚方式

```bash
# 后端回滚：把上一个 jar 传回去重启即可
scp <旧jar> root@47.116.138.65:/opt/blog/blog-api.jar
ssh root@47.116.138.65 "systemctl restart blog-api"

# 前端回滚：同理替换 /opt/blog/web/.output 后重启 blog-web
```

> 建议部署前先备份：`ssh root@47.116.138.65 "cp /opt/blog/blog-api.jar /opt/blog/blog-api.jar.bak"`

---

## 7. 检查清单

- [ ] 本机构建后端 → `BUILD SUCCESS`
- [ ] jar 内 `strings` 校验含 `oauth/callback?error=` ✅（已验证）
- [ ] scp jar 到 `/opt/blog/blog-api.jar`
- [ ] `systemctl restart blog-api` + 日志无异常
- [ ] 本机构建前端 → `.output` 生成
- [ ] 打包整个 `.output` 上传解包
- [ ] `systemctl restart blog-web`
- [ ] 自测 ① `redirect_uri` 已编码
- [ ] 自测 ② `?error=` 页面能显示中文原因
- [ ] 自测 ③ 真实登录成功
