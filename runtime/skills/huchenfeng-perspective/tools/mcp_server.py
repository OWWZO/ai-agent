#!/usr/bin/env python3
"""
户晨风语录向量检索 MCP Server (stdio模式)。

为 Claude Code 提供向量检索能力，支持从490份直播文字稿中语义搜索。

配置方法 (在 .claude/settings.json 中添加):
{
  "mcpServers": {
    "huchenfeng-search": {
      "command": "python3",
      "args": ["tools/mcp_server.py"],
      "cwd": "/path/to/hu-chenfeng-skill"
    }
  }
}
"""

import base64
import json
import struct
import sys
from pathlib import Path

import numpy as np

INDEX_PATH = Path(__file__).parent / "vector_index.json"
_index = None
_model = None


def decode_embedding(emb, fmt: str = None):
    """解码embedding，支持base64_float16和原始list两种格式。"""
    if fmt == "base64_float16" and isinstance(emb, str):
        binary = base64.b64decode(emb)
        return list(struct.unpack(f'{len(binary)//2}e', binary))
    return emb


def get_index():
    global _index
    if _index is None:
        with open(INDEX_PATH, "r", encoding="utf-8") as f:
            _index = json.load(f)
        # 预解码所有embedding
        fmt = _index.get("embedding_format")
        if fmt:
            for c in _index["chunks"]:
                c["embedding"] = decode_embedding(c["embedding"], fmt)
    return _index


def get_model():
    global _model
    if _model is None:
        from fastembed import TextEmbedding
        index = get_index()
        _model = TextEmbedding(model_name=index["model"])
    return _model


def cosine_similarity(a, b):
    a = np.array(a, dtype=np.float32)
    b = np.array(b, dtype=np.float32)
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-10))


def search_quotes(query: str, top_k: int = 5, date_from: str = None, date_to: str = None, hu_only: bool = False) -> list[dict]:
    """语义搜索户晨风直播文字稿。"""
    index = get_index()
    model = get_model()

    query_emb = list(model.embed([query]))[0].tolist()

    results = []
    for chunk in index["chunks"]:
        if date_from and chunk["date"] < date_from:
            continue
        if date_to and chunk["date"] > date_to:
            continue
        if hu_only and "户晨风：" not in chunk["text"]:
            continue

        score = cosine_similarity(query_emb, chunk["embedding"])
        results.append({
            "text": chunk["text"],
            "date": chunk["date"],
            "source": chunk["source"],
            "score": round(score, 4),
        })

    results.sort(key=lambda x: x["score"], reverse=True)
    return results[:top_k]


# === MCP Protocol Implementation (stdio, JSON-RPC 2.0) ===

TOOLS = [
    {
        "name": "search_huchenfeng",
        "description": "从户晨风490份直播文字稿(2023-2025)的向量索引中语义搜索相关内容。可按日期范围筛选。返回最相关的文字稿片段及其日期和来源。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索查询，例如：'苹果人和安卓人'、'山姆超市的意义'、'大专不是大学生'、'吃苦有用吗'"
                },
                "top_k": {
                    "type": "integer",
                    "description": "返回结果数量，默认5",
                    "default": 5
                },
                "date_from": {
                    "type": "string",
                    "description": "起始日期 YYYY-MM-DD（可选）"
                },
                "date_to": {
                    "type": "string",
                    "description": "截止日期 YYYY-MM-DD（可选）"
                },
                "hu_only": {
                    "type": "boolean",
                    "description": "是否只返回户晨风本人说的话（排除连麦网友），默认false",
                    "default": False
                }
            },
            "required": ["query"]
        }
    }
]


def handle_request(request: dict) -> dict:
    method = request.get("method", "")
    req_id = request.get("id")

    if method == "initialize":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {}},
                "serverInfo": {
                    "name": "huchenfeng-search",
                    "version": "1.0.0"
                }
            }
        }
    elif method == "notifications/initialized":
        return None  # notification, no response
    elif method == "tools/list":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {"tools": TOOLS}
        }
    elif method == "tools/call":
        tool_name = request["params"]["name"]
        args = request["params"].get("arguments", {})

        if tool_name == "search_huchenfeng":
            results = search_quotes(
                query=args["query"],
                top_k=args.get("top_k", 5),
                date_from=args.get("date_from"),
                date_to=args.get("date_to"),
                hu_only=args.get("hu_only", False),
            )

            # 格式化输出
            output_parts = []
            for i, r in enumerate(results, 1):
                text = r["text"]
                if len(text) > 1000:
                    text = text[:1000] + "..."
                output_parts.append(
                    f"【结果{i}】[{r['date']}] 相关度:{r['score']}\n"
                    f"来源: {r['source']}\n"
                    f"{text}"
                )

            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "content": [{
                        "type": "text",
                        "text": "\n\n---\n\n".join(output_parts) if output_parts else "未找到相关内容。"
                    }]
                }
            }
        else:
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {"code": -32601, "message": f"Unknown tool: {tool_name}"}
            }
    elif method == "ping":
        return {"jsonrpc": "2.0", "id": req_id, "result": {}}
    else:
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "error": {"code": -32601, "message": f"Unknown method: {method}"}
        }


def main():
    """MCP stdio main loop."""
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            continue

        response = handle_request(request)
        if response is not None:
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
