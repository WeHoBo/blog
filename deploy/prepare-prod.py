#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""幂等补齐 /opt/blog/application-prod.yml 的新配置项。

需 root 运行：sudo python3 prepare-prod.py
处理内容：
  1. ai.rag-url / ai.rag-token             —— RAG 地址与共享令牌
  2. app.cors.allowed-origins              —— 跨域白名单
  3. spring.mvc.async.request-timeout      —— AI 流式回答，避免 30s 默认超时掐断
  4. spring.data.redis.password            —— Redis 密码（对应 redis.conf 的 requirepass）
  5. logging.file.path                     —— 日志落盘目录（配合 logback-spring.xml）
  6. jwt.secret 旧明文 -> ${JWT_SECRET:}   —— 若仍是老值

特性：已有项自动跳过；有改动时先在同目录生成 .bak-时间戳 备份。
      只打印改了什么键，绝不回显任何密钥值。
"""
import os
import re
import shutil
import sys
import time

PATH = os.environ.get("PROD_YML", "/opt/blog/application-prod.yml")
LOG_PATH = os.environ.get("PROD_LOG_PATH", "/opt/blog/logs")


def section_end(lines, start):
    """返回顶级段落 start 之后第一个非缩进行的下标（即段落结束）。"""
    for j in range(start + 1, len(lines)):
        if lines[j] and not lines[j][0].isspace():
            return j
    return len(lines)


def block_end(lines, idx):
    """返回 idx 行所属块结束的下标（缩进回到 <= idx 缩进处）。"""
    if not lines[idx].strip():
        return idx + 1
    indent = len(lines[idx]) - len(lines[idx].lstrip())
    for j in range(idx + 1, len(lines)):
        if not lines[j].strip():
            continue
        cur = len(lines[j]) - len(lines[j].lstrip())
        if cur <= indent:
            return j
    return len(lines)


def find_key(lines, key, indent, start, end):
    """在 [start, end) 内找缩进恰为 indent、键名为 key 的行，返回下标或 -1。

    注意：必须用「缩进 + 键名 + 冒号」判定，不能用整行等值比较 ——
    否则 `password: ${X}` 这类带值的行永远匹配不上，导致重复插入（曾踩过）。
    """
    for i in range(start, end):
        s = lines[i]
        if not s.strip():
            continue
        if len(s) - len(s.lstrip()) != indent:
            continue
        body = s.strip()
        if body == key + ":" or body.startswith(key + ": "):
            return i
    return -1


def main():
    with open(PATH, encoding="utf-8") as f:
        lines = f.read().splitlines()
    changed = []
    notes = []

    def find_section(name):
        for i, line in enumerate(lines):
            if line.rstrip() == name + ":":
                return i
        return -1

    # ---------- 1. ai.rag-url / ai.rag-token ----------
    ai = find_section("ai")
    if ai == -1:
        lines += ["", "ai:", "  rag-url: http://127.0.0.1:8000", "  rag-token: ${RAG_API_TOKEN:}"]
        changed.append("ai.*")
    else:
        block = "\n".join(lines[ai:section_end(lines, ai)])
        if "rag-url:" not in block:
            lines.insert(ai + 1, "  rag-url: http://127.0.0.1:8000")
            changed.append("ai.rag-url")
            block = "\n".join(lines[ai:section_end(lines, ai)])
        if "rag-token:" not in block:
            insert_at = ai + 2 if "rag-url:" in lines[ai + 1] else ai + 1
            lines.insert(insert_at, "  rag-token: ${RAG_API_TOKEN:}")
            changed.append("ai.rag-token")

    # ---------- 2. app.cors.allowed-origins ----------
    app = find_section("app")
    if app == -1:
        lines += ["", "app:", "  cors:",
                  "    allowed-origins: ${CORS_ALLOWED_ORIGINS:https://codeup.asia,https://www.codeup.asia}"]
        changed.append("app.cors")
    else:
        block = "\n".join(lines[app:section_end(lines, app)])
        if "cors:" not in block:
            lines.insert(app + 1, "  cors:")
            lines.insert(app + 2,
                         "    allowed-origins: ${CORS_ALLOWED_ORIGINS:https://codeup.asia,https://www.codeup.asia}")
            changed.append("app.cors.allowed-origins")

    # ---------- 3. spring.mvc.async.request-timeout ----------
    spring = find_section("spring")
    if spring != -1:
        end = section_end(lines, spring)
        if not any("request-timeout" in line for line in lines[spring:end]):
            mvc_abs = find_key(lines, "mvc", 2, spring + 1, end)
            if mvc_abs == -1:
                lines.insert(spring + 1, "  mvc:")
                mvc_abs = spring + 1
            a_end = block_end(lines, mvc_abs)
            async_abs = find_key(lines, "async", 4, mvc_abs + 1, a_end)
            if async_abs == -1:
                async_abs = mvc_abs + 1
                lines.insert(async_abs, "    async:")
            lines.insert(async_abs + 1, "      request-timeout: 300s")
            changed.append("spring.mvc.async.request-timeout")

    # ---------- 4. spring.data.redis.password ----------
    spring = find_section("spring")
    if spring != -1:
        s_end = section_end(lines, spring)
        data = find_key(lines, "data", 2, spring + 1, s_end)
        if data != -1:
            d_end = block_end(lines, data)
            redis = find_key(lines, "redis", 4, data + 1, d_end)
            if redis != -1:
                r_end = block_end(lines, redis)
                if find_key(lines, "password", 6, redis + 1, r_end) == -1:
                    insert_at = r_end
                    while insert_at > redis + 1 and not lines[insert_at - 1].strip():
                        insert_at -= 1
                    lines.insert(insert_at, "      password: ${REDIS_PASSWORD:}")
                    changed.append("spring.data.redis.password")
            else:
                notes.append("spring.data.data 下未找到 redis: 段落，请手工补 password（见执行手册）")
        else:
            notes.append("spring 下未找到 data: 段落，请手工补 redis 配置（见执行手册）")

    # ---------- 5. logging.file.path ----------
    logging_idx = find_section("logging")
    if logging_idx == -1:
        lines += ["", "logging:", "  file:", "    path: " + LOG_PATH]
        changed.append("logging.file.path")
    else:
        l_end = section_end(lines, logging_idx)
        file_key = find_key(lines, "file", 2, logging_idx + 1, l_end)
        if file_key == -1:
            lines.insert(logging_idx + 1, "  file:")
            lines.insert(logging_idx + 2, "    path: " + LOG_PATH)
            changed.append("logging.file.path")
        else:
            f_end = block_end(lines, file_key)
            if find_key(lines, "path", 4, file_key + 1, f_end) == -1:
                lines.insert(file_key + 1, "    path: " + LOG_PATH)
                changed.append("logging.file.path")

    # ---------- 6. jwt.secret 旧明文迁移 ----------
    # 匹配「仍是字面量」的 secret（排除已是 ${...} 占位符的行），统一改为读环境变量，
    # 不在脚本里写死任何真实密钥内容。
    for i, line in enumerate(lines):
        if re.match(r"^\s*secret:\s*(?!\$\{)", line):
            lines[i] = "  secret: ${JWT_SECRET:}"
            changed.append("jwt.secret -> ${JWT_SECRET:}")
            notes.append("jwt.secret 已改为读取环境变量；请确认 /opt/blog/blog.env 里已设置 JWT_SECRET，"
                         "否则后端会启动失败（这是有意为之的 fail-fast）")

    if not changed:
        print("application-prod.yml 已包含全部新配置，无需修改")
    else:
        backup = PATH + ".bak-" + time.strftime("%Y%m%d%H%M%S")
        shutil.copy2(PATH, backup)
        with open(PATH, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        print("已更新 application-prod.yml（备份：" + backup + "）")
        print("本次改动项：")
        for c in changed:
            print("  - " + c)

    for n in notes:
        print("[注意] " + n)
    return 0


if __name__ == "__main__":
    sys.exit(main())
