# -*- coding: utf-8 -*-
"""Qdrant 客户端配置、Embedding 代理与向量召回工具。

供 table_rag / data_agent 等场景直连 Qdrant 或通过 HTTP embedding 服务取向量。
"""
import os
import json
import requests

from typing import Optional, List
from urllib.parse import urlparse


from dotenv import load_dotenv

from qdrant_client import QdrantClient

load_dotenv()


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _trimmed_env(name: str) -> Optional[str]:
    value = os.getenv(name)
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def _env_int(name: str, default: int) -> int:
    value = _trimmed_env(name)
    if value is None:
        return default
    return int(value)


def resolve_shared_qdrant_config() -> dict:
    """解析共享 Qdrant 配置。"""
    # 共享配置供通用 SOP 等场景使用；调用方应在建客户端前再判断 url/host 是否实际存在。
    return {
        "url": _trimmed_env("QDRANT_URL"),
        "host": _trimmed_env("QDRANT_HOST"),
        "port": _env_int("QDRANT_PORT", 6334),
        "api_key": _trimmed_env("QDRANT_API_KEY"),
        "prefer_grpc": _env_bool("QDRANT_PREFER_GRPC", True),
    }


def resolve_data_agent_qdrant_config() -> dict:
    """解析 DataAgent 专属 Qdrant 配置。"""
    # DataAgent 的 host 优先于 url，避免同时配置时把本地直连误判成云端 URL。
    override_url = _trimmed_env("DATA_AGENT_QDRANT_URL")
    override_host = _trimmed_env("DATA_AGENT_QDRANT_HOST")
    return {
        "url": None if override_host else override_url,
        "host": override_host,
        "port": _env_int("DATA_AGENT_QDRANT_PORT", 0),
        "api_key": _trimmed_env("DATA_AGENT_QDRANT_API_KEY"),
        "prefer_grpc": _env_bool("DATA_AGENT_QDRANT_PREFER_GRPC", False),
    }


def resolve_table_rag_qdrant_config() -> dict:
    """解析 table_rag 直连 Qdrant 配置，仅使用 DataAgent 配置。"""
    return resolve_data_agent_qdrant_config()


def has_direct_qdrant_config(config: dict) -> bool:
    """判断是否存在可用于直连的 Qdrant 配置。"""
    return bool(config.get("url") or config.get("host"))


def build_qdrant_client(
    *,
    url: Optional[str],
    host: Optional[str],
    port: int,
    api_key: Optional[str],
    prefer_grpc: bool,
    timeout: float,
) -> QdrantClient:
    """根据 url/host 二选一创建 Qdrant 客户端。"""
    # qdrant-client 对带 scheme 的地址使用 url，其余地址按 host 处理；两者不能同时传入。
    client_kwargs = {
        "grpc_port": int(port),
        "timeout": timeout,
        "prefer_grpc": prefer_grpc,
        "api_key": api_key,
    }
    if url:
        parsed = urlparse(url)
        if parsed.scheme:
            client_kwargs["url"] = url
        else:
            client_kwargs["host"] = url
    elif host:
        client_kwargs["host"] = host
    else:
        raise ValueError("Qdrant host/url is required")
    return QdrantClient(**client_kwargs)


class EmbeddingClient:
    """HTTP 批量 embedding 客户端，对接内部 / 共享向量服务。"""

    def __init__(self, embedding_url: str):
        self.embedding_url = embedding_url
        self.timeout = 300000

    def get_vector_batch(self, texts: List[str]) -> Optional[List[List[float]]]:
        """
        批量获取文本向量
        :param texts: 文本列表
        :return: 二维浮点数列表，失败返回 None
        """
        # embedding 服务约定直接返回二维向量列表；格式不符合时返回 None，让上层走降级路径。
        try:
            body = {
                "inputs": texts,
                "normalize": True
            }
            headers = {
                "Content-Type": "application/json"
            }
            
            response = requests.post(
                self.embedding_url,
                data=json.dumps(body),
                headers=headers,
                timeout=self.timeout  # 设置超时避免卡死
            )
            response.raise_for_status()  # 抛出 HTTP 错误
            
            result = response.json()
            # 假设返回的是直接的 List<List<Float>> 格式，如 [[0.1, 0.2, ...], [...]]
            if isinstance(result, list) and len(result) > 0 and isinstance(result[0], list):
                return result
            else:
                print(f"⚠️ 返回格式异常: {result}")
                return None
        
        except Exception as e:
            print(f"❌ embedding failed, error: {str(e)}")
            return None
    
    def get_vector(self, text: str) -> Optional[List[float]]:
        """
        获取单个文本的向量
        :param text: 输入文本
        :return: 浮点数列表，失败返回 None
        """
        vectors = self.get_vector_batch([text])
        if vectors and len(vectors) > 0:
            return vectors[0]
        return None

