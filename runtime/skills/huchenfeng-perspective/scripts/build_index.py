#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ai-agent-station-study 适配版向量索引重建脚本。
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
        source_dir = Path(runtime_args["source_dir"]).expanduser().resolve()
        output_path = resolve_output_path(runtime_args.get("output_path"))

        build_index_module = load_python_module("huchenfeng_original_build_index", tools_dir() / "build_index.py")
        chunks = build_index_module.load_transcripts(str(source_dir))
        chunks = build_index_module.build_embeddings(chunks)
        build_index_module.save_index(chunks, str(output_path))

        output_dir = ensure_output_dir()
        summary_file = output_dir / "build_index_summary.md"
        summary_file.write_text(
            "\n".join([
                "# Build Index Summary",
                "",
                f"- source_dir: {source_dir}",
                f"- output_path: {output_path}",
                f"- chunk_count: {len(chunks)}",
            ]),
            encoding="utf-8",
        )

        print(f"索引重建完成：{output_path}")
        print(f"文本块数量：{len(chunks)}")
        print(f"摘要文件：{summary_file}")
    except Exception as exc:
        print(f"build_index 脚本执行失败：{exc}", file=sys.stderr)
        if "fastembed" in str(exc) or "numpy" in str(exc):
            print("请先在 reactor-tool 的 Python 环境中安装依赖：pip install fastembed numpy", file=sys.stderr)
        sys.exit(1)


def read_runtime_args():
    parser = argparse.ArgumentParser(description="重建户晨风向量索引")
    parser.add_argument("source_dir", nargs="?", help="原始直播文字稿目录")
    parser.add_argument("--source-dir", "--source_dir", dest="source_dir_option", type=str, default=None, help="原始直播文字稿目录")
    parser.add_argument("--output-path", "--output_path", dest="output_path", type=str, default=None, help="索引输出路径")
    cli_args = parser.parse_args()

    env_args = {}
    raw_env = os.environ.get("SKILL_ARGUMENTS_JSON", "{}").strip()
    if raw_env:
        env_args = json.loads(raw_env)

    source_dir = pick_first_non_empty(env_args.get("source_dir"), cli_args.source_dir_option, cli_args.source_dir)
    if not source_dir:
        raise ValueError("缺少 source_dir 参数，请指向直播文字稿根目录")

    return {
        "source_dir": str(source_dir).strip(),
        "output_path": pick_first_non_empty(env_args.get("output_path"), cli_args.output_path),
    }


def pick_first_non_empty(*values):
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return value
    return None


def skill_root():
    return Path(__file__).resolve().parents[1]


def tools_dir():
    return skill_root() / "tools"


def resolve_output_path(output_path: str | None):
    if output_path:
        return Path(output_path).expanduser().resolve()
    return tools_dir() / "vector_index.json"


def ensure_output_dir():
    output_dir = os.environ.get("SKILL_OUTPUT_DIR", "").strip()
    target_dir = Path(output_dir) if output_dir else skill_root() / "output"
    target_dir.mkdir(parents=True, exist_ok=True)
    return target_dir


def load_python_module(module_name: str, module_path: Path):
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
