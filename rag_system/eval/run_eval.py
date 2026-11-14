#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
run_eval.py - AI 子系统评测集执行器（P5）
==========================================================================
改动 AI 相关代码前后各跑一次，用 --compare 看分数和指标有没有变差。

用法：
    python run_eval.py                        # 跑全部，生成报告
    python run_eval.py --only q01,q11,q12     # 只跑指定题（调试断言时用）
    python run_eval.py --category injection   # 只跑某一类
    python run_eval.py --compare reports/eval-2026-11-13-101500.json
    python run_eval.py --dry-run              # 只打印题目与断言，不发请求

两个设计决定，改动前先读：

1. **走 HTTP 而不是 import agent**
   评测要验的是线上真实链路：SSE 序列化、Java 工具网关、模型注册表下发。
   直接调内部函数会绕过这些，测出来的分数好看但没有意义。

2. **不传 X-AI-User-Id**
   `agent._settle()` 第一行是 `if not user_id: return`，不传用户 id 就不写
   `ai_usage_daily` / `agent_trace`。所以评测既不污染生产账单，也不会把
   全局预算熔断触发掉 —— 否则跑两轮评测就把当天的额度吃光了。
"""
import argparse
import json
import statistics
import sys
import time
from datetime import datetime
from pathlib import Path

try:
    import requests
except ImportError:
    print("缺少 requests：pip install requests")
    raise SystemExit(1)

EVAL_DIR = Path(__file__).resolve().parent
REPORT_DIR = EVAL_DIR / "reports"
ENV_FILE = EVAL_DIR.parent / "backend" / ".env"

# Windows 控制台默认 GBK，中文表格会乱码；Linux 上这行是无害的
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


# ----------------------------------------------------------------------
# 环境
# ----------------------------------------------------------------------
def read_env(key: str, default: str = "") -> str:
    """从 backend/.env 读一个变量（只读，不导入 config 以免连带加载 chromadb）。"""
    if not ENV_FILE.exists():
        return default
    try:
        for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    except Exception:
        pass
    return default


def web_available(base: str, token: str) -> bool:
    """问 /health 拿 webSearch，决定要不要跳过联网题。"""
    try:
        headers = {"X-RAG-Token": token} if token else {}
        r = requests.get(f"{base}/health", headers=headers, timeout=5)
        return bool(r.json().get("webSearch"))
    except Exception:
        return False


# ----------------------------------------------------------------------
# 单题执行
# ----------------------------------------------------------------------
def run_case(case: dict, base: str, token: str, timeout: int) -> dict:
    """跑一题，返回结构化结果。任何异常都转成 error 字段，不往上抛。"""
    question = case["question"]
    payload = {"question": question, "top_k": 6}
    headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
    if token:
        headers["X-RAG-Token"] = token
    # ⚠️ 刻意不传 X-AI-User-Id：见模块说明第 2 条

    result = {
        "id": case["id"], "category": case.get("category", ""),
        "question": question, "answer": "", "tools": [], "sources": [],
        "steps": 0, "max_round": 0, "tokens": 0,
        "prompt_tokens": 0, "completion_tokens": 0,
        "first_token_ms": None, "total_ms": None, "degraded": False,
        "model": "", "error": None, "fails": [],
    }

    t0 = time.monotonic()
    try:
        resp = requests.post(f"{base}/chat", json=payload, headers=headers,
                             stream=True, timeout=(5, timeout))
    except requests.exceptions.RequestException as e:
        result["error"] = f"连接失败: {type(e).__name__}: {e}"
        result["total_ms"] = int((time.monotonic() - t0) * 1000)
        return result

    if resp.status_code != 200:
        result["error"] = f"HTTP {resp.status_code}: {resp.text[:200]}"
        result["total_ms"] = int((time.monotonic() - t0) * 1000)
        return result

    # 不设 encoding 的话 iter_lines 会按 ISO-8859-1 解码，中文全变乱码
    resp.encoding = "utf-8"

    parts: list[str] = []
    ev = ""
    try:
        for raw in resp.iter_lines(decode_unicode=True):
            if not raw:
                continue
            if raw.startswith(":"):          # ": ping" 心跳
                continue
            if raw.startswith("event:"):
                ev = raw[6:].strip()
                continue
            if raw.startswith("data:"):
                body = raw[5:].strip()
                try:
                    data = json.loads(body)
                except json.JSONDecodeError:
                    continue

                if ev == "token":
                    if result["first_token_ms"] is None:
                        result["first_token_ms"] = int((time.monotonic() - t0) * 1000)
                    parts.append(data.get("text", ""))
                elif ev == "tool":
                    if data.get("state") == "done":
                        step_no = int(data.get("step") or 0)
                        result["tools"].append({
                            "name": data.get("name", ""),
                            "ok": bool(data.get("ok")),
                            "step": step_no,
                            "error": data.get("error"),
                        })
                        # step 是**轮次**（受 AGENT_MAX_STEPS 约束）；
                        # done.steps 是**工具调用总数**（一轮可能并行调多个）。
                        # 断言 max_steps 要按轮次判，否则多工具并行会被误判超限。
                        if step_no > result["max_round"]:
                            result["max_round"] = step_no
                elif ev == "sources":
                    if isinstance(data, list):
                        result["sources"] = data
                elif ev == "done":
                    result["steps"] = int(data.get("steps") or 0)
                    result["tokens"] = int(data.get("tokens") or 0)
                    result["prompt_tokens"] = int(data.get("promptTokens") or 0)
                    result["completion_tokens"] = int(data.get("completionTokens") or 0)
                    result["degraded"] = bool(data.get("degraded"))
                    result["model"] = data.get("model") or ""
                elif ev == "error":
                    result["error"] = data.get("message") or "服务返回 error 事件"
    except requests.exceptions.RequestException as e:
        # ReadTimeout 最常见：模型或工具卡住了
        if result["error"] is None:
            result["error"] = f"读取中断: {type(e).__name__}"
    finally:
        try:
            resp.close()
        except Exception:
            pass

    result["answer"] = "".join(parts)
    result["total_ms"] = int((time.monotonic() - t0) * 1000)
    return result


# ----------------------------------------------------------------------
# 判定
# ----------------------------------------------------------------------
def judge(case: dict, res: dict) -> list[str]:
    """返回失败原因列表，空列表 = 通过。断言全部基于字符串匹配，不做主观评分。"""
    if res["error"]:
        return [res["error"]]

    exp = case.get("expect") or {}
    fails: list[str] = []
    tool_names = [t["name"] for t in res["tools"]]
    answer = res["answer"]

    for name in exp.get("must_tool", []):
        if name not in tool_names:
            fails.append(f"未调用必需工具 {name}（实际：{tool_names or '无'}）")

    # 站内有 search_knowledge（Python 向量库）和 search_articles（Java 侧）
    # 两个入口，模型任选其一都算对 —— 断言写死某一个会把正确行为判成失败。
    need_any = exp.get("must_tool_any")
    if need_any and not any(n in tool_names for n in need_any):
        fails.append(f"未调用任一检索工具 {need_any}（实际：{tool_names or '无'}）")

    for name in exp.get("must_not_tool", []):
        if name in tool_names:
            fails.append(f"不该调用 {name}（简单问题不该检索）")

    need = exp.get("must_contain_any")
    if need and not any(k in answer for k in need):
        fails.append(f"答案未命中任一关键词 {need}")

    if exp.get("must_have_sources") and not res["sources"]:
        fails.append("来源卡片为空")

    for word in exp.get("forbidden", []):
        if word in answer:
            fails.append(f"出现禁止内容「{word}」（疑似泄漏系统提示）")

    limit = exp.get("max_steps")
    if limit and res["max_round"] > limit:
        fails.append(f"工具轮次 {res['max_round']} 超过上限 {limit}"
                     f"（共 {res['steps']} 次调用）")

    if not answer.strip():
        fails.append("答案为空")

    return fails


# ----------------------------------------------------------------------
# 汇总
# ----------------------------------------------------------------------
def summarize(cases: list[dict], results: list[dict], skipped: list[str]) -> dict:
    ran = [r for r in results if r["error"] is None]
    all_tools = [t for r in ran for t in r["tools"]]
    failed_tools = [t for t in all_tools if not t["ok"]]
    firsts = [r["first_token_ms"] for r in ran if r["first_token_ms"]]
    tokens = [r["tokens"] for r in ran if r["tokens"]]
    rounds = [r["max_round"] for r in ran if r["max_round"]]
    calls = [r["steps"] for r in ran if r["steps"]]

    total = len(results)
    passed = sum(1 for r in results if not r["fails"])

    return {
        "total": total,
        "passed": passed,
        "pass_rate": round(passed / total, 4) if total else 0.0,
        "skipped": skipped,
        "errors": sum(1 for r in results if r["error"]),
        # P50 用中位数：偶发一次慢请求不该把整体指标带偏
        "p50_first_token_ms": int(statistics.median(firsts)) if firsts else None,
        # 轮次才对应 AGENT_MAX_STEPS 和"单轮平均步数<2.2"这个目标；
        # 调用数会把"一轮并行调两个工具"算成两步，指标虚高。
        "avg_round": round(statistics.mean(rounds), 2) if rounds else None,
        "avg_tool_calls": round(statistics.mean(calls), 2) if calls else None,
        "avg_tokens": int(statistics.mean(tokens)) if tokens else None,
        "tool_calls": len(all_tools),
        "tool_failures": len(failed_tools),
        "tool_failure_rate": round(len(failed_tools) / len(all_tools), 4) if all_tools else 0.0,
        "timeout_rate": round(sum(1 for r in results if "Timeout" in (r["error"] or "")) / total, 4) if total else 0.0,
        "degraded_cases": sum(1 for r in results if r["degraded"]),
    }


# ----------------------------------------------------------------------
# 报告
# ----------------------------------------------------------------------
def print_table(results: list[dict]) -> None:
    print("\n" + "=" * 80)
    print(f"{'题号':<6}{'类别':<12}{'结果':<6}{'轮/调':>7}{'token':>7}{'首字ms':>8}  失败原因")
    print("-" * 80)
    for r in results:
        ok = "PASS" if not r["fails"] else "FAIL"
        ft = r["first_token_ms"] if r["first_token_ms"] is not None else "-"
        rc = f"{r['max_round']}/{r['steps']}"
        reason = "" if not r["fails"] else "; ".join(r["fails"])[:44]
        print(f"{r['id']:<6}{r['category']:<12}{ok:<6}{rc:>7}{r['tokens']:>7}{str(ft):>8}  {reason}")
    print("=" * 80)


def print_summary(s: dict) -> None:
    print(f"\n[汇总] 通过 {s['passed']}/{s['total']}  "
          f"({s['pass_rate'] * 100:.1f}%)   错误 {s['errors']}   跳过 {len(s['skipped'])}")
    print(f"  P50 首 token      {s['p50_first_token_ms']} ms     (目标 < 3000)")
    print(f"  平均工具轮次      {s['avg_round']}             (目标 < 2.2)")
    print(f"  平均工具调用      {s['avg_tool_calls']}")
    print(f"  平均 token        {s['avg_tokens']}")
    print(f"  工具失败率        {s['tool_failure_rate'] * 100:.1f}%  "
          f"({s['tool_failures']}/{s['tool_calls']})   (目标 < 3%)")
    print(f"  超时率            {s['timeout_rate'] * 100:.1f}%   (目标 < 0.5%)")
    if s["degraded_cases"]:
        print(f"  ⚠ 有 {s['degraded_cases']} 题发生模型降级（不影响判定，但要知道）")


def compare(base: dict, cur: dict) -> None:
    """对比两份报告。指标下降标 [!]，改善标 [+]，持平标 [=]。"""
    rows = [
        ("通过数", f"{base['passed']}/{base['total']}", f"{cur['passed']}/{cur['total']}",
         cur["passed"] - base["passed"], True),
        ("P50 首token(ms)", base.get("p50_first_token_ms"), cur.get("p50_first_token_ms"),
         None, False),
        ("平均工具轮次", base.get("avg_round"), cur.get("avg_round"), None, False),
        ("平均工具调用", base.get("avg_tool_calls"), cur.get("avg_tool_calls"), None, False),
        ("平均token", base.get("avg_tokens"), cur.get("avg_tokens"), None, False),
        ("工具失败率", f"{base['tool_failure_rate'] * 100:.1f}%",
         f"{cur['tool_failure_rate'] * 100:.1f}%", None, False),
    ]
    print("\n" + "=" * 62)
    print(f"{'指标':<18}{'基线':>14}{'本次':>14}   变化")
    print("-" * 62)
    for name, old, new, delta, higher_better in rows:
        mark = "="
        if isinstance(old, (int, float)) and isinstance(new, (int, float)):
            diff = new - old
            if diff == 0:
                mark = "="
            elif higher_better:
                mark = "[+] 变好" if diff > 0 else "[!] 变差"
            else:
                # 延迟/步数/token/失败率：越小越好
                mark = "[+] 变好" if diff < 0 else "[!] 变差"
        elif delta is not None:
            mark = "=" if delta == 0 else ("[+] 变好" if delta > 0 else "[!] 变差")
        print(f"{name:<18}{str(old):>14}{str(new):>14}   {mark}")
    print("=" * 62)


# ----------------------------------------------------------------------
# 入口
# ----------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser(description="AI 子系统评测集执行器")
    ap.add_argument("--base", default="http://127.0.0.1:8000", help="RAG 服务地址")
    ap.add_argument("--token", default=None, help="RAG_API_TOKEN（默认读 backend/.env）")
    ap.add_argument("--timeout", type=int, default=120, help="单题读取超时（秒）")
    ap.add_argument("--only", default="", help="只跑这些题号，逗号分隔")
    ap.add_argument("--category", default="", help="只跑某一类")
    ap.add_argument("--compare", default="", help="与历史报告对比")
    ap.add_argument("--out", default="", help="报告输出路径")
    ap.add_argument("--dry-run", action="store_true", help="只打印题目，不发请求")
    args = ap.parse_args()

    spec = json.loads((EVAL_DIR / "eval_set.json").read_text(encoding="utf-8"))
    cases = spec["questions"]

    if args.only:
        wanted = {x.strip() for x in args.only.split(",") if x.strip()}
        cases = [c for c in cases if c["id"] in wanted]
    if args.category:
        cases = [c for c in cases if c.get("category") == args.category]

    if not cases:
        print("没有匹配的题目")
        return 1

    if args.dry_run:
        for c in cases:
            print(f"{c['id']} [{c.get('category')}] {c['question']}")
            print(f"     断言: {json.dumps(c.get('expect') or {}, ensure_ascii=False)}")
        return 0

    token = args.token if args.token is not None else read_env("RAG_API_TOKEN")
    if token:
        print(f"[配置] token 已加载（{len(token)} 字符）")
    else:
        print("[配置] 未配置 token（服务端若开了鉴权，所有请求都会 401）")

    has_web = web_available(args.base, token)
    print(f"[配置] webSearch = {has_web}")

    results: list[dict] = []
    skipped: list[str] = []

    for i, case in enumerate(cases, 1):
        if case.get("skip_if") == "no_web" and not has_web:
            skipped.append(case["id"])
            print(f"[{i}/{len(cases)}] {case['id']} 跳过（未配置联网 key）")
            continue

        print(f"[{i}/{len(cases)}] {case['id']} 运行中 ...", end="", flush=True)
        res = run_case(case, args.base, token, args.timeout)
        res["fails"] = judge(case, res)
        results.append(res)
        status = "PASS" if not res["fails"] else "FAIL"
        print(f"\r[{i}/{len(cases)}] {case['id']} {status}  "
              f"{res['total_ms']}ms  {res['tokens']}tok")

    if not results:
        print("全部跳过，无结果")
        return 0

    stats = summarize(cases, results, skipped)
    print_table(results)
    print_summary(stats)

    report = {
        "version": spec.get("version", 1),
        "ran_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "base": args.base,
        "models": sorted({r["model"] for r in results if r["model"]}),
        "summary": stats,
        "cases": results,
    }

    out = Path(args.out) if args.out else (
        REPORT_DIR / f"eval-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n[报告] {out}")

    if args.compare:
        old_path = Path(args.compare)
        if not old_path.exists():
            print(f"[对比] 找不到基线报告：{old_path}")
        else:
            old = json.loads(old_path.read_text(encoding="utf-8"))
            compare(old["summary"], stats)

    return 0 if stats["errors"] == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
