
import os
import json
import requests

from typing import Optional, List
from urllib.parse import urlparse


import openai
from dotenv import load_dotenv

from qdrant_client import QdrantClient
from qdrant_client.models import (Filter,
                                  FieldCondition,
                                  MatchAny,
                                  MatchValue,
                                  Range,
                                  PointStruct,
                                  VectorParams,
                                  Distance)

openai.api_key = "your-openai-key"

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
    return {
        "url": _trimmed_env("QDRANT_URL"),
        "host": _trimmed_env("QDRANT_HOST"),
        "port": _env_int("QDRANT_PORT", 6334),
        "api_key": _trimmed_env("QDRANT_API_KEY"),
        "prefer_grpc": _env_bool("QDRANT_PREFER_GRPC", True),
    }


def resolve_data_agent_qdrant_config() -> dict:
    """解析 DataAgent 专属 Qdrant 配置。"""
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


def get_embedding(text):
    response = openai.Embedding.create(
        input=text,
        model="text-embedding-ada-002"
    )
    return response['data'][0]['embedding']


class EmbeddingClient:
    def __init__(self, embedding_url: str):
        self.embedding_url = embedding_url
        self.timeout = 300000
    
    def get_vector_batch(self, texts: List[str]) -> Optional[List[List[float]]]:
        """
        批量获取文本向量
        :param texts: 文本列表
        :return: 二维浮点数列表，失败返回 None
        """
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


class QdrantRecall(object):
    def __init__(self, host, port, api_key, collection_name, qdrant_limit=30, threshhold=-1, timeout=0.5 * 100000000):
        self.collection_name = collection_name
        self.qdrant_limit = qdrant_limit
        self.qd_threshhold = threshhold
        
        self.distance = Distance.COSINE
        self.vector_size = 1024
        prefer_grpc = _env_bool("TR_QDRANT_PREFER_GRPC", True)
        
        # 初始化 Qdrant 客户端
        self.client = build_qdrant_client(
            url=None,
            host=host,
            port=int(port),
            timeout=int(timeout),
            prefer_grpc=prefer_grpc,
            api_key=api_key,
        )
        
        # 可选：检查集合是否存在，若不存在可创建（根据需求决定是否启用）
        self._ensure_collection()
    
    def _ensure_collection(self):
        try:
            self.client.get_collection(self.collection_name)
            print(f"✅ 集合 '{self.collection_name}' 已存在")
        except Exception:
            print(f"⚠️ 集合 '{self.collection_name}' 不存在，正在创建...")
            self.client.create_collection(
                collection_name=self.collection_name,
                vectors_config=VectorParams(
                    size=self.vector_size,
                    distance=self.distance,
                ),
            )
            print(f"✅ 成功创建集合 '{self.collection_name}'，维度={self.vector_size}，距离={self.distance.name}")
    
    def insert(self, points):
        """
        插入向量数据，支持单条或批量插入。

        :param points: 单个 PointStruct 或 List[PointStruct]
                      或 dict/list of dict 格式：
                         - 单条: {'id': 123, 'vector': [0.1, 0.2, ...], 'payload': {...}}
                         - 批量: [{'id': 1, 'vector': [...], 'payload': {}}, ...]
        :return: 操作结果（Qdrant 的 OperationResponse）
        """
        if isinstance(points, dict):
            # 单条插入
            point = PointStruct(
                id=points['id'],
                vector=points['vector'],
                payload=points.get('payload', {})
            )
            points = [point]
        elif isinstance(points, list) and len(points) > 0 and isinstance(points[0], dict):
            # 批量插入：从字典列表转换为 PointStruct 列表
            points = [
                PointStruct(
                    id=p['id'],
                    vector=p['vector'],
                    payload=p.get('payload', {})
                ) for p in points
            ]
        # 如果已经是 PointStruct 列表，则直接使用
        
        return self.client.upsert(
            collection_name=self.collection_name,
            points=points
        )
    
    def delete(self, ids=None, filters=None):
        """
        删除向量点，支持两种方式：
        1. 指定 id 列表删除
        2. 使用过滤条件删除（推荐用于复杂场景）

        :param ids: int 或 List[int]，要删除的点 ID
        :param filters: dict，过滤条件，格式同 search() 中的 filters
        :return: 删除操作响应
        """
        if ids is None and filters is None:
            raise ValueError("❌ 必须提供 ids 或 filters 中至少一个参数")
        
        if ids is not None:
            if isinstance(ids, int):
                ids = [ids]
            delete_request = self.client.delete(
                    collection_name=self.collection_name,
                    points=ids  # 👈 旧版支持
                )
        else:
            # 构建 filter 对象
            must_conditions = []
            for key, val in filters.items():
                if isinstance(val, (str, bool, int, float)):
                    must_conditions.append(
                        FieldCondition(key=key, match=MatchValue(value=val))
                    )
                elif isinstance(val, list):
                    must_conditions.append(
                        FieldCondition(key=key, match=MatchAny(any=val))
                    )
                elif isinstance(val, dict):
                    range_args = {}
                    for op in ["gte", "gt", "lte", "lt"]:
                        if op in val:
                            range_args[op] = val[op]
                    must_conditions.append(
                        FieldCondition(key=key, range=Range(**range_args))
                    )
                else:
                    raise ValueError(f"❌ 不支持的过滤值类型: {type(val)}，字段: {key}")
            
            query_filter = Filter(must=must_conditions) if must_conditions else None
            
            delete_request = self.client.delete(
            collection_name=self.collection_name,
            points_selector=query_filter  # 👈 旧版也支持
        )
        
        return self.client.delete(collection_name=self.collection_name, points_selector=delete_request)
    
    def search(self, query_vector, filters):
        must_conditions = []
        
        for key, val in filters.items():
            if isinstance(val, (str, bool, int, float)):
                must_conditions.append(
                    FieldCondition(
                        key=key,
                        match=MatchValue(value=val)
                    )
                )
            elif isinstance(val, list):
                must_conditions.append(
                    FieldCondition(
                        key=key,
                        match=MatchAny(any=val)
                    )
                )
            elif isinstance(val, dict):
                range_args = {}
                for op in ["gte", "gt", "lte", "lt"]:
                    if op in val:
                        range_args[op] = val[op]
                must_conditions.append(
                    FieldCondition(
                        key=key,
                        range=Range(**range_args)
                    )
                )
            else:
                raise ValueError(f"❌ 不支持的过滤值类型: {type(val)}，字段: {key}")
        
        query_filter = Filter(must=must_conditions) if must_conditions else None
        
        results = self.client.search(
            collection_name=self.collection_name,
            query_vector=query_vector,
            query_filter=query_filter,
            limit=self.qdrant_limit,
            score_threshold=self.qd_threshhold,
        )
        
        payloads = []
        for res in results:
            payload = res.payload
            payload.update({"score": res.score})
            payloads.append(payload)
        
        return payloads


def test():
    port = int(os.getenv("QDRANT_PORT", None))
    host = os.getenv("QDRANT_HOST", None)
    
    print(port, host)
    qd_recall = QdrantRecall(
                    host=os.getenv('QDRANT_HOST'),
                    port=port,
                    api_key=os.getenv("QDRANT_API_KEY", None),
                    collection_name="sop_plan",
                    qdrant_limit=10,
                    threshhold=-1,
                    timeout=0.5 * 100000000
                )
    SOP1 = [
        {
            "sop_desc": "对销售数据进行综合分析",
            "sop_name": "对销售数据进行综合分析",
            "sop_steps": [
                {
                    "steps": [
                        "使用分析工具，按月/季度/年统计销售额、利润等，识别周期性变化。"
                    ],
                    "title": "进行销售趋势分析"
                },
                {
                    "steps": [
                        "使用分析工具，对公司、消费者、小型企业等不同客户群体进行对比分析。"
                    ],
                    "title": "进行客户细分分析"
                },
                {
                    "steps": [
                        "使用分析工具，对地区/城市进行分析：挖掘区域市场差异，发现潜力市场。"
                    ],
                    "title": "销售客户细分分析"
                },
                {
                    "steps": [
                        "使用分析工具，对销售产品类别分析：家具、技术、办公用品等类别的销售表现、利润贡献。"
                    ],
                    "title": "销售产品类别分析"
                },
                {
                    "steps": [
                        "基于前面步骤的分析和结论，进行汇总展示最终的 HTML 报告"
                    ],
                    "title": "报告呈现"
                }
            ]
        },
        {
            "sop_desc": "分析产品的销售表现",
            "sop_name": "分析产品的销售表现",
            "sop_steps": [
                {
                    "steps": [
                        "通过{{数据分析工具}}统计不同类别和子类别产品的销售额、销售量和利润，找出畅销和滞销产品。"
                    ],
                    "title": "分析产品整体销售情况"
                },
                {
                    "steps": [
                        "通过{{数据分析工具}}分析哪些产品经常被一起购买，为捆绑销售或交叉销售提供依据。"
                    ],
                    "title": "分析产品的交叉销售情况"
                },
                {
                    "steps": [
                        "通过{{数据分析工具}}对客户分布与核心销售产品进行分析：分析不同地区（国家 / 地区、省 / 自治区、城市、区域）的客户数量和销售额分布，找出主要市场和潜在市场。并找到主要市场的核心销售产品",
                        "通过{{数据分析工具}}，研究不同细分客户群体的购买偏好、消费金额和利润贡献，制定不同产品的针对不同客户群体的营销策略。"
                    ],
                    "title": "分析产品在不同市场的销售情况"
                },
                {
                    "steps": [
                        "通过report_tool 撰写图文并茂的网页版报告"
                    ],
                    "title": "撰写报告"
                }
            ]
        },
    ]
    _sops = [
        {"description": sop["sop_desc"], "sop_id": str(index),
         "sop_name": sop["sop_name"],
         "sop_json_string": json.dumps(sop, ensure_ascii=False),
         "sop_string": sop["sop_name"] + "\n" + sop["sop_desc"] + "\n".join([step["title"] + "\n".join(step["steps"]) for step in sop["sop_steps"]]),
         "sop_type": "list",
         "vector_type": "vector_type"
         } for index, sop in enumerate(SOP1)]
    
    embedding_url = os.getenv("EMBEDDING_URL")
    emb_client = EmbeddingClient(embedding_url)
    
    points = []
    for index, sop in enumerate(_sops):
        sop.update({
            "vector_type": "name",
        })
        
        point = {
            "id": index + 1,
            "vector": emb_client.get_vector(sop["sop_name"]),
            "payload": sop,
        }
        points.append(point)
        
        sop.update({
            "vector_type": "sop_string",
        })
        point = {
            "id": index + len(_sops) + 1,
            "vector": emb_client.get_vector(sop["sop_string"]),
            "payload": sop,
        }
        points.append(point)
    
    qd_recall.insert(points=points)

if __name__ == "__main__":
    test()
