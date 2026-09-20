# 好啵博客

个人技术博客 · https://codeup.asia

## 技术栈与目录

| 目录 | 技术栈 | 说明 |
|---|---|---|
| `blog-web` | Nuxt 3 / Vue 3 / Tailwind / CodeMirror / KaTeX | 前台与后台页面（SSR） |
| `blog-server` | Spring Boot 3 / MyBatis-Plus / MySQL / Redis | 接口与业务逻辑 |
| `rag_system` | FastAPI / ChromaDB / DeepSeek | AI 知识库问答服务 |

## 文档

- 项目文档索引（变更报告 / 建议清单 / 待办）：[docs/README.md](docs/README.md)
- 部署指南：[deploy/README.md](deploy/README.md)
- 服务器加固与备份手册：[deploy/服务器加固与备份执行手册.md](deploy/服务器加固与备份执行手册.md)
- RAG 子系统：[rag_system/README.md](rag_system/README.md)

## 部署（简述）

后端 `mvn package` 产出 JAR，前端 `npm ci && npm run build` 产出 `.output`；同步到服务器 `/opt/blog` 后重启 `blog-api` / `blog-web`。完整步骤以 `deploy/README.md` 为准。
