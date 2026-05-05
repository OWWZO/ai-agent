# DeepSearch DDG + Jina Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace DeepSearch's paid search backends with `DuckDuckGo` search plus `Jina Reader` content fetching while keeping the existing Java-to-Python API, SSE stages, typed tool output, file upload, and replay behavior unchanged.

**Architecture:** Keep `reactor_tool/tool/deepsearch.py` and the Java `deep_search` contract unchanged. Replace the provider implementation inside `reactor_tool/tool/search_component/search_engine.py` so query decomposition still produces sub-queries, DDG returns candidate links/snippets, Jina Reader becomes the primary page-content fetcher, and the existing direct `aiohttp + BeautifulSoup` parser remains as fallback. Result staging (`extend/search/report`), file uploads, and structured output stay unchanged, so no schema, Java DTO, or frontend changes are required.

**Tech Stack:** Python 3.11, FastAPI, `aiohttp`, `beautifulsoup4`, `ddgs`, existing Reactor Tool file service, `unittest`

---

## File Map

- Modify: `reactor-tool/reactor_tool/tool/search_component/search_engine.py`
  责任：新增 `DDGSearch` 实现，补充 `Jina Reader` 正文抓取链路，并把当前 `parser()` 重构为“Jina 优先、原始 HTTP 兜底”的正文抓取流程。
- Modify: `reactor-tool/reactor_tool/tool/deepsearch.py`
  责任：将默认搜索引擎切换到 `ddg`，但保持 `DeepSearch.run()` 的流式输出协议不变。
- Modify: `reactor-tool/reactor_tool/model/protocal.py`
  责任：补齐 `DeepSearchRequest.search_engines` 的注释说明，声明支持 `ddg`。
- Modify: `reactor-tool/.env_template`
  责任：把默认搜索引擎配置切到 `ddg`，新增 `Jina Reader` 抓取相关环境变量说明。
- Modify: `reactor-tool/pyproject.toml`
  责任：增加 `ddgs` 依赖；不新增 `httpx`，统一复用现有 `aiohttp` 技术栈。
- Modify: `reactor-tool/README.md`
  责任：补充 DeepSearch 新默认配置与 Jina Reader 抓取说明。
- Create: `reactor-tool/tests/test_search_engine.py`
  责任：覆盖 DDG 结果归一化、Jina Reader 优先抓取、Jina 失败时 fallback 到原始 HTTP parser、结果去重与空内容过滤。
- Create: `reactor-tool/tests/test_deepsearch_engine_selection.py`
  责任：覆盖 `DeepSearch` 默认搜索引擎从 `bing` 切换到 `ddg`，并验证显式 `search_engines` 仍可覆盖默认配置。

## Constraints

- 不修改 Java 侧 `DeepSearchTool`、`DeepSearchRequest`、数据库 schema、tool output 结构或前端展示。
- 不引入“深/浅搜索双模式”；本次仅替换底层搜索来源与正文抓取方式。
- 不把 `image_search` 并入 `deepsearch` 主链路；图片搜索后续作为报告配图增强单独规划。
- `Jina Reader` 失败时必须保留 direct HTTP 兜底，避免单点依赖导致整条链路不可用。

## Verification Commands

- `cd reactor-tool && uv run python -m unittest tests.test_search_engine -v`
- `cd reactor-tool && uv run python -m unittest tests.test_deepsearch_engine_selection -v`
- `cd reactor-tool && uv run python -m unittest tests.test_search_engine tests.test_deepsearch_engine_selection -v`

## Task 1: Lock Regression Tests First

**Files:**
- Create: `reactor-tool/tests/test_search_engine.py`
- Create: `reactor-tool/tests/test_deepsearch_engine_selection.py`

- [ ] **Step 1: Write the failing search-engine regression test**

```python
import unittest
from unittest.mock import AsyncMock, Mock, patch

from reactor_tool.model.document import Doc
from reactor_tool.tool.search_component.search_engine import DDGSearch, SearchBase


class SearchEngineIntegrationTest(unittest.IsolatedAsyncioTestCase):

    @patch("reactor_tool.tool.search_component.search_engine.DDGS")
    async def test_should_normalize_ddg_results_into_docs(self, mock_ddgs):
        mock_client = Mock()
        mock_client.text.return_value = [
            {
                "title": "Result A",
                "href": "https://example.com/a",
                "body": "snippet a",
            }
        ]
        mock_ddgs.return_value = mock_client

        docs = await DDGSearch().search("deepsearch 替换搜索引擎", request_id="req-1")

        self.assertEqual(1, len(docs))
        self.assertEqual("Result A", docs[0].title)
        self.assertEqual("https://example.com/a", docs[0].link)
        self.assertEqual("snippet a", docs[0].content)
        self.assertEqual("ddg", docs[0].data["search_engine"])

    @patch.object(SearchBase, "_fetch_content_with_direct_http", new_callable=AsyncMock)
    @patch.object(SearchBase, "_fetch_content_with_jina_reader", new_callable=AsyncMock)
    async def test_should_use_jina_reader_content_when_available(self, mock_jina, mock_direct):
        mock_jina.return_value = "clean article body"
        mock_direct.return_value = "fallback body"
        docs = [Doc(doc_type="web_page", title="A", link="https://example.com/a", content="snippet")]

        parsed = await SearchBase.parser(docs=docs, timeout=15)

        self.assertEqual("clean article body", parsed[0].content)
        mock_direct.assert_not_awaited()

    @patch.object(SearchBase, "_fetch_content_with_direct_http", new_callable=AsyncMock)
    @patch.object(SearchBase, "_fetch_content_with_jina_reader", new_callable=AsyncMock)
    async def test_should_fallback_to_direct_http_when_jina_reader_returns_empty(self, mock_jina, mock_direct):
        mock_jina.return_value = ""
        mock_direct.return_value = "fallback body"
        docs = [Doc(doc_type="web_page", title="A", link="https://example.com/a", content="snippet")]

        parsed = await SearchBase.parser(docs=docs, timeout=15)

        self.assertEqual("fallback body", parsed[0].content)
        mock_direct.assert_awaited()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd reactor-tool && uv run python -m unittest tests.test_search_engine -v`

Expected: FAIL with errors such as `ImportError: cannot import name 'DDGSearch'` or `AttributeError: type object 'SearchBase' has no attribute '_fetch_content_with_jina_reader'`

- [ ] **Step 3: Write the failing DeepSearch default-engine selection test**

```python
import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.deepsearch import DeepSearch


class DeepSearchEngineSelectionTest(unittest.TestCase):

    def test_should_default_to_ddg_when_env_is_empty(self):
        with patch.dict(os.environ, {"USE_SEARCH_ENGINE": ""}, clear=False):
            search = DeepSearch()
        self.assertEqual(["ddg"], search.engines)

    def test_should_respect_explicit_search_engines_argument(self):
        search = DeepSearch(engines=["ddg"])
        self.assertEqual(["ddg"], search.engines)
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd reactor-tool && uv run python -m unittest tests.test_deepsearch_engine_selection -v`

Expected: FAIL because `DeepSearch` does not expose normalized `engines` and still defaults to `bing`

- [ ] **Step 5: Commit the failing tests**

```bash
git add reactor-tool/tests/test_search_engine.py reactor-tool/tests/test_deepsearch_engine_selection.py
git commit -m "test: lock deepsearch ddg and jina regression cases"
```

## Task 2: Add DDG Search Provider and Engine Selection

**Files:**
- Modify: `reactor-tool/reactor_tool/tool/search_component/search_engine.py`
- Modify: `reactor-tool/reactor_tool/tool/deepsearch.py`
- Modify: `reactor-tool/reactor_tool/model/protocal.py`
- Modify: `reactor-tool/pyproject.toml`

- [ ] **Step 1: Add the failing dependency and provider contract changes**

```toml
dependencies = [
    # ...
    "ddgs>=9.10.0",
]
```

```python
class DDGSearch(SearchBase):

    def __init__(self):
        super().__init__()
        self._engine = "ddg"

    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        raise NotImplementedError("DDGSearch not implemented yet")
```

- [ ] **Step 2: Run the focused tests**

Run: `cd reactor-tool && uv run python -m unittest tests.test_search_engine tests.test_deepsearch_engine_selection -v`

Expected: FAIL because the provider stub exists but search normalization and engine selection are still incomplete

- [ ] **Step 3: Implement minimal DDG search support and engine normalization**

```python
class DDGSearch(SearchBase):

    def __init__(self):
        super().__init__()
        self._engine = "ddg"
        self._region = os.getenv("DDG_REGION", "wt-wt")
        self._safesearch = os.getenv("DDG_SAFESEARCH", "moderate")

    async def search(self, query: str, request_id: str = None, *args, **kwargs) -> List[Doc]:
        def _run_text_search() -> List[dict]:
            from ddgs import DDGS
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
        ]
```

```python
class DeepSearch:
    def __init__(self, engines: List[str] = []):
        normalized_engines = [engine.strip().lower() for engine in engines if engine and engine.strip()]
        if not normalized_engines:
            env_value = os.getenv("USE_SEARCH_ENGINE", "ddg")
            normalized_engines = [engine.strip().lower() for engine in env_value.split(",") if engine.strip()]
        if not normalized_engines:
            normalized_engines = ["ddg"]
        self.engines = normalized_engines
        use_ddg = "ddg" in normalized_engines
        # 其余 paid engine 兼容保留，但默认不启用
```

```python
class DeepSearchRequest(BaseModel):
    # ddg, bing, jina, sogou, serp, exa
    search_engines: List[str] = Field(default=[], description="使用哪些搜索引擎")
```

- [ ] **Step 4: Run tests to verify DDG selection passes**

Run: `cd reactor-tool && uv run python -m unittest tests.test_deepsearch_engine_selection -v`

Expected: PASS

- [ ] **Step 5: Commit DDG provider groundwork**

```bash
git add reactor-tool/pyproject.toml reactor-tool/reactor_tool/tool/search_component/search_engine.py reactor-tool/reactor_tool/tool/deepsearch.py reactor-tool/reactor_tool/model/protocal.py
git commit -m "feat: add duckduckgo deepsearch provider"
```

## Task 3: Replace Direct Parser with Jina-First Content Fetching

**Files:**
- Modify: `reactor-tool/reactor_tool/tool/search_component/search_engine.py`
- Test: `reactor-tool/tests/test_search_engine.py`

- [ ] **Step 1: Add the failing Jina-first parser hooks**

```python
class SearchBase(ABC):

    @staticmethod
    async def _fetch_content_with_jina_reader(source_url: str, timeout: int) -> str:
        raise NotImplementedError

    @staticmethod
    async def _fetch_content_with_direct_http(source_url: str, timeout: int) -> str:
        raise NotImplementedError
```

- [ ] **Step 2: Run the parser regression test**

Run: `cd reactor-tool && uv run python -m unittest tests.test_search_engine.SearchEngineIntegrationTest.test_should_use_jina_reader_content_when_available -v`

Expected: FAIL because parser does not yet call the new Jina-first hooks

- [ ] **Step 3: Implement Jina Reader primary fetcher plus HTTP fallback**

```python
class SearchBase(ABC):

    @staticmethod
    async def _fetch_content_with_jina_reader(source_url: str, timeout: int) -> str:
        if not source_url:
            return ""
        headers = {
            "Content-Type": "application/json",
            "X-Return-Format": "text",
            "X-Timeout": str(timeout),
        }
        jina_api_key = (os.getenv("JINA_API_KEY") or "").strip()
        if jina_api_key:
            headers["Authorization"] = f"Bearer {jina_api_key}"
        async with aiohttp.ClientSession() as session:
            async with session.post(
                "https://r.jina.ai/",
                json={"url": source_url},
                headers=headers,
                timeout=aiohttp.ClientTimeout(connect=5, total=timeout),
            ) as response:
                if response.status != 200:
                    return ""
                return (await response.text()).strip()

    @staticmethod
    async def _fetch_content_with_direct_http(source_url: str, timeout: int) -> str:
        client_timeout = aiohttp.ClientTimeout(connect=5, total=timeout)
        async with aiohttp.ClientSession() as session:
            async with session.get(source_url, timeout=client_timeout) as response:
                raw_text = await response.text()
        soup = BeautifulSoup(raw_text, "html.parser")
        return soup.get_text(" ", strip=True)

    @staticmethod
    async def parser(docs: List[Doc], timeout: int = 15, **kwargs) -> List[Doc]:
        async def _resolve_content(doc: Doc) -> str:
            jina_content = await SearchBase._fetch_content_with_jina_reader(doc.link, timeout)
            if jina_content and len(jina_content.strip()) > 50:
                return jina_content
            return await SearchBase._fetch_content_with_direct_http(doc.link, timeout)

        async with asyncio.TaskGroup() as tg:
            tasks = [tg.create_task(_resolve_content(doc)) for doc in docs]

        for doc, content in zip(docs, [task.result() for task in tasks]):
            if content:
                doc.content = content
        return docs
```

- [ ] **Step 4: Run the full search-engine regression suite**

Run: `cd reactor-tool && uv run python -m unittest tests.test_search_engine -v`

Expected: PASS

- [ ] **Step 5: Commit the Jina-first parser**

```bash
git add reactor-tool/reactor_tool/tool/search_component/search_engine.py reactor-tool/tests/test_search_engine.py
git commit -m "feat: prioritize jina reader for deepsearch page parsing"
```

## Task 4: Wire the Mixed Flow and Keep DeepSearch Behavior Stable

**Files:**
- Modify: `reactor-tool/reactor_tool/tool/search_component/search_engine.py`
- Modify: `reactor-tool/reactor_tool/tool/deepsearch.py`
- Test: `reactor-tool/tests/test_search_engine.py`
- Test: `reactor-tool/tests/test_deepsearch_engine_selection.py`

- [ ] **Step 1: Add the failing mixed-flow integration assertions**

```python
@patch.object(DDGSearch, "search_and_dedup", new_callable=AsyncMock)
async def test_mix_search_should_delegate_to_ddg_when_enabled(self, mock_ddg):
    mock_ddg.return_value = [
        Doc(doc_type="web_page", title="A", link="https://example.com/a", content="body")
    ]
    docs = await MixSearch().search(query="AI Agent", use_ddg=True, use_bing=False, use_jina=False, use_sogou=False, use_serp=False, use_exa=False)
    self.assertEqual(1, len(docs))
```

- [ ] **Step 2: Run the focused integration assertion**

Run: `cd reactor-tool && uv run python -m unittest tests.test_search_engine.SearchEngineIntegrationTest.test_mix_search_should_delegate_to_ddg_when_enabled -v`

Expected: FAIL because `MixSearch` does not yet know `DDGSearch`

- [ ] **Step 3: Implement DDG wiring in `MixSearch` and `DeepSearch`**

```python
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
        self,
        query: str,
        request_id: str = None,
        use_ddg: bool = True,
        use_bing: bool = False,
        use_jina: bool = False,
        use_sogou: bool = False,
        use_serp: bool = False,
        use_exa: bool = False,
        *args,
        **kwargs,
    ) -> List[Doc]:
        assert use_ddg or use_bing or use_jina or use_sogou or use_serp or use_exa
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
            tasks = [tg.create_task(engine.search_and_dedup(query=query, request_id=request_id)) for engine in engines]
        return [doc for docs in [task.result() for task in tasks] for doc in docs]
```

```python
self._search_single_query = partial(
    MixSearch().search_and_dedup,
    use_ddg=use_ddg,
    use_bing=use_bing,
    use_jina=use_jina,
    use_sogou=use_sogou,
    use_serp=use_serp,
    use_exa=use_exa,
)
```

- [ ] **Step 4: Run the targeted regression commands**

Run:
- `cd reactor-tool && uv run python -m unittest tests.test_search_engine -v`
- `cd reactor-tool && uv run python -m unittest tests.test_deepsearch_engine_selection -v`

Expected: PASS

- [ ] **Step 5: Commit the DeepSearch engine replacement**

```bash
git add reactor-tool/reactor_tool/tool/search_component/search_engine.py reactor-tool/reactor_tool/tool/deepsearch.py reactor-tool/tests/test_search_engine.py reactor-tool/tests/test_deepsearch_engine_selection.py
git commit -m "feat: replace deepsearch backend with ddg and jina"
```

## Task 5: Update Runtime Configuration and Operator Docs

**Files:**
- Modify: `reactor-tool/.env_template`
- Modify: `reactor-tool/README.md`

- [ ] **Step 1: Write the failing documentation delta**

```dotenv
# ========== DeepSearch 配置 ==========
USE_JD_SEARCH_GATEWAY=false
USE_SEARCH_ENGINE=ddg
DDG_REGION=wt-wt
DDG_SAFESEARCH=moderate
JINA_API_KEY=
JINA_READER_TIMEOUT=15
SEARCH_TIMEOUT=100000
SEARCH_THREAD_NUM=5
DEEPSEARCH_TOTAL_TIMEOUT_SECONDS=1200
```

```markdown
## DeepSearch 搜索配置

- 默认搜索提供方已切换为 `DuckDuckGo`
- 默认正文抓取链路为 `Jina Reader`
- 当 `Jina Reader` 返回空内容、超时或异常状态码时，会自动回退到 direct HTTP parser
- 如需排查抓取质量，请优先检查 `USE_SEARCH_ENGINE`、`JINA_API_KEY`、`JINA_READER_TIMEOUT`
```

- [ ] **Step 2: Run the focused regression suite**

Run: `cd reactor-tool && uv run python -m unittest tests.test_search_engine tests.test_deepsearch_engine_selection -v`

Expected: PASS; docs/env changes should not break runtime tests

- [ ] **Step 3: Apply the runtime config and docs update**

```dotenv
USE_SEARCH_ENGINE=ddg
DDG_REGION=wt-wt
DDG_SAFESEARCH=moderate
JINA_READER_TIMEOUT=15
```

```markdown
### DeepSearch 说明

- Query 分解与 `extend/search/report` 三阶段 SSE 协议不变
- 搜索链接来源改为 `DuckDuckGo`
- 页面正文优先通过 `Jina Reader` 抓取，失败时自动回退原始 HTTP 页面解析
- Java 侧 `deep_search` 调用、数据库持久化、前端回放展示均无需额外改造
```

- [ ] **Step 4: Run the final verification command**

Run: `cd reactor-tool && uv run python -m unittest tests.test_search_engine tests.test_deepsearch_engine_selection -v`

Expected: PASS

- [ ] **Step 5: Commit the configuration and docs**

```bash
git add reactor-tool/.env_template reactor-tool/README.md
git commit -m "docs: update deepsearch ddg and jina runtime config"
```

## Final Verification Checklist

- [ ] `DeepSearch.run()` 的 `extend/search/report` 输出格式未变化
- [ ] Java 侧 `DeepSearchTool` 无需新增字段或改请求体
- [ ] `DeepSearchStructuredResultBuilder` 无需改动，仍能消费搜索结果与最终答案
- [ ] DDG 搜索结果会被归一化为现有 `Doc(title, link, content)` 结构
- [ ] Jina Reader 失败时 direct HTTP fallback 生效
- [ ] `.env_template` 默认值已切到 `USE_SEARCH_ENGINE=ddg`
- [ ] README 已说明“DDG 搜索 + Jina Reader + direct HTTP fallback”运行方式

## Out of Scope

- `image_search` 与报告配图集成
- Java `deep_search` 工具参数扩展
- 深/浅搜索双模式
- 新数据库表或新 tool output schema

