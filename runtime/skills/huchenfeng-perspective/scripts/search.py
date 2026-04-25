#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ai-agent-station-study 适配版户晨风语录检索脚本。
"""
import argparse
import importlib.util
import json
import os
import sys
from pathlib import Path


def main():
    try:
        runtime_args = read_runtime_args()
        query = runtime_args["query"]
        top_k = runtime_args["top_k"]
        date_from = runtime_args["date_from"]
        date_to = runtime_args["date_to"]
        hu_only = runtime_args["hu_only"]

        search_module = load_python_module("huchenfeng_original_search", tools_dir() / "search.py")
        index_path = resolve_index_path()
        index = search_module.load_index(str(index_path))
        results = search_module.search(
            query=query,
            index=index,
            top_k=top_k,
            date_from=date_from,
            date_to=date_to,
            speaker="户晨风" if hu_only else None,
        )
        formatted = search_module.format_results(results)

        output_dir = ensure_output_dir()
        output_file = output_dir / "search_results.md"
        output_file.write_text(formatted, encoding="utf-8")

        print(f"查询：{query}")
        print(f"结果数：{len(results)}")
        print(f"输出文件：{output_file}")
        print()
        print(formatted)
    except Exception as exc:
        print(f"search 脚本执行失败：{exc}", file=sys.stderr)
        if "fastembed" in str(exc) or "numpy" in str(exc):
            print("请先在 reactor-tool 的 Python 环境中安装依赖：pip install fastembed numpy", file=sys.stderr)
        sys.exit(1)


def read_runtime_args():
    """同时兼容 SKILL_ARGUMENTS_JSON 和命令行参数。"""
    parser = argparse.ArgumentParser(description="户晨风语录检索")
    parser.add_argument("query", nargs="?", help="搜索关键词")
    parser.add_argument("--query", dest="query_option", help="搜索关键词")
    parser.add_argument("--top-k", "--top_k", dest="top_k", type=int, default=None, help="返回条数")
    parser.add_argument("--date-from", "--date_from", dest="date_from", type=str, default=None, help="起始日期 YYYY-MM-DD")
    parser.add_argument("--date-to", "--date_to", dest="date_to", type=str, default=None, help="截止日期 YYYY-MM-DD")
    parser.add_argument("--hu-only", "--hu_only", dest="hu_only", action="store_true", help="仅返回户晨风本人语录")
    cli_args = parser.parse_args()

    env_args = {}
    raw_env = os.environ.get("SKILL_ARGUMENTS_JSON", "{}").strip()
    if raw_env:
        env_args = json.loads(raw_env)

    query = pick_first_non_empty(env_args.get("query"), cli_args.query_option, cli_args.query)
    if not query:
        raise ValueError("缺少 query 参数，请通过 arguments.query 或 argv 传入检索词")

    top_k = env_args.get("top_k")
    if top_k is None:
        top_k = cli_args.top_k if cli_args.top_k is not None else 5

    return {
        "query": str(query).strip(),
        "top_k": max(1, int(top_k)),
        "date_from": pick_first_non_empty(env_args.get("date_from"), cli_args.date_from),
        "date_to": pick_first_non_empty(env_args.get("date_to"), cli_args.date_to),
        "hu_only": to_bool(env_args.get("hu_only"), cli_args.hu_only),
    }


def pick_first_non_empty(*values):
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return value
    return None


def to_bool(*values):
    for value in values:
        if value is None:
            continue
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            return value.strip().lower() in {"1", "true", "yes", "y", "on"}
        return bool(value)
    return False


def skill_root():
    return Path(__file__).resolve().parents[1]


def tools_dir():
    return skill_root() / "tools"


def resolve_index_path():
    default_path = tools_dir() / "vector_index.json"
    if not default_path.exists():
        raise FileNotFoundError(f"未找到向量索引文件：{default_path}")
    return default_path


def ensure_output_dir():
    output_dir = os.environ.get("SKILL_OUTPUT_DIR", "").strip()
    target_dir = Path(output_dir) if output_dir else skill_root() / "output"
    target_dir.mkdir(parents=True, exist_ok=True)
    return target_dir


def load_python_module(module_name: str, module_path: Path):
    """按文件路径加载原始工具模块，尽量复用原始实现。"""
    if not module_path.exists():
        raise FileNotFoundError(f"未找到原始工具脚本：{module_path}")
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载模块：{module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


if __name__ == "__main__":
    main()
