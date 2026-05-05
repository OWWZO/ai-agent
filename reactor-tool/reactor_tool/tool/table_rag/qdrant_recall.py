
import os
import time
import requests
import json

from dotenv import load_dotenv
from typing import List, Optional
from qdrant_client.models import Filter, FieldCondition, MatchValue, MatchAny

from reactor_tool.util.log_util import logger, timer
from reactor_tool.util.qdrant_utils import (
    EmbeddingClient,
    build_qdrant_client,
    has_direct_qdrant_config,
    resolve_table_rag_qdrant_config,
)
from reactor_tool.tool.mrag.embedding.text_embedding import get_text_embedding_model

load_dotenv()  # 加载 .env 文件


class QdrantRecall(object):
    def __init__(self):
        qdrant_config = resolve_table_rag_qdrant_config()
        
        self.collection_name = os.getenv("TR_QDRANT_COLLECTION_NAME")
        self.qdrant_limit = int(os.getenv('TR_QD_RECALL_TOP_K'))
        
        self.qd_threshhold = float(os.getenv('TR_QD_THRESHHOLD'))
        self.qdrant_timeout = int(os.getenv('TR_QD_TIMEOUT'))
        
        client = build_qdrant_client(
            url=qdrant_config.get("url"),
            host=qdrant_config.get("host"),
            port=qdrant_config.get("port"),
            timeout=self.qdrant_timeout,
            prefer_grpc=qdrant_config.get("prefer_grpc"),
            api_key=qdrant_config.get("api_key"),
        )
        self.client = client
        
    def search(self, query_vector, model_code_list):
        query_filter = Filter(
            must=[
                FieldCondition(
                    key="modelCode",
                    match=MatchAny(any=model_code_list)
                )
            ]
        )
        
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

@timer("table_rag")
def get_qd_server_recall(query, model_code_list):
    qd_threshhold = float(os.getenv('TR_QD_THRESHHOLD'))
    collectionName = os.getenv('TR_QDRANT_COLLECTION_NAME', None)
    qdrant_url = os.getenv('TR_QDRANT_URL', None)
    qdrant_limit = int(os.getenv('TR_QD_RECALL_TOP_K'))
    qdrant_timeout = int(os.getenv('TR_QD_TIMEOUT'))
    
    body = {
        "scoreThreshold": qd_threshhold,
        "query": query,
        "keywordFilterMap": {
            "modelCode": model_code_list
        },
        "limit": qdrant_limit,
        "timeout": qdrant_timeout,
        "collectionName": collectionName
    }
    r = requests.post(qdrant_url, json=body)
    if r.status_code != 200 or "data" not in r.json():
        return []
    elif r.json()["data"] is None:
        return []
    
    # 使用示例
    data = r.json()["data"]
    return data

@timer("table_rag")
def get_qd_recall(query, model_code_list):
    embedding_url = os.getenv("TR_EMBEDDING_URL")
    if embedding_url:
        emb_client = EmbeddingClient(embedding_url)
        query_vector = emb_client.get_vector(query)
    else:
        query_vector = get_text_embedding_model().encode_text_batch([query])[0]
    
    qd_client = QdrantRecall()
    recall = qd_client.search(query_vector, model_code_list)
    return recall
    

if __name__ == "__main__":
    # 读取配置
    res1 = get_qd_recall("不同城市的销售额分布", model_code_list=["sales_count", "sales"])
    print(len(res1))
    # res2 = get_qd_server_recall("不同城市的销售额分布", model_code_list=["sales_count"])
    print(res1)
    # print(res2)
