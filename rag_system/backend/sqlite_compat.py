# -*- coding: utf-8 -*-
"""
sqlite_compat.py - 在导入 chromadb 之前把 sqlite3 换成 pysqlite3
------------------------------------------------------------------
ChromaDB 要求 sqlite3 >= 3.35.0，而 Alibaba Cloud Linux 3 自带 3.26。
`pip install pysqlite3-binary` 提供一个内置新版 sqlite 的模块，
把它顶到 sys.modules['sqlite3']，chromadb 里的 `import sqlite3` 就会拿到新版。

⚠️ 必须在**任何** import chromadb 之前执行（包括间接导入）。
所以本文件只做这一件事、没有其它依赖。

入口清单（漏一个，那个脚本在生产机上就会抛
"Your system has an unsupported version of sqlite3"）：
  · main.py              服务本体
  · retrieval_selftest.py 检索配额自测
  · health_selftest.py    /health 自测
  · agent_selftest.py     Agent 全流程自测

本机（Windows / macOS / conda / python.org 版）自带新版 sqlite，
import pysqlite3 会失败并静默跳过，不影响本地开发。
"""
import sys

try:
    import pysqlite3
except ImportError:
    pass
else:
    sys.modules["sqlite3"] = pysqlite3
