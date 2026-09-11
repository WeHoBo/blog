# 企业级RAG智能文档问答系统

基于 **FastAPI + ChromaDB + DeepSeek + Vue3** 的本地 RAG（检索增强生成）文档问答系统。
上传 PDF 自动解析、切块、向量化建立企业知识库，随后即可对文档内容进行流式智能问答。
全链路原生手写实现（未使用 LangChain 等框架），代码结构清晰、注释完整，适合学习与二次开发。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 文档上传 | 支持拖拽/点击上传 PDF；PyMuPDF 逐页提取文本；按「块大小 + 重叠度」切分；向量化写入本地 ChromaDB；SQLite 记录文档元信息 |
| RAG 问答 | 问题向量化 → ChromaDB 召回 topK 相关片段 → 组装提示词（约束只依据资料回答、禁止幻觉）→ DeepSeek `deepseek-chat` 流式输出 |
| 问答历史 | 每轮问答自动存入 SQLite；前端可一键加载历史记录 |
| 前端页面 | Vue3 原生组件：上传进度条、文档列表、对话气泡流式渲染、问答范围选择（全部文档/指定文档） |

## 技术栈

- **后端**：Python 3.10+ · FastAPI · PyMuPDF · ChromaDB（本地持久化）· SQLite3 · DeepSeek API（OpenAI 兼容接口，`deepseek-chat` 模型）
- **前端**：Vue3 · Vite（无第三方 UI 库，原生组件 + 原生 CSS）

## 目录结构

```
rag_system/
├─ backend/                     # FastAPI 后端
│  ├─ main.py                   # 入口：4 个接口 + CORS + 流式响应
│  ├─ config.py                 # 全局配置（读取 .env、定义数据目录）
│  ├─ pdf_parser.py             # PyMuPDF 解析 PDF、文本切分（chunk + overlap）
│  ├─ vector_store.py           # ChromaDB 向量库封装 + OpenAI 兼容 Embedding 客户端
│  ├─ llm_client.py             # DeepSeek 流式调用封装 + RAG 提示词组装
│  ├─ db.py                     # SQLite 初始化、文档/历史 CRUD
│  ├─ requirements.txt          # Python 依赖
│  ├─ .env                      # 环境变量配置（密钥、模型、RAG 参数）
│  ├─ smoke_test.py             # （可选）离线冒烟测试，无需真实 API Key
│  └─ data/                     # 运行时自动生成：uploads/ PDF副本、chroma/ 向量库、rag.db
└─ frontend/                    # Vue3 前端
   ├─ index.html
   ├─ package.json
   ├─ vite.config.js            # 开发服务器 + /api 代理到后端
   └─ src/
      ├─ main.js
      ├─ App.vue                # 顶栏 + 左右两栏布局
      ├─ api.js                 # 后端接口封装（上传进度、流式读取）
      ├─ style.css              # 全局样式
      └─ components/
         ├─ UploadPanel.vue     # 上传区域 + 进度条 + 文档列表
         └─ ChatPanel.vue       # 聊天界面（流式展示 + 历史记录）
```

## 环境要求

- Python **3.10+**（本机已验证 3.14 可正常运行）
- Node.js **18+**（本机已验证 Node 20）
- 能访问 `https://api.deepseek.com`（DeepSeek 官方接口）
- 一个 **OpenAI 兼容格式的 Embedding 服务**（详见下方「Embedding 服务说明」）

---

## 一、环境安装

### 1. 创建 Python 虚拟环境并安装依赖

```bash
# 在项目根目录
python -m venv .venv

# Windows
.venv\Scripts\pip install -r backend\requirements.txt
# macOS / Linux
.venv/bin/pip install -r backend/requirements.txt
```

### 2. 安装前端依赖

```bash
cd frontend
npm install
```

## 二、环境变量配置

编辑 `backend/.env`（已含全部配置项与中文注释），需要填写两处密钥：

```ini
# ① DeepSeek 大模型配置（必填）—— 在 https://platform.deepseek.com 创建 API Key
DEEPSEEK_API_KEY=sk-你的DeepSeek密钥

# ② Embedding 向量服务配置（必填）
EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_API_KEY=sk-你的embedding服务密钥
EMBEDDING_MODEL=BAAI/bge-large-zh-v1.5
```

> **Embedding 服务说明**：DeepSeek 官方**不提供** embedding 接口，向量化必须指向一个 OpenAI 兼容格式的 embedding 服务，任选其一：
>
> - **SiliconFlow 硅基流动**（推荐，免费额度）：`https://api.siliconflow.cn/v1`，模型 `BAAI/bge-large-zh-v1.5`
> - **one-api / new-api 网关**：填网关地址，如 `http://127.0.0.1:3000/v1`
> - **本地服务**：如 Ollama（`http://127.0.0.1:11434/v1`，模型如 `llama3`）、Xinference 等
>
> ⚠️ **重要**：更换 embedding 服务或模型后，向量维度可能变化，需删除 `backend/data/chroma` 目录并重新上传文档。

RAG 参数（可选调整）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CHUNK_SIZE` | 500 | 文本切分块大小（字符数） |
| `CHUNK_OVERLAP` | 80 | 相邻块重叠度（字符数），避免跨块语义截断 |
| `TOP_K` | 5 | 每次检索返回的相关片段数量 |

## 三、启动后端

```bash
# 进入 backend 目录并启动（后端端口 8000）
cd backend
..\.venv\Scripts\python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

启动成功后终端会打印向量库就绪信息，浏览器访问 `http://127.0.0.1:8000/docs` 可查看 Swagger 接口文档。
> 若 `.env` 未配置会在此处直接报错，按提示补齐配置即可。

## 四、启动前端

```bash
# 进入 frontend 目录并启动（前端端口 5173）
cd frontend
npm run dev
```

浏览器访问 **http://localhost:5173** 即可使用：

1. 左侧点击/拖拽上传 PDF（带进度条），上传完成后自动出现在文档列表；
2. 右侧输入问题回车发送，回答流式逐字渲染；
3. 顶部可选择问答范围（全部文档 / 指定文档）、点击「查看历史」加载历史问答、「新对话」清空当前会话。

## 五、接口列表

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/upload` | 上传 PDF（multipart/form-data，字段名 `file`），解析切块向量化后返回文档信息 |
| `GET` | `/documents` | 获取已上传文档列表 |
| `POST` | `/chat` | RAG 问答，JSON：`{question, doc_id?, top_k?}`，返回 `text/plain` 流式文本 |
| `GET` | `/history?limit=50` | 获取聊天会话历史 |

## 六、离线冒烟测试（可选）

无需真实 API Key，可用随机向量 + 模拟流快速验证全链路（上传 → 检索 → 流式问答 → 历史）：

```bash
cd backend
..\.venv\Scripts\python smoke_test.py
```

> 注意：该脚本会使用并清空 `backend/data` 下的本地数据目录。

## 七、常见问题

- **启动报错「未配置 Embedding 服务」**：`backend/.env` 中 `EMBEDDING_*` 三项未填写完整。
- **上传后提问答「未找到相关信息」**：请确认 embedding 服务可用、文档已成功切块入库；扫描版/图片型 PDF 无法提取文字，需 OCR 后使用。
- **更换 embedding 模型后检索异常**：向量维度不一致导致，删除 `backend/data/chroma` 重新上传文档。
- **Windows 下 pip 安装 chromadb 失败**：升级 Python 到 3.10+，或先升级 pip（`pip install -U pip`）后重试。
- **端口占用**：8000 / 5173 被占用时，可修改 `uvicorn` 启动参数与 `frontend/vite.config.js` 中的 `target` 端口。
