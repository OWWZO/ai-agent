# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/9
# =====================
import asyncio
import json
import os
from loguru import logger
from abc import ABC, abstractmethod
from typing import List
from urllib.parse import quote
import aiohttp
from bs4 import BeautifulSoup
try:
    from ddgs import DDGS
except ImportError:
    DDGS = None

from reactor_tool.model.document import Doc
from reactor_tool.util.log_util import timer


def _search_url_ok(url) -> bool:
    """Return True if url is set and valid for aiohttp (avoids InvalidUrlClientError)."""
    if not url or not str(url).strip():
        return False
    s = str(url).strip()
    return s.startswith(("http://", "https://"))


class SearchBase(ABC):
    """搜索基类"""

    def __init__(self):
        self._count = int(os.getenv("SEARCH_COUNT", 10))
        self._timeout = int(os.getenv("SEARCH_TIMEOUT", 99999))
        # 单 URL 抓取超时（秒），过大会导致墙内访问 Reddit/Threads/X 等长时间挂起后卡死
        self._parser_timeout = int(os.getenv("SEARCH_PARSER_TIMEOUT", 15))
        self._use_jd_gateway = os.getenv("USE_JD_SEARCH_GATEWAY", "true") == "true"

    @abstractmethod
    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        """抽象搜索方法"""
        raise NotImplementedError

    @staticmethod
    async def _fetch_content_with_jina_reader(source_url: str, timeout: int) -> str:
        """优先通过 Jina Reader 抓取清洗后的正文。"""
        if not _search_url_ok(source_url):
            return ""
        headers = {
            "Content-Type": "application/json",
            "X-Return-Format": "text",
            "X-Timeout": str(timeout),
        }
        jina_api_key = (os.getenv("JINA_API_KEY") or "").strip()
        if jina_api_key:
            headers["Authorization"] = f"Bearer {jina_api_key}"
        client_timeout = aiohttp.ClientTimeout(connect=5, total=timeout)
        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(
                        "https://r.jina.ai/",
                        json={"url": source_url},
                        headers=headers,
                        timeout=client_timeout,
                ) as response:
                    if response.status != 200:
                        logger.warning(f"jina reader skipped: url=[{source_url}] status={response.status}")
                        return ""
                    content = (await response.text()).strip()
                    return content
        except Exception as e:
            logger.warning(f"jina reader error: url=[{source_url}] error={e}")
            return ""

    @staticmethod
    async def _fetch_content_with_direct_http(source_url: str, timeout: int) -> str:
        """Jina Reader 不可用时，回退到直接抓取原始页面。"""
        if not _search_url_ok(source_url):
            return ""
        client_timeout = aiohttp.ClientTimeout(connect=5, total=timeout)
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(source_url, timeout=client_timeout) as response:
                    content_type = (response.content_type or "").lower()
                    if content_type not in [
                        "text/html", "text/plain", "text/xml", "application/json",
                        "application/xml", "application/octet-stream"
                    ]:
                        logger.warning(f"parser content-type[{response.content_type}] not parser: url=[{source_url}]")
                        return ""
                    raw_bytes = await response.read()
        except Exception as e:
            logger.warning(f"parser error: url=[{source_url}] error={e}")
            return ""

        try:
            raw_text = raw_bytes.decode("utf-8")
        except UnicodeDecodeError:
            raw_text = raw_bytes.decode("gb2312", errors="ignore")

        soup = BeautifulSoup(raw_text, "html.parser")
        return soup.get_text(" ", strip=True)

    @staticmethod
    @timer()
    async def parser(docs: List[Doc], timeout: int = 15, **kwargs) -> List[Doc]:
        use_jina_reader = kwargs.get("use_jina_reader", True)

        async def _resolve_content(doc: Doc) -> str:
            # 深度搜索默认改为直连抓取，只有显式开启时才尝试 Jina Reader。
            if use_jina_reader:
                jina_timeout = int(os.getenv("JINA_READER_TIMEOUT", timeout))
                jina_content = await SearchBase._fetch_content_with_jina_reader(doc.link, jina_timeout)
                if jina_content and jina_content.strip():
                    return jina_content.strip()
            direct_content = await SearchBase._fetch_content_with_direct_http(doc.link, timeout)
            return direct_content.strip() if direct_content else ""

        async with asyncio.TaskGroup() as tg:
            tasks = [tg.create_task(_resolve_content(doc)) for doc in docs]

        for doc, task in zip(docs, tasks):
            result = task.result()
            if result:
                doc.content = result
        return docs

    @timer()
    async def search_and_dedup(
            self, query: str, request_id: str = None, *args, **kwargs
    ) -> List[Doc]:
        """
        搜索并去重，同时删除没有内容的文档
        """
        try:
            docs = await self.search(query=query, request_id=request_id, *args, **kwargs)
        except aiohttp.InvalidURL as e:
            logger.warning(f"Search skipped (invalid URL): {e}")
            return []
        except aiohttp.client_exceptions.InvalidUrlClientError as e:
            logger.warning(f"Search skipped (invalid URL): {e}")
            return []
        except (aiohttp.ClientError, asyncio.TimeoutError, OSError) as e:
            # 单个搜索引擎异常时直接降级，避免影响混合搜索整体结果。
            logger.warning(f"{self.__class__.__name__} skipped due to request error: {e}")
            return []
        except Exception as e:
            # 兜底保护，保证某个搜索引擎失败时不会中断整个深度搜索流程。
            logger.exception(f"{self.__class__.__name__} skipped due to unexpected error: {e}")
            return []
        docs = await self.parser(
            docs=docs,
            timeout=self._parser_timeout,
            use_jina_reader=kwargs.get("use_jina_reader", True),
        )

        seen_docs = set()
        deduped_docs = []
        for doc in docs:
            if doc.content and doc.content not in seen_docs:
                deduped_docs.append(doc)
                seen_docs.add(doc.content)
        return deduped_docs


class DDGSearch(SearchBase):

    def __init__(self):
        super().__init__()
        self._engine = "ddg"
        self._region = os.getenv("DDG_REGION", "wt-wt")
        self._safesearch = os.getenv("DDG_SAFESEARCH", "moderate")

    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        if DDGS is None:
            logger.warning("ddgs library not installed, skip ddg search")
            return []

        def _run_text_search() -> List[dict]:
            client = DDGS(timeout=self._timeout)
            results = client.text(
                query,
                region=self._region,
                safesearch=self._safesearch,
                max_results=self._count,
            )
            return list(results) if results else []

        raw_results = await asyncio.to_thread(_run_text_search)
        return [
            Doc(
                doc_type="web_page",
                content=item.get("body", "") or item.get("snippet", ""),
                title=item.get("title", ""),
                link=item.get("href", item.get("url", "")),
                data={"search_engine": self._engine},
            )
            for item in raw_results
            if item.get("href", item.get("url", ""))
        ]


class BingSearch(SearchBase):

    def __init__(self):
        super().__init__()
        self._engine = "bing-search"
        self._url = os.getenv("BING_SEARCH_URL")
        self._api_key = os.getenv("BING_SEARCH_API_KEY")

        self.headers = {
            "Content-Type": "application/json",
        }
        self.set_auth()

    def set_auth(self):
        if self._use_jd_gateway:
            self.headers["Authorization"] = f"Bearer {self._api_key}"
        else:
            self.headers["Ocp-Apim-Subscription-Key"] = self._api_key

    def construct_body(self, query: str, request_id: str = None):
        if self._use_jd_gateway:
            return {
                "request_id": request_id,
                "model": self._engine,

                "messages": [{
                    "role": "user",
                    "content": query
                }],
                "count": self._count,
                "stream": False,
            }
        else:
            return {
                "q": query,
                "textDecorations": True
            }

    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        if not _search_url_ok(self._url):
            logger.warning(f"Bing search skipped: BING_SEARCH_URL not configured or invalid")
            return []
        body = self.construct_body(query, request_id)
        async with aiohttp.ClientSession() as session:
            async with session.post(self._url, json=body, headers=self.headers, timeout=self._timeout) as response:
                result = json.loads(await response.text())
                return [
                    Doc(
                        doc_type="web_page",
                        content=item.get("snippet", ""),
                        title=item.get("name", ""),
                        link=item.get("url", ""),
                        data={"search_engine": self._engine},
                    ) for item in result.get("webPages", {}).get("value", [])
                ]


class JinaSearch(BingSearch):

    def __init__(self):
        super().__init__()
        self._engine = "search_pro_jina"
        self._url = os.getenv("JINA_SEARCH_URL")
        self._api_key = os.getenv("JINA_SEARCH_API_KEY")

    def _build_search_url(self, query: str) -> str:
        """Jina Search 使用路径参数而不是 q 查询参数。"""
        return f"{self._url.rstrip('/')}/{quote(query, safe='')}"

    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        if not _search_url_ok(self._url):
            logger.warning(f"Jina search skipped: JINA_SEARCH_URL not configured or invalid")
            return []
        if self._use_jd_gateway:
            body = self.construct_body(query, request_id)
            async with aiohttp.ClientSession() as session:
                async with session.post(self._url, json=body, headers=self.headers, timeout=self._timeout) as response:
                    result = json.loads(await response.text())
                    return [
                        Doc(
                            doc_type="web_page",
                            content=item.get("content", ""),
                            title=item.get("title", ""),
                            link=item.get("link", ""),
                            data={"search_engine": self._engine},
                        ) for item in result.get("search_result", [])
                    ]
        else:
            headers = {
                "Accept": "application/json",
                "Authorization": f"Bearer {self._api_key}"
            }
            search_url = self._build_search_url(query)
            async with aiohttp.ClientSession() as session:
                async with session.get(search_url, headers=headers, timeout=self._timeout) as response:
                    if response.status != 200:
                        logger.error(f"Jina search failed: status={response.status}, body={await response.text()}")
                        return []
                    result = await response.json(content_type=None)
                    return [
                        Doc(
                            doc_type="web_page",
                            content=item.get("content", ""),
                            title=item.get("title", ""),
                            link=item.get("url", ""),
                            data={"search_engine": self._engine},
                        ) for item in result.get("data", [])
                    ]


class SogouSearch(JinaSearch):

    def __init__(self):
        super().__init__()
        self._engine = "search_pro_sogou"
        self._url = os.getenv("SOGOU_SEARCH_URL")
        self._api_key = os.getenv("SOGOU_SEARCH_API_KEY")


class SerperSearch(JinaSearch):

    def __init__(self):
        super().__init__()
        self._engine = "serper"
        self._url = os.getenv("SERPER_SEARCH_URL")
        self._api_key = os.getenv("SERPER_SEARCH_API_KEY")
        self.set_auth()
    
    def set_auth(self):
        self.headers["X-API-KEY"] = self._api_key

    def construct_body(self, query: str, request_id: str = None):
        return {
            "q": query,
            "count": self._count,
        }
    
    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        if not _search_url_ok(self._url):
            logger.warning(f"Serper search skipped: SERPER_SEARCH_URL not configured or invalid")
            return []
        body = self.construct_body(query, request_id)
        async with aiohttp.ClientSession() as session:
            async with session.post(self._url, json=body, headers=self.headers, timeout=self._timeout) as response:
                result = json.loads(await response.text())
                return [
                    Doc(
                        doc_type="web_page",
                        content=item.get("snippet", ""),
                        title=item.get("title", ""),
                        link=item.get("link", ""),
                        data={"search_engine": self._engine},
                    ) for item in result.get("organic", [])
                ]


class ExaSearch(SearchBase):

    def __init__(self):
        super().__init__()
        self._engine = "exa"
        self._url = os.getenv("EXA_SEARCH_URL", "https://api.exa.ai/search")
        self._api_key = os.getenv("EXA_API_KEY")
        self.headers = {
            "accept": "application/json",
            "content-type": "application/json",
            "x-api-key": self._api_key
        }

    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        if not _search_url_ok(self._url):
            logger.warning(f"Exa search skipped: EXA_SEARCH_URL not configured or invalid")
            return []
        
        body = {
            "query": query,
            "numResults": self._count,
            "useAutoprompt": True,
            "contents": {
                "text": {
                    "maxCharacters": 20000
                }
            }
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(self._url, json=body, headers=self.headers, timeout=self._timeout) as response:
                    if response.status != 200:
                        logger.error(f"Exa search failed: status={response.status}")
                        return []
                    
                    data = await response.json()
                    results = data.get("results", [])
                    
                    return [
                        Doc(
                            doc_type="web_page",
                            content=item.get("text", "") or item.get("extract", "") or "",
                            title=item.get("title", ""),
                            link=item.get("url", ""),
                            data={"search_engine": self._engine, "score": item.get("score")},
                        ) for item in results
                    ]
        except Exception as e:
            logger.error(f"Exa search error: {e}")
            return []


class MixSearch(BingSearch):

    def __init__(self):
        super().__init__()
        self._engine = "mix_search"
        self._ddg_engine = DDGSearch()
        self._bing_engine = BingSearch()
        self._jina_engine = JinaSearch()
        self._sogou_engine = SogouSearch()
        self._serp_engine = SerperSearch()
        self._exa_engine = ExaSearch()

    async def search(
            self, query: str, request_id: str = None,
            use_ddg: bool = True, use_bing: bool = False, use_jina: bool = False, use_sogou: bool = False,
            use_serp: bool = False, use_exa: bool = False, *args, **kwargs) -> List[Doc]:
        assert use_ddg or use_bing or use_jina or use_sogou or use_serp or use_exa
        use_jina_reader = kwargs.get("use_jina_reader", True)
        engines = []
        if use_ddg:
            engines.append(self._ddg_engine)
        if use_bing:
            engines.append(self._bing_engine)
        if use_jina:
            engines.append(self._jina_engine)
        if use_sogou:
            engines.append(self._sogou_engine)
        if use_serp:
            engines.append(self._serp_engine)
        if use_exa:
            engines.append(self._exa_engine)
        async with asyncio.TaskGroup() as tg:
            tasks = [
                tg.create_task(
                    engine.search_and_dedup(
                        query=query,
                        request_id=request_id,
                        use_jina_reader=use_jina_reader,
                    )
                )
                for engine in engines
            ]
        results = [task.result() for task in tasks]
        return [doc for docs in results for doc in docs]
