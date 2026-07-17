# -*- coding: utf-8 -*-
"""SOP 工作台：Qdrant 权威读写（双向量 name + sop_string）。

连接配置与 MRAG 一致：优先 QDRANT_URL（云端），否则 QDRANT_HOST/PORT。
Embedding 优先 EMBEDDING_URL，否则复用 TEXT_EMBEDDING_* 共享模型。
"""
from __future__ import annotations

import json
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from loguru import logger
from qdrant_client.models import (
    Distance,
    FieldCondition,
    Filter,
    FilterSelector,
    MatchValue,
    PayloadSchemaType,
    PointIdsList,
    PointStruct,
    VectorParams,
)

from reactor_tool.util.qdrant_utils import (
    EmbeddingClient,
    build_qdrant_client,
    has_direct_qdrant_config,
    resolve_shared_qdrant_config,
)


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _env_str(name: str, default: Optional[str] = None) -> Optional[str]:
    value = os.getenv(name)
    if value is None:
        return default
    normalized = value.strip()
    return normalized or default


def resolve_sop_collection_name() -> str:
    return _env_str("SOP_COLLECTION_NAME", "sop_plan") or "sop_plan"


def build_sop_string(sop_name: str, sop_desc: str, sop_steps: List[Dict[str, Any]]) -> str:
    parts = [sop_name or "", sop_desc or ""]
    for step in sop_steps or []:
        title = str(step.get("title") or "").strip()
        if title:
            parts.append(title)
        for item in step.get("steps") or []:
            text = str(item or "").strip()
            if text:
                parts.append(text)
    return "\n".join(parts)


def normalize_steps(raw_steps: Any) -> List[Dict[str, Any]]:
    if not isinstance(raw_steps, list):
        return []
    steps: List[Dict[str, Any]] = []
    for item in raw_steps:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title") or "").strip()
        children = item.get("steps") or []
        if not isinstance(children, list):
            children = [str(children)]
        steps.append(
            {
                "title": title,
                "steps": [str(child).strip() for child in children if str(child).strip()],
            }
        )
    return steps


def point_id_for(sop_id: str, vector_type: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"sop:{sop_id}:{vector_type}"))


def payload_to_sop_record(payload: Dict[str, Any]) -> Dict[str, Any]:
    sop_id = str(payload.get("sop_id") or "").strip()
    sop_name = str(payload.get("sop_name") or "").strip()
    sop_desc = str(
        payload.get("sop_desc")
        or payload.get("description")
        or ""
    ).strip()
    sop_type = str(payload.get("sop_type") or "list").strip() or "list"
    status = str(payload.get("status") or "online").strip().lower()
    if status not in {"online", "offline", "draft"}:
        status = "online"

    steps: List[Dict[str, Any]] = []
    raw_json = payload.get("sop_json_string")
    if isinstance(raw_json, str) and raw_json.strip():
        try:
            parsed = json.loads(raw_json)
            if isinstance(parsed, dict):
                steps = normalize_steps(parsed.get("sop_steps") or parsed.get("steps"))
                sop_name = sop_name or str(parsed.get("sop_name") or "").strip()
                sop_desc = sop_desc or str(parsed.get("sop_desc") or "").strip()
        except Exception:
            steps = []
    if not steps:
        steps = normalize_steps(payload.get("sop_steps"))

    return {
        "sop_id": sop_id,
        "sop_name": sop_name,
        "sop_desc": sop_desc,
        "sop_type": sop_type,
        "sop_steps": steps,
        "status": status,
        "updated_at": payload.get("updated_at"),
        "created_at": payload.get("created_at"),
        "sop_string": payload.get("sop_string") or build_sop_string(sop_name, sop_desc, steps),
    }


class SopWorkspaceService:
    """SOP CRUD + 试召回，Qdrant 为唯一存储。"""

    def __init__(self, request_id: str = "sop-workspace"):
        self.request_id = request_id
        self.collection_name = resolve_sop_collection_name()
        self.vector_size = int(os.getenv("TEXT_EMBEDDING_DIMENSION", "1024") or 1024)
        self._client = None

    def _require_client(self):
        if self._client is not None:
            return self._client

        config = resolve_shared_qdrant_config()
        if not has_direct_qdrant_config(config):
            raise RuntimeError(
                "缺少 Qdrant 配置：请设置 QDRANT_URL（推荐）或 QDRANT_HOST/QDRANT_PORT"
            )

        timeout = float(os.getenv("QDRANT_TIMEOUT", "30") or 30)
        self._client = build_qdrant_client(
            url=config.get("url"),
            host=config.get("host"),
            port=int(config.get("port") or 6334),
            api_key=config.get("api_key"),
            prefer_grpc=bool(config.get("prefer_grpc")),
            timeout=timeout,
        )
        self._ensure_collection(self._client)
        return self._client

    def _ensure_collection(self, client) -> None:
        try:
            client.get_collection(self.collection_name)
        except Exception:
            logger.info(f"创建 SOP collection: {self.collection_name}")
            client.create_collection(
                collection_name=self.collection_name,
                vectors_config=VectorParams(
                    size=self.vector_size,
                    distance=Distance.COSINE,
                ),
            )
        self._ensure_payload_indexes(client)

    def _ensure_payload_indexes(self, client) -> None:
        """云端 Qdrant 过滤字段必须有 payload index。"""
        for field_name in ("vector_type", "sop_id", "status"):
            try:
                client.create_payload_index(
                    collection_name=self.collection_name,
                    field_name=field_name,
                    field_schema=PayloadSchemaType.KEYWORD,
                )
            except Exception as error:
                # 已存在索引时忽略
                message = str(error).lower()
                if "already" in message or "exists" in message:
                    continue
                logger.warning(
                    f"create payload index failed field={field_name}: {error}"
                )

    def _embed_texts(self, texts: List[str]) -> List[List[float]]:
        embedding_url = _env_str("EMBEDDING_URL") or _env_str("TR_EMBEDDING_URL")
        if embedding_url:
            vectors = EmbeddingClient(embedding_url).get_vector_batch(texts)
            if not vectors or len(vectors) != len(texts):
                raise RuntimeError("EMBEDDING_URL 向量服务返回异常")
            return vectors

        try:
            from reactor_tool.tool.mrag.embedding.text_embedding import get_text_embedding_model

            vectors = get_text_embedding_model().encode_text_batch(texts)
        except Exception as error:
            raise RuntimeError(
                f"embedding 失败：未配置 EMBEDDING_URL，且 TEXT_EMBEDDING_* 不可用: {error}"
            ) from error

        if not vectors or len(vectors) != len(texts):
            raise RuntimeError("TEXT_EMBEDDING 返回向量数量不匹配")
        return vectors

    def _scroll_name_payloads(self, limit: int = 500) -> List[Dict[str, Any]]:
        client = self._require_client()
        query_filter = Filter(
            must=[FieldCondition(key="vector_type", match=MatchValue(value="name"))]
        )
        records: List[Dict[str, Any]] = []
        next_offset = None
        while True:
            points, next_offset = client.scroll(
                collection_name=self.collection_name,
                scroll_filter=query_filter,
                limit=min(100, max(1, limit - len(records))),
                offset=next_offset,
                with_payload=True,
                with_vectors=False,
            )
            for point in points or []:
                payload = dict(point.payload or {})
                payload["_point_id"] = point.id
                records.append(payload)
            if next_offset is None or len(records) >= limit:
                break
        return records

    def list_sops(
        self,
        keyword: str = "",
        status: Optional[str] = None,
        limit: int = 200,
    ) -> List[Dict[str, Any]]:
        keyword_norm = (keyword or "").strip().lower()
        status_norm = (status or "").strip().lower() or None
        try:
            payloads = self._scroll_name_payloads(limit=max(limit, 50))
        except Exception as error:
            logger.warning(f"list SOP failed: {error}")
            raise

        items: List[Dict[str, Any]] = []
        for payload in payloads:
            record = payload_to_sop_record(payload)
            if not record["sop_id"]:
                continue
            if status_norm and record["status"] != status_norm:
                continue
            if keyword_norm:
                blob = f"{record['sop_name']}\n{record['sop_desc']}".lower()
                if keyword_norm not in blob:
                    continue
            items.append(record)

        items.sort(
            key=lambda item: (
                item.get("updated_at") or item.get("created_at") or "",
                item.get("sop_name") or "",
            ),
            reverse=True,
        )
        return items[:limit]

    def get_sop(self, sop_id: str) -> Optional[Dict[str, Any]]:
        sop_id = str(sop_id or "").strip()
        if not sop_id:
            return None
        for item in self.list_sops(limit=500):
            if item["sop_id"] == sop_id:
                return item
        return None

    def upsert_sop(
        self,
        *,
        sop_id: Optional[str],
        sop_name: str,
        sop_desc: str,
        sop_type: str = "list",
        sop_steps: Optional[List[Dict[str, Any]]] = None,
        status: str = "online",
    ) -> Dict[str, Any]:
        name = (sop_name or "").strip()
        desc = (sop_desc or "").strip()
        if not name:
            raise ValueError("sop_name 不能为空")

        steps = normalize_steps(sop_steps)
        status_norm = (status or "online").strip().lower()
        if status_norm not in {"online", "offline", "draft"}:
            raise ValueError("status 仅支持 online/offline/draft")

        resolved_id = str(sop_id or "").strip() or str(uuid.uuid4())
        now = _utc_now_iso()
        existing = self.get_sop(resolved_id)
        created_at = (existing or {}).get("created_at") or now

        core = {
            "sop_name": name,
            "sop_desc": desc,
            "sop_steps": steps,
        }
        sop_string = build_sop_string(name, desc, steps)
        sop_json_string = json.dumps(core, ensure_ascii=False)

        base_payload = {
            "sop_id": resolved_id,
            "sop_name": name,
            "sop_desc": desc,
            "description": desc,
            "sop_type": (sop_type or "list").strip() or "list",
            "sop_steps": steps,
            "sop_string": sop_string,
            "sop_json_string": sop_json_string,
            "status": status_norm,
            "created_at": created_at,
            "updated_at": now,
        }

        name_vec, string_vec = self._embed_texts([name, sop_string])
        client = self._require_client()
        name_payload = {**base_payload, "vector_type": "name"}
        string_payload = {**base_payload, "vector_type": "sop_string"}

        self._delete_by_sop_id(resolved_id)
        client.upsert(
            collection_name=self.collection_name,
            points=[
                PointStruct(
                    id=point_id_for(resolved_id, "name"),
                    vector=name_vec,
                    payload=name_payload,
                ),
                PointStruct(
                    id=point_id_for(resolved_id, "sop_string"),
                    vector=string_vec,
                    payload=string_payload,
                ),
            ],
        )
        return payload_to_sop_record(name_payload)

    def delete_sop(self, sop_id: str) -> bool:
        sop_id = str(sop_id or "").strip()
        if not sop_id:
            raise ValueError("sop_id 不能为空")
        self._delete_by_sop_id(sop_id)
        return True

    def _delete_by_sop_id(self, sop_id: str) -> None:
        client = self._require_client()
        stable_ids = [
            point_id_for(sop_id, "name"),
            point_id_for(sop_id, "sop_string"),
        ]
        try:
            client.delete(
                collection_name=self.collection_name,
                points_selector=PointIdsList(points=stable_ids),
            )
        except Exception as error:
            logger.warning(f"delete stable sop points failed sop_id={sop_id}: {error}")

        try:
            client.delete(
                collection_name=self.collection_name,
                points_selector=FilterSelector(
                    filter=Filter(
                        must=[FieldCondition(key="sop_id", match=MatchValue(value=sop_id))]
                    )
                ),
            )
        except Exception as error:
            try:
                client.delete(
                    collection_name=self.collection_name,
                    points_selector=Filter(
                        must=[FieldCondition(key="sop_id", match=MatchValue(value=sop_id))]
                    ),
                )
            except Exception as nested:
                logger.warning(
                    f"delete filtered sop points failed sop_id={sop_id}: {error}; nested={nested}"
                )

    def set_status(self, sop_id: str, status: str) -> Dict[str, Any]:
        current = self.get_sop(sop_id)
        if not current:
            raise ValueError(f"SOP 不存在: {sop_id}")
        return self.upsert_sop(
            sop_id=current["sop_id"],
            sop_name=current["sop_name"],
            sop_desc=current["sop_desc"],
            sop_type=current.get("sop_type") or "list",
            sop_steps=current.get("sop_steps") or [],
            status=status,
        )

    def recall_test(self, query: str) -> Dict[str, Any]:
        from reactor_tool.tool.plan_sop import PlanSOP

        plan = PlanSOP(self.request_id)
        mode, choosed = plan.sop_choose(query=query, sop_list=[])
        hits: List[Dict[str, Any]] = []
        try:
            raw = plan.sop_recall(query, vector_type="name")
            for item in raw or []:
                hits.append(
                    {
                        "sop_id": str(getattr(item, "sop_id", "")),
                        "sop_name": str(getattr(item, "sop_name", "")),
                        "score": getattr(item, "score", None),
                        "status": getattr(item, "status", "online"),
                    }
                )
        except Exception as error:
            logger.warning(f"recall_test hits failed: {error}")

        return {
            "sop_mode": mode,
            "choosed_sop_string": choosed,
            "hits": hits,
        }
