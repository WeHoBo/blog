#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""幂等补齐 /etc/nginx/nginx.conf 的 AI 流式 / 上传反代 / 上传体积配置。

需 root 运行：sudo python3 prepare-nginx.py
说明：服务器实际的 443 站点配置位于 /etc/nginx/nginx.conf（不是 conf.d/blog.conf），
      本脚本只插入缺失片段，不做整文件替换；改前自动备份 .bak-时间戳。
插入位置：第一个 `location / {` 之前（nginx 前缀匹配按长度优先，顺序不影响）。
"""
import re
import shutil
import sys
import time

PATH = "/etc/nginx/nginx.conf"

AI_TEMPLATE = """location /api/ai/ {{
{ind}    proxy_pass http://127.0.0.1:8080;
{ind}    proxy_http_version 1.1;
{ind}    proxy_set_header Host $host;
{ind}    proxy_set_header X-Real-IP $remote_addr;
{ind}    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
{ind}    proxy_set_header X-Forwarded-Proto $scheme;
{ind}    proxy_set_header Connection "";
{ind}    proxy_buffering off;
{ind}    proxy_cache off;
{ind}    proxy_read_timeout 300s;
{ind}    proxy_send_timeout 300s;
{ind}    chunked_transfer_encoding on;
{ind}}}

"""

UPLOAD_TEMPLATE = """location /uploads/ {{
{ind}    proxy_pass http://127.0.0.1:8080;
{ind}    proxy_set_header Host $host;
{ind}    proxy_set_header X-Real-IP $remote_addr;
{ind}    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
{ind}    proxy_set_header X-Forwarded-Proto $scheme;
{ind}    expires 30d;
{ind}    add_header Cache-Control "public";
{ind}}}

"""


def main():
    with open(PATH, encoding="utf-8") as f:
        text = f.read()

    match = re.search(r"(?m)^([ \t]*)location / \{", text)
    if not match:
        print("未找到 `location / {`，为避免破坏配置不做任何修改，请人工核对 " + PATH)
        return 1

    ind = match.group(1)
    additions = ""
    if "client_max_body_size" not in text:
        additions += ind + "client_max_body_size 20m;\n\n"
    if "/api/ai/" not in text:
        additions += AI_TEMPLATE.format(ind=ind)
    if "/uploads/" not in text:
        additions += UPLOAD_TEMPLATE.format(ind=ind)

    if not additions:
        print("nginx.conf 已包含 client_max_body_size / /api/ai/ / /uploads/，无需修改")
        return 0

    backup = PATH + ".bak-" + time.strftime("%Y%m%d%H%M%S")
    shutil.copy2(PATH, backup)
    text = text[:match.start()] + additions + text[match.start():]
    with open(PATH, "w", encoding="utf-8") as f:
        f.write(text)
    print("已更新 nginx.conf（备份：" + backup + "）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
