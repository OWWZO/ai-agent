"""MRAG 评测数据集与 manifest 工具。"""

import json
from pathlib import Path
from typing import Iterable, TypeVar

from .canonical_keys import build_canonical_key, build_runtime_key
from .models import EvalQrel, EvalQuery, ManifestRecord

T = TypeVar("T")


def _preview_from_payload(payload: dict) -> str:
    """从 payload 生成便于人工标注的预览文本。"""

    if payload.get("text"):
        return str(payload["text"])[:200]
    if payload.get("image_path"):
        return str(payload["image_path"])
    if payload.get("page_path"):
        return str(payload["page_path"])
    return payload.get("filename") or "unknown"


def _source_ref_from_payload(payload: dict) -> str:
    """从 payload 生成来源引用。"""

    return (
        payload.get("file_path")
        or payload.get("image_path")
        or payload.get("page_path")
        or payload.get("filename")
        or "unknown"
    )


def build_manifest_records(payloads: list[dict]) -> list[ManifestRecord]:
    """将 payload 列表去重后转为 manifest records。"""

    record_map: dict[str, ManifestRecord] = {}
    for payload in payloads:
        chunk_type = str(payload.get("chunk_type") or "").lower()
        if chunk_type not in {"text", "image", "page"}:
            continue
        canonical_key = build_canonical_key(payload)
        if canonical_key in record_map:
            continue
        record_map[canonical_key] = ManifestRecord(
            canonical_key=canonical_key,
            evidence_type=chunk_type,
            title=str(payload.get("filename") or "unknown"),
            source_ref=str(_source_ref_from_payload(payload)),
            preview=str(_preview_from_payload(payload)),
            runtime_key=build_runtime_key(payload),
        )
    return list(record_map.values())


def dump_jsonl_records(path: str | Path, records: Iterable[T]) -> None:
    """将 Pydantic 模型或 dict 列表写入 JSONL。"""

    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as file:
        for record in records:
            if hasattr(record, "model_dump"):
                payload = record.model_dump(mode="json")
            else:
                payload = record
            file.write(json.dumps(payload, ensure_ascii=False) + "\n")


def _load_jsonl(path: str | Path) -> list[dict]:
    """读取 JSONL 文件。"""

    target = Path(path)
    if not target.exists():
        return []
    rows = []
    with target.open("r", encoding="utf-8") as file:
        for line in file:
            raw = line.strip()
            if not raw:
                continue
            rows.append(json.loads(raw))
    return rows


def load_queries(path: str | Path) -> list[EvalQuery]:
    """读取 query 数据集。"""

    return [EvalQuery.model_validate(row) for row in _load_jsonl(path)]


def load_qrels(path: str | Path) -> list[EvalQrel]:
    """读取 qrel 数据集。"""

    return [EvalQrel.model_validate(row) for row in _load_jsonl(path)]
