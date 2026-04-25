import os
import os

from dotenv import load_dotenv

from concurrent.futures import ThreadPoolExecutor, as_completed
from elasticsearch import Elasticsearch, helpers

from reactor_tool.util.log_util import logger

# 加载 .env 文件
load_dotenv()


def _trimmed(value):
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def _resolve_es_url(host, scheme):
    normalized_host = _trimmed(host)
    if not normalized_host:
        raise ValueError("Elasticsearch host is required (TR_ES_CONFIGS_HOST)")
    if "://" in normalized_host:
        return normalized_host.rstrip("/")
    normalized_scheme = _trimmed(scheme) or "http"
    return f"{normalized_scheme}://{normalized_host}"


def _resolve_auth_kwargs(config):
    api_key = _trimmed(config.get("api_key"))
    if api_key:
        return {"api_key": api_key}

    user = _trimmed(config.get("user"))
    password = config.get("password")
    if user:
        return {"http_auth": (user, password or "")}
    return {}


def get_docs(func):
    def wrapper(*args, **kwargs):
        doc = func(*args, **kwargs)["hits"]["hits"]
        if doc:
            retrieved_docs = []
            for item in doc:
                new_doc = item.get("_source")
                new_doc["score"] = item.get("_score")
                new_doc["_id"] = item.get("_id")
                retrieved_docs.append(new_doc)
            return retrieved_docs
        else:
            return []

    return wrapper


class ElasticsearchClient:
    def __init__(self, config):
        host = config.get("host")
        scheme = config.get("scheme", "http")
        es_url = _resolve_es_url(host, scheme)
        auth_kwargs = _resolve_auth_kwargs(config)
        analyzer_env = os.getenv("TR_ES_QUERY_ANALYZER")
        # 默认沿用 ik_max_word；如果用户显式配置为空串，则表示关闭自定义 analyzer，交给索引 mapping 处理。
        self._query_analyzer = "ik_max_word" if analyzer_env is None else _trimmed(analyzer_env)
        self._query_analyzer_available = self._query_analyzer is not None

        try:
            self._client = Elasticsearch(es_url, **auth_kwargs)
            self._thread_pool = ThreadPoolExecutor(max_workers=8)
        except Exception as e:
            logger.error(f"[ElasticsearchClient] Failed to connect: {e}")
            raise

    @staticmethod
    def _is_missing_analyzer_error(error: Exception) -> bool:
        error_text = str(error).lower()
        return "analyzer" in error_text and "not found" in error_text

    @staticmethod
    def _build_search_request_body(query, model_code_list, size, analyzer=None):
        value_match = {"query": query}
        if analyzer:
            value_match["analyzer"] = analyzer

        return {
            "size": size,
            "query": {
                "bool": {
                    "must": {
                        "match": {
                            "value": value_match
                        }
                    },
                    "filter": [
                        {
                          "terms": {
                            "modelCode": model_code_list,
                            "boost": 1
                          }
                        }
                      ],
                }
            },
            "sort": [
                {
                    "_score": {
                        "order": "desc"
                    }
                }
            ]
        }

    def search_body(self, index, search_body):
        def _query_by_ids(search_body):
            query = search_body.get("query", "这是一个es测试")
            model_code_list = search_body.get("model_code_list", [])
            size = search_body.get("size", 20)
            analyzer = self._query_analyzer if self._query_analyzer_available else None
            body = self._build_search_request_body(
                query=query,
                model_code_list=model_code_list,
                size=size,
                analyzer=analyzer,
            )

            try:
                doc = self._client.search(index=index, body=body)
            except Exception as error:
                if analyzer and self._is_missing_analyzer_error(error):
                    logger.warning(
                        f"[ElasticsearchClient] analyzer '{analyzer}' not found, "
                        f"fallback to index default analyzer. index={index}"
                    )
                    # 记录一次后续直接走无 analyzer 查询，避免 PlanSolve/table_rag 重复刷错。
                    self._query_analyzer_available = False
                    body = self._build_search_request_body(
                        query=query,
                        model_code_list=model_code_list,
                        size=size,
                        analyzer=None,
                    )
                    doc = self._client.search(index=index, body=body)
                else:
                    raise
            logger.debug(f"elastic body {body} search result length {len(doc['hits']['hits'])}")
            return {hit['_id']: hit.get("_source") for hit in doc['hits']['hits']}

        res = _query_by_ids(search_body)
        return res
    
    @get_docs
    def query_by_customize(
            self,
            index: str,
            body: dict,
            size: int = 10000
    ):
        return self._client.search(index=index, body=body, size=size)

    @get_docs
    def query_by_scroll(
            self,
            scroll_id,
            scroll: str = '1m'
    ):
        return self._client.scroll(scroll_id=scroll_id, scroll=scroll)

    def insert(
            self,
            index: str,
            data: list[dict]
    ):
        actions = [
            {
                "_op_type": "index",
                "_index": index,
                "_id": d["id"],
                "_source": d["body"]
                }
            for d in data
        ]
        return helpers.bulk(self._client, actions)

    def delete(
            self,
            index: str,
            ids: list,
    ):
        actions = [
            {
                "_op_type": "delete",
                "_index": index,
                "_id": _id
            }
            for _id in ids
        ]
        return helpers.bulk(self._client, actions)

    def get_mapping(
            self,
            index: str
    ):
        return self._client.indices.get_mapping(index=index)

def main():
    # 读取环境变量
    config = {}
    config["host"] = os.getenv("TR_ES_CONFIGS_HOST")
    config["port"] = os.getenv("port")
    config["scheme"] = os.getenv("TR_ES_CONFIGS_SCHEME", "http")
    config["user"] = os.getenv("TR_ES_CONFIGS_USER")
    config["password"] = os.getenv("TR_ES_CONFIGS_PASSWORD")
    config["api_key"] = os.getenv("TR_ES_CONFIGS_API_KEY")
    es_index = os.getenv("TR_ES_CONFIGS_INDEX", None)
    
    es_client = ElasticsearchClient(config)
    print(config)
    question_es_client = ElasticsearchClient(config)

    search_body = {"query": "cho人数", "model_code_list": ["ceRaxqPbeFgVb8F4h32htcC0HMhrfi"], "size": 100}

    res = question_es_client.search_body(es_index, search_body=search_body)
    print(res)
    
if __name__ == '__main__':
    main()
