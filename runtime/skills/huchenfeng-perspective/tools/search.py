#!/usr/bin/env python3
"""
向量检索工具：从户晨风直播文字稿向量索引中检索相关内容。

用法:
    python3 tools/search.py "苹果人和安卓人的区别"
    python3 tools/search.py "山姆超市" --top 10
    python3 tools/search.py "学历重要吗" --date-from 2025-01-01
"""

import argparse
import base64
import json
import struct
import sys
from pathlib import Path

import numpy as np


def cosine_similarity(a, b):
    """计算余弦相似度。"""
    a = np.array(a, dtype=np.float32)
    b = np.array(b, dtype=np.float32)
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-10))


def decode_embedding(emb, fmt: str = None):
    """解码embedding，支持base64_float16和原始list两种格式。"""
    if fmt == "base64_float16" and isinstance(emb, str):
        binary = base64.b64decode(emb)
        return list(struct.unpack(f'{len(binary)//2}e', binary))
    return emb


def load_index(index_path: str) -> dict:
    """加载向量索引。"""
    with open(index_path, "r", encoding="utf-8") as f:
        index = json.load(f)
    # 预解码所有embedding
    fmt = index.get("embedding_format")
    if fmt:
        for c in index["chunks"]:
            c["embedding"] = decode_embedding(c["embedding"], fmt)
    return index


def search(query: str, index: dict, top_k: int = 5, date_from: str = None, date_to: str = None, speaker: str = None) -> list[dict]:
    """在向量索引中搜索。"""
    from fastembed import TextEmbedding

    model = TextEmbedding(model_name=index["model"])
    query_emb = list(model.embed([query]))[0].tolist()

    results = []
    for chunk in index["chunks"]:
        # 日期过滤
        if date_from and chunk["date"] < date_from:
            continue
        if date_to and chunk["date"] > date_to:
            continue

        score = cosine_similarity(query_emb, chunk["embedding"])
        results.append({
            "id": chunk["id"],
            "text": chunk["text"],
            "date": chunk["date"],
            "source": chunk["source"],
            "score": score,
        })

    results.sort(key=lambda x: x["score"], reverse=True)

    # 如果指定了说话人过滤，在排序后过滤
    if speaker:
        results = [r for r in results if speaker in r["text"]]

    return results[:top_k]


def format_results(results: list[dict]) -> str:
    """格式化搜索结果。"""
    if not results:
        return "未找到相关内容。"

    output = []
    for i, r in enumerate(results, 1):
        text = r["text"]
        # 截断过长文本
        if len(text) > 800:
            text = text[:800] + "..."
        output.append(f"### 结果 {i} [相关度: {r['score']:.3f}] [日期: {r['date']}]")
        output.append(f"来源: {r['source']}")
        output.append(f"```")
        output.append(text)
        output.append(f"```")
        output.append("")

    return "\n".join(output)


def main():
    parser = argparse.ArgumentParser(description="户晨风直播文字稿向量检索")
    parser.add_argument("query", help="搜索查询")
    parser.add_argument("--top", type=int, default=5, help="返回结果数量 (默认: 5)")
    parser.add_argument("--date-from", type=str, default=None, help="起始日期 (YYYY-MM-DD)")
    parser.add_argument("--date-to", type=str, default=None, help="截止日期 (YYYY-MM-DD)")
    parser.add_argument("--index", type=str, default=None, help="索引文件路径")
    parser.add_argument("--json", action="store_true", help="输出JSON格式")

    args = parser.parse_args()

    # 默认索引路径
    if args.index is None:
        args.index = str(Path(__file__).parent / "vector_index.json")

    if not Path(args.index).exists():
        print(f"错误: 索引文件不存在 {args.index}")
        print("请先运行: python3 tools/build_index.py /path/to/HuChenFeng")
        sys.exit(1)

    index = load_index(args.index)
    results = search(args.query, index, top_k=args.top, date_from=args.date_from, date_to=args.date_to)

    if args.json:
        # JSON输出（供程序化调用使用）
        print(json.dumps(results, ensure_ascii=False, indent=2))
    else:
        print(format_results(results))


if __name__ == "__main__":
    main()
