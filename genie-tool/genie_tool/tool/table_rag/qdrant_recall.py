
import os
import openai

import time
import requests
import json

from dotenv import load_dotenv
from typing import List, Optional
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue, MatchAny

from genie_tool.util.log_util import logger, timer
from genie_tool.util.qdrant_utils import EmbeddingClient

load_dotenv()  # 加载 .env 文件


class QdrantRecall(object):
    def __init__(self):
        QDRANT_HOST = os.getenv("TR_QDRANT_HOST")
        QDRANT_PORT = int(os.getenv("TR_QDRANT_PORT", 443))
        QDRANT_API_KEY = os.getenv("TR_QDRANT_API_KEY")
        
        self.collection_name = os.getenv("TR_QDRANT_COLLECTION_NAME")
        self.qdrant_limit = int(os.getenv('TR_QD_RECALL_TOP_K', 20))
        
        self.qd_threshhold = float(os.getenv('TR_QD_THRESHHOLD', 0.6))
        self.qdrant_timeout = int(os.getenv('TR_QD_TIMEOUT', 30))
        
        client = QdrantClient(
            host=QDRANT_HOST,
            grpc_port=int(QDRANT_PORT),
            timeout=self.qdrant_timeout,
            https=False,
            prefer_grpc=True,
            api_key=QDRANT_API_KEY,
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
    qd_threshhold = float(os.getenv('TR_QD_THRESHHOLD', 0.6))
    collectionName = os.getenv('TR_QDRANT_COLLECTION_NAME', None)
    qdrant_url = os.getenv('TR_QDRANT_URL', None)
    qdrant_limit = int(os.getenv('TR_QD_RECALL_TOP_K', 20))
    qdrant_timeout = int(os.getenv('TR_QD_TIMEOUT', 3000))
    
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
    emb_client = EmbeddingClient(embedding_url)
    query_vector = emb_client.get_vector(query)
    
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