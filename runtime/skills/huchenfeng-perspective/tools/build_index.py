#!/usr/bin/env python3
"""
将户晨风直播文字稿切片、嵌入并构建向量索引。

用法:
    python3 tools/build_index.py /path/to/HuChenFeng

输出:
    tools/vector_index.json  — 向量索引文件
"""

import base64
import json
import os
import re
import struct
import sys
from pathlib import Path


def split_into_chunks(text: str, date: str, max_chars: int = 1500, overlap: int = 200) -> list[dict]:
    """将文字稿按段落和长度切片，保留说话人标签。"""
    chunks = []
    # 按说话人轮次分割
    turns = re.split(r'\n(?=(?:户晨风|某网友)：)', text)

    current_chunk = ""
    current_speaker = ""

    for turn in turns:
        turn = turn.strip()
        if not turn:
            continue

        # 检测说话人
        speaker_match = re.match(r'^(户晨风|某网友)：', turn)
        speaker = speaker_match.group(1) if speaker_match else ""

        if len(current_chunk) + len(turn) > max_chars and current_chunk:
            chunks.append({
                "text": current_chunk.strip(),
                "date": date,
                "speaker": current_speaker,
            })
            # 保留overlap
            if overlap > 0 and len(current_chunk) > overlap:
                current_chunk = current_chunk[-overlap:] + "\n" + turn
            else:
                current_chunk = turn
        else:
            current_chunk += "\n" + turn if current_chunk else turn

        if speaker:
            current_speaker = speaker

    if current_chunk.strip():
        chunks.append({
            "text": current_chunk.strip(),
            "date": date,
            "speaker": current_speaker,
        })

    return chunks


def extract_date_from_filename(filepath: str) -> str:
    """从文件名提取日期。"""
    basename = Path(filepath).stem
    match = re.search(r'(\d{4}-\d{2}-\d{2})', basename)
    return match.group(1) if match else basename


def load_transcripts(source_dir: str) -> list[dict]:
    """加载所有文字稿并切片。"""
    all_chunks = []
    source_path = Path(source_dir)

    md_files = sorted(source_path.rglob("*.md"))
    # 排除 README.md, SUMMARY.md 等非文字稿文件
    skip_names = {"README.md", "SUMMARY.md", "Preface.md", "Acknowledgements.md", "videos.md", "LICENSE"}
    md_files = [f for f in md_files if f.name not in skip_names and not f.name.startswith(".")]

    print(f"找到 {len(md_files)} 个文字稿文件")

    for i, md_file in enumerate(md_files):
        if i % 50 == 0:
            print(f"  处理中... {i}/{len(md_files)}")

        date = extract_date_from_filename(str(md_file))
        text = md_file.read_text(encoding="utf-8")

        if len(text.strip()) < 100:
            continue

        chunks = split_into_chunks(text, date)
        for j, chunk in enumerate(chunks):
            chunk["id"] = f"{date}_{j}"
            chunk["source"] = str(md_file.relative_to(source_path))

        all_chunks.extend(chunks)

    print(f"共生成 {len(all_chunks)} 个文本块")
    return all_chunks


def build_embeddings(chunks: list[dict], model_name: str = "BAAI/bge-small-zh-v1.5") -> list[dict]:
    """使用 fastembed 生成嵌入向量。"""
    from fastembed import TextEmbedding

    print(f"加载嵌入模型: {model_name}")
    model = TextEmbedding(model_name=model_name)

    texts = [c["text"] for c in chunks]

    print(f"正在生成 {len(texts)} 个向量...")
    embeddings = list(model.embed(texts, batch_size=64))

    for i, emb in enumerate(embeddings):
        chunks[i]["embedding"] = emb.tolist()

    print("向量生成完成")
    return chunks


def save_index(chunks: list[dict], output_path: str):
    """保存向量索引（base64_float16紧凑格式）。"""
    index = {
        "model": "BAAI/bge-small-zh-v1.5",
        "dimension": len(chunks[0]["embedding"]) if chunks else 0,
        "embedding_format": "base64_float16",
        "count": len(chunks),
        "chunks": []
    }

    for c in chunks:
        # float64 -> float16 + base64 编码，大幅减小体积
        emb = c["embedding"]
        binary = struct.pack(f'{len(emb)}e', *emb)
        emb_b64 = base64.b64encode(binary).decode('ascii')

        text = c["text"]
        if len(text) > 1200:
            text = text[:1200]

        index["chunks"].append({
            "id": c["id"],
            "text": text,
            "date": c["date"],
            "source": c["source"],
            "embedding": emb_b64,
        })

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(',', ':'))

    size_mb = os.path.getsize(output_path) / 1024 / 1024
    print(f"索引已保存: {output_path} ({size_mb:.1f} MB, {len(chunks)} 个块)")


def main():
    if len(sys.argv) < 2:
        print("用法: python3 tools/build_index.py /path/to/HuChenFeng")
        sys.exit(1)

    source_dir = sys.argv[1]
    script_dir = Path(__file__).parent
    output_path = str(script_dir / "vector_index.json")

    if not Path(source_dir).is_dir():
        print(f"错误: 目录不存在 {source_dir}")
        sys.exit(1)

    chunks = load_transcripts(source_dir)
    chunks = build_embeddings(chunks)
    save_index(chunks, output_path)


if __name__ == "__main__":
    main()
