"""
文本Reranker模块

该模块专门处理文本相关的重排序：
- 语义相关性重排序
- 关键词匹配优化
- 文本质量评估
- 多语言排序优化

主要功能：
1. Cross-encoder语义相关性评分
2. BM25分数融合优化
3. 文本质量和可读性评估
4. 查询-文档匹配度计算
5. 上下文相关性排序
6. 个性化排序策略
"""
import os
from abc import ABC, abstractmethod
from typing import Dict, Any

import dotenv
import requests

dotenv.load_dotenv()


class TextReranker(ABC):

    @abstractmethod
    def rerank(self, question: str, texts: list[str]) -> list[float]:
        pass


class APITextReranker(TextReranker):
    """文本重排序器类"""

    def __init__(self):
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": "Bearer " + os.getenv("TEXT_RERANKER_API_KEY"),
        }
        self.model = os.getenv("TEXT_RERANKER_MODEL_NAME")
        self.timeout = int(os.getenv("API_TIMEOUT", 300))
        self.max_document_length = int(os.getenv("TEXT_RERANKER_MAX_DOCUMENT_LENGTH", 8000))
        self.return_documents = os.getenv("TEXT_RERANKER_RETURN_DOCUMENTS", "true").lower() == "true"

    def _normalize_documents(self, texts: list[str]) -> list[str]:
        """在请求前统一裁剪文档长度，避免上游 rerank 服务直接拒绝。"""
        return [text[:self.max_document_length] for text in texts]

    @staticmethod
    def _resolve_top_n(document_count: int) -> int:
        """默认返回全部候选，允许通过环境变量显式收紧 top_n。"""
        configured_top_n = os.getenv("TEXT_RERANKER_TOP_N")
        if not configured_top_n:
            return document_count
        try:
            return max(1, int(configured_top_n))
        except ValueError as exc:
            raise ValueError("TEXT_RERANKER_TOP_N 必须是正整数") from exc

    def _prepare_request_data(self, question: str, texts: list[str]) -> Dict[str, Any]:
        normalized_texts = self._normalize_documents(texts)
        return {
            "model": self.model,
            "query": question,
            "documents": normalized_texts,
            "return_documents": self.return_documents,
            "top_n": self._resolve_top_n(len(normalized_texts)),
        }

    def _make_api_request(self, data: Dict[str, Any]) -> Dict[str, Any]:
        try:
            response = requests.post(
                os.getenv("TEXT_RERANKER_BASE_URL"),
                headers=self.headers,
                json=data,
                timeout=self.timeout,
            )

            if response.status_code != 200:
                raise requests.HTTPError(
                    f"API请求失败 [{response.status_code}]: {response.text}"
                )

            return response.json()

        except requests.exceptions.Timeout:
            raise Exception(f"请求超时 (>{self.timeout}s)")
        except requests.exceptions.ConnectionError:
            raise Exception("网络连接错误")
        except requests.exceptions.RequestException as e:
            raise Exception(f"请求异常: {e}")

    def _extract_scores(self, response_data: Dict[str, Any]) -> list[dict]:
        """兼容中转站与原生服务的不同返回结构。"""
        if isinstance(response_data.get("results"), list):
            return response_data["results"]

        output = response_data.get("output")
        if isinstance(output, dict) and isinstance(output.get("results"), list):
            return output["results"]

        raise ValueError(f"无法从 rerank 响应中提取 results: {response_data}")

    def rerank(self, question: str, texts: list[str]) -> list[float]:

        if not texts:
            return []

        request_data = self._prepare_request_data(question, texts)
        response_data = self._make_api_request(request_data)
        text_scores = self._extract_scores(response_data)
        # logger.info(f"text_scores: {text_scores}")
        score_list = [0.0] * len(texts)

        for i, text_score in enumerate(text_scores):
            score_list[text_score.get("index")] = text_score.get('relevance_score')

        return score_list


def get_text_reranker() -> TextReranker:
    text_reranker_type = os.getenv("TEXT_RERANKER_TYPE")
    if text_reranker_type.lower() == "api":
        return APITextReranker()
    else:
        raise ValueError(f"不支持的文本重排序器类型: {text_reranker_type}")
