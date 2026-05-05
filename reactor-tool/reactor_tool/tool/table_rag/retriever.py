# coding=utf-8
import json
import os
import time
from typing import Optional

from dotenv import load_dotenv

import requests
from reactor_tool.util.log_util import logger
from reactor_tool.tool.table_rag.es_client import ElasticsearchClient
from reactor_tool.tool.table_rag.qdrant_recall import get_qd_recall, get_qd_server_recall
from reactor_tool.util.qdrant_utils import has_direct_qdrant_config, resolve_table_rag_qdrant_config

# 加载 .env 文件
load_dotenv()


def _first_non_blank_env(*names, default=None):
    for name in names:
        value = os.getenv(name)
        if value is not None and value.strip():
            return value.strip()
    return default


def retrieved_cells_dict2map_key_val(retrieved_cells_dict, _few_shot_seprator):
    # key 去重，value 不会有重复的
    _map = {}
    for key, item in retrieved_cells_dict.items():
        assert "columnId" in item and "modelCode" in item, item
        column_id = item["columnId"]
        table_id = item["modelCode"]
        key = f"{table_id}-{column_id}"
        
        if key not in _map:
            few_shot_value = item["value"] if item["value"] else ""
            del item["value"]
            _map[key] = {
                "value": few_shot_value,
                "schema": item,
            }
        
        else:
            # 更新value
            few_shot_value = _map[key]["value"]
            few_shot_value += _few_shot_seprator + item["value"] if item["value"] else ""
            _map[key]["value"] = few_shot_value
    return _map


def retrieved_list2map_schema(retrieved_list):
    _map = {}
    for item in retrieved_list:
        assert "columnId" in item and "modelCode" in item, item
        column_id = item["columnId"]
        table_id = item["modelCode"]
        key = f"{table_id}-{column_id}"
        
        if key not in _map:
            _map[key] = item
        
    return _map


class Retriever:
    def __init__(self, request_id):
        self.es_recall_top_k = os.getenv("TR_ES_RECALL_TOP_K")

        self.request_id = request_id

        self.es_client = self.get_es_client()
        
    def get_es_client(self):
        # 读取环境变量
        config = {}
        config["host"] = _first_non_blank_env("DATA_AGENT_ES_HOST")
        config["port"] = os.getenv("port")
        config["scheme"] = _first_non_blank_env("DATA_AGENT_ES_SCHEME")
        config["user"] = _first_non_blank_env("DATA_AGENT_ES_USER")
        config["password"] = _first_non_blank_env("DATA_AGENT_ES_PASSWORD")
        config["api_key"] = _first_non_blank_env("DATA_AGENT_ES_API_KEY")
        self.es_index = _first_non_blank_env("TR_ES_CONFIGS_INDEX")

        # 未配置 ES 时不初始化客户端，避免因无 ES 导致 table_rag 报错
        host = config.get("host")
        if not host or (isinstance(host, str) and not host.strip()):
            logger.warning("[Retriever] DATA_AGENT_ES_HOST not set, ES client will not be initialized")
            return None

        try:
            es_client = ElasticsearchClient(config)
            return es_client
        except Exception as e:
            logger.error(f"[Retriever] Failed to initialize ES client: {e}")
            return None

    def es_recall(self, query : str ="cho人数",
                  model_code_list: Optional[str] = []):
        # ES 客户端未初始化时直接返回空，不报错
        if self.es_client is None:
            logger.debug("[Retriever] ES client not available, skipping ES recall")
            return {}

        search_body = {"query": query,
                       "model_code_list": model_code_list,
                       "size": self.es_recall_top_k}
        try:
            res = self.es_client.search_body(self.es_index, search_body=search_body)
            return res
        except Exception as e:
            logger.error(f"[Retriever] ES recall failed: {e}")
            return {}

    async def qdrant_recall(self, query, model_code_list):
        qdrant_enable_env = os.getenv("DATA_AGENT_QDRANT_ENABLE")
        if qdrant_enable_env is None:
            return []
        QDRANT_ENABLE = qdrant_enable_env.lower() == "true"
        TR_QDRANT_URL = os.getenv("TR_QDRANT_URL", None)
        if not QDRANT_ENABLE:
            return []
        if TR_QDRANT_URL:
            data = get_qd_server_recall(query, model_code_list)
        elif has_direct_qdrant_config(resolve_table_rag_qdrant_config()):
            data = get_qd_recall(query, model_code_list)
        else:
            data = []
        
        if data is None:
            data = []
        if not data:
            logger.error(f"No data found for {query}")
        return {"data": data}

    async def retrieve_schema(self, query, model_code_list):
        # 召回列名
        data = await self.qdrant_recall(query, model_code_list)
        
        return data

    async def retrieve_cell(self, query, model_code_list):
        # 召回值
        data = self.es_recall(query, model_code_list)
        return {"data": data}
    
    def qd_merge_rerank(self, qdrant_results):
        merge_map = {}
        for result in qdrant_results:
            key = result["modelCode"] + "-" + result["columnId"]
            if key not in merge_map:
                merge_map[key] = {
                    "schema": result,
                    "score": result["score"],
                }
            
            else:
                merge_map[key]["score"] += result["score"]
        
        # to list
        schema_list = []
        for key, result in merge_map.items():
            result["schema"]["score"] = result["score"]
            schema_list.append(result["schema"])
        
        # column score
        schema_list = sorted(schema_list, key=lambda k: k["score"], reverse=True)
        
        return schema_list
    
    def qd_es_merge(self, retrieved_cells, retrieved_columns):
        # retrieved_cells
        _few_shot_seprator = ";"
        retrieved_cells_key_val_map = retrieved_cells_dict2map_key_val(retrieved_cells, _few_shot_seprator)
        retrieved_columns_schema_map = retrieved_list2map_schema(retrieved_columns)
        # 需要把retrieveled few shots 放到columns里面去
        for key, schema_val in retrieved_cells_key_val_map.items():
            
            if key in retrieved_columns_schema_map:
                _few_shot = retrieved_columns_schema_map[key]["fewShot"]
                if _few_shot:
                    _few_shot = schema_val["value"] + _few_shot_seprator + _few_shot
                else:
                    _few_shot = schema_val["value"]
            else:
                retrieved_columns_schema_map[key] = schema_val["schema"]
                _few_shot = schema_val["value"]
            
            retrieved_columns_schema_map[key]["fewShot"] = _few_shot
            
        retrieved_columns_schema_list = [val for key, val in retrieved_columns_schema_map.items()]
        return retrieved_columns_schema_list
