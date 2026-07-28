# -*- coding: utf-8 -*-
"""Tool API routes for Java HTTP/SSE calls.

Endpoints:
  /code_interpreter  /report  /deepsearch  /web_fetch
  /embedding/text    /table_rag  /cal_engine  /auto_analysis  /nl2sql
  /sopRecall         /script_runner  /mragQuery
  /document_generate /slides_generate /excel_generator /checklist_generate /template_filler
"""
import asyncio
import contextvars
import json
import math
import os
import threading
import time
import uuid
from datetime import datetime

from dotenv import load_dotenv
from fastapi import APIRouter, HTTPException
from fastapi.responses import JSONResponse
from jinja2 import Template
from loguru import logger
from sse_starlette import ServerSentEvent, EventSourceResponse

from reactor_tool.model.code import ActionOutput, CodeOuput
from reactor_tool.model.protocal import (
    TableRAGRequest,
    AutoAnalysisRequest,
    CIRequest,
    CalEngineRequest,
    ReportRequest,
    DeepSearchRequest,
    NL2SQLRequest,
    SopChooseRequest,
    ScriptRunnerRequest,
    MultimodalRAGRequest,
    EmbeddingProxyRequest,
    EmbeddingProxyResponse,
    WebFetchRequest,
    DocgenRequest,
    CodeExecutionRequest,
)
from reactor_tool.tool.mrag.storage.models.mrag_session_model import MRagSessionModel
from reactor_tool.tool.mrag.storage.models.mrag_turn_model import MRagTurnModel
from reactor_tool.tool.mrag.storage.store_factory import (
    get_mrag_session_store,
    get_mrag_turn_store,
)
from reactor_tool.tool.web_fetcher import WebFetcher
from reactor_tool.tool.code_interpreter_policy import CodeExecutionPermissionError
from reactor_tool.util.file_util import upload_file
from reactor_tool.util.report_file_util import sanitize_report_html_content
from reactor_tool.util.prompt_util import get_prompt
from reactor_tool.util.middleware_util import RequestHandlerRoute
load_dotenv()


router = APIRouter(route_class=RequestHandlerRoute)


def _error_response(status_code: int, message: str) -> JSONResponse:
    """Unified error response for Java clients."""
    return JSONResponse(status_code=status_code, content={"message": message})


def _normalize_vector(vector: list[float]) -> list[float]:
    """L2-normalize one vector."""
    if not vector:
        return vector
    norm = math.sqrt(sum(component * component for component in vector))
    if norm <= 0:
        return vector
    return [float(component / norm) for component in vector]


def _normalize_vector_batch(vectors: list[list[float]], normalize: bool) -> list[list[float]]:
    """Optionally batch L2-normalize vectors."""
    if not normalize:
        return vectors
    return [_normalize_vector(vector) for vector in vectors]


@router.post("/code_interpreter")
async def post_code_interpreter(
    body: CIRequest,
):
    """代码解释器：SSE 流式返回代码/动作/最终结果。"""
    # 按需导入重型依赖，避免仅使用轻量路由时被 smolagents 等可选依赖阻塞。
    from reactor_tool.tool.code_interpreter import code_interpreter_agent

    # 相对文件名补全为文件服务预览 URL
    if body.file_names:
        for idx, f_name in enumerate(body.file_names):
            if not f_name.startswith("/") and not f_name.startswith("http"):
                body.file_names[idx] = f"{os.getenv('FILE_SERVER_URL')}/preview/{body.request_id}/{f_name}"

    async def _stream():
        """将 Agent 产出映射为 SSE 事件（CodeOuput / ActionOutput / 文本）。"""
        acc_content = ""
        acc_token = 0
        acc_time = time.time()
        try:
            async for chunk in code_interpreter_agent(
                task=body.task,
                file_names=body.file_names,
                request_id=body.request_id,
                stream=True,
                permission_profile=body.permission_profile,
            ):

                if isinstance(chunk, CodeOuput):
                    # 代码块 + 产物文件
                    yield ServerSentEvent(
                        data=json.dumps(
                            {
                                "requestId": body.request_id,
                                "code": chunk.code,
                                "fileInfo": chunk.file_list,
                                "isFinal": False,
                            },
                            ensure_ascii=False,
                        )
                    )
                elif isinstance(chunk, ActionOutput):
                    # 动作 observation
                    yield ServerSentEvent(
                        data=json.dumps(
                            {
                                "requestId": body.request_id,
                                "codeOutput": chunk.content,
                                "fileInfo": chunk.file_list,
                                "isFinal": True,
                            },
                            ensure_ascii=False,
                        )
                    )
                    yield ServerSentEvent(data="[DONE]")
                elif isinstance(chunk, str):
                    acc_content += chunk
                    acc_token += 1
                    if body.stream_mode.mode == "general":
                        yield ServerSentEvent(
                            data=json.dumps(
                                {"requestId": body.request_id, "data": chunk, "isFinal": False},
                                ensure_ascii=False,
                            )
                        )
                    elif body.stream_mode.mode == "token":
                        if acc_token >= body.stream_mode.token:
                            yield ServerSentEvent(
                                data=json.dumps(
                                    {
                                        "requestId": body.request_id,
                                        "data": acc_content,
                                        "isFinal": False,
                                    },
                                    ensure_ascii=False,
                                )
                            )
                            acc_token = 0
                            acc_content = ""
                    elif body.stream_mode.mode == "time":
                        if time.time() - acc_time > body.stream_mode.time:
                            yield ServerSentEvent(
                                data=json.dumps(
                                    {
                                        "requestId": body.request_id,
                                        "data": acc_content,
                                        "isFinal": False,
                                    },
                                    ensure_ascii=False,
                                )
                            )
                            acc_time = time.time()
                            acc_content = ""
                    if body.stream_mode.mode in ["time", "token"] and acc_content:
                        yield ServerSentEvent(
                            data=json.dumps(
                                {
                                    "requestId": body.request_id,
                                    "data": acc_content,
                                    "isFinal": False,
                                },
                                ensure_ascii=False,
                            )
                        )
        except CodeExecutionPermissionError as exc:
            yield ServerSentEvent(
                data=json.dumps(
                    {
                        "requestId": body.request_id,
                        "data": exc.to_public_payload(),
                        "isFinal": True,
                    },
                    ensure_ascii=False,
                )
            )
            yield ServerSentEvent(data="[DONE]")


    if body.stream:
        return EventSourceResponse(
            _stream(),
            ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
            ping=15,
        )
    else:
        content = ""
        try:
            async for chunk in code_interpreter_agent(
                task=body.task,
                file_names=body.file_names,
                request_id=body.request_id,
                stream=body.stream,
                permission_profile=body.permission_profile,
            ):
                # stream=False yields a single RunResult from smolagents
                if hasattr(chunk, "output"):
                    content = str(chunk.output) if chunk.output is not None else ""
                    break
                if isinstance(chunk, str):
                    content += chunk
        except CodeExecutionPermissionError as exc:
            return JSONResponse(
                status_code=400,
                content={
                    "code": 400,
                    "data": exc.to_public_payload(),
                    "requestId": body.request_id,
                },
            )
        if not content:
            content = ""
        out_file_name = body.file_name or "code_output"
        out_file_type = getattr(body, "file_type", None) or "md"
        if out_file_type == "ppt":
            out_file_type = "html"
        file_info = [
            await upload_file(
                content=content,
                file_name=out_file_name,
                request_id=body.request_id,
                file_type=out_file_type,
            )
        ]
        return {
            "code": 200,
            "data": content,
            "fileInfo": file_info,
            "requestId": body.request_id,
        }


@router.post("/code_execution")
async def post_code_execution(body: CodeExecutionRequest):
    """Run caller-supplied Python and return stdout, errors, and fileInfo."""
    from reactor_tool.tool.direct_code_execution import execute_code

    return await execute_code(body)


@router.post("/report")
async def post_report(
    body: ReportRequest,
):
    """报告生成：流式输出正文，结束后落盘 html/md/ppt 产物。"""
    from reactor_tool.tool.report import report

    # 相对文件名补全为文件服务预览 URL
    if body.file_names:
        for idx, f_name in enumerate(body.file_names):
            if not f_name.startswith("/") and not f_name.startswith("http"):
                body.file_names[idx] = f"{os.getenv('FILE_SERVER_URL')}/preview/{body.request_id}/{f_name}"

    async def _stream():
        content = ""
        acc_content = ""
        acc_token = 0
        acc_time = time.time()
        async for chunk in report(
            task=body.task,
            file_names=body.file_names,
            file_type=body.file_type,
            template_type=body.template_type,
        ):
            content += chunk
            acc_content += chunk
            acc_token += 1
            if body.stream_mode.mode == "general":
                yield ServerSentEvent(
                    data=json.dumps(
                        {"requestId": body.request_id, "data": chunk, "isFinal": False},
                        ensure_ascii=False,
                    )
                )
            elif body.stream_mode.mode == "token":
                if acc_token >= body.stream_mode.token:
                    yield ServerSentEvent(
                        data=json.dumps(
                            {
                                "requestId": body.request_id,
                                "data": acc_content,
                                "isFinal": False,
                            },
                            ensure_ascii=False,
                        )
                    )
                    acc_token = 0
                    acc_content = ""
            elif body.stream_mode.mode == "time":
                if time.time() - acc_time > body.stream_mode.time:
                    yield ServerSentEvent(
                        data=json.dumps(
                            {
                                "requestId": body.request_id,
                                "data": acc_content,
                                "isFinal": False,
                            },
                            ensure_ascii=False,
                        )
                    )
                    acc_time = time.time()
                    acc_content = ""
        if body.stream_mode.mode in ["time", "token"] and acc_content:
            yield ServerSentEvent(
                data=json.dumps({"requestId": body.request_id, "data": acc_content, "isFinal": False},
                                ensure_ascii=False))
        if body.file_type in ["ppt", "html"]:
            content = sanitize_report_html_content(content)
        file_info = [await upload_file(content=content, file_name=body.file_name, request_id=body.request_id,
                                 file_type="html" if body.file_type == "ppt" else body.file_type)]
        yield ServerSentEvent(data=json.dumps(
            {"requestId": body.request_id, "data": content, "fileInfo": file_info,
             "isFinal": True}, ensure_ascii=False))
        yield ServerSentEvent(data="[DONE]")

    if body.stream:
        return EventSourceResponse(
            _stream(),
            ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
            ping=15,
        )
    else:
        content = ""
        async for chunk in report(
            task=body.task,
            file_names=body.file_names,
            file_type=body.file_type,
            template_type=body.template_type,
        ):
            content += chunk
        if body.file_type in ["ppt", "html"]:
            content = sanitize_report_html_content(content)
        file_info = [await upload_file(content=content, file_name=body.file_name, request_id=body.request_id,
                                 file_type="html" if body.file_type == "ppt" else body.file_type)]
        return {"code": 200, "data": content, "fileInfo": file_info, "requestId": body.request_id}


@router.post("/deepsearch")
async def post_deepsearch(
    body: DeepSearchRequest,
):
    """深度搜索端点"""
    from reactor_tool.tool.deepsearch import DeepSearch

    deepsearch = DeepSearch(engines=body.search_engines)
    async def _stream():
        async for chunk in deepsearch.run(
                query=body.query,
                request_id=body.request_id,
                max_loop=body.max_loop,
                stream=True,
                stream_mode=body.stream_mode,
        ):
            yield ServerSentEvent(data=chunk)
        yield ServerSentEvent(data="[DONE]")

    return EventSourceResponse(_stream(), ping_message_factory=lambda: ServerSentEvent(data="heartbeat"), ping=15)


@router.post("/web_fetch")
async def post_web_fetch(body: WebFetchRequest):
    """单网页抓取端点，始终把完整正文沉淀为文件产物。"""
    try:
        result = await WebFetcher().fetch(body)
        file_info = [
            await upload_file(
                content=result.full_content,
                file_name=result.file_name,
                request_id=body.request_id,
                file_type="markdown",
            )
        ]
        return {
            "code": 200,
            "data": result.to_response_data(),
            "fileInfo": file_info,
            "requestId": body.request_id,
        }
    except ValueError as exc:
        logger.warning("web_fetch request failed: {}", exc)
        return JSONResponse(
            status_code=400,
            content={
                "code": 400,
                "message": str(exc),
                "requestId": body.request_id,
            },
        )
    except Exception as exc:
        logger.exception("web_fetch request failed unexpectedly")
        return JSONResponse(
            status_code=502,
            content={
                "code": 502,
                "message": str(exc),
                "requestId": body.request_id,
            },
        )


@router.post("/embedding/text")
async def post_text_embedding(body: EmbeddingProxyRequest):
    """共享文本向量代理端点。"""
    from reactor_tool.tool.mrag.embedding.text_embedding import get_text_embedding_model

    try:
        embedding_model = get_text_embedding_model()
        vectors = embedding_model.encode_text_batch(body.inputs)
        normalized_vectors = _normalize_vector_batch(vectors, body.normalize)
        dimension = len(normalized_vectors[0]) if normalized_vectors else None
        response = EmbeddingProxyResponse(
            vectors=normalized_vectors,
            dimension=dimension,
            model=os.getenv("TEXT_EMBEDDING_MODEL_NAME"),
        )
        return response.model_dump()
    except TimeoutError:
        logger.exception("embedding/text timeout")
        return _error_response(504, "共享文本向量服务调用超时")
    except Exception as exc:
        logger.exception("embedding/text failed")
        return _error_response(502, f"共享文本向量服务调用失败: {exc}")


@router.post("/table_rag")
async def post_table_rag(
    body: TableRAGRequest,
):
    """表结构/列值 RAG：only_recall 仅粗排，否则完整精排选 schema。"""
    from reactor_tool.tool.table_rag import TableRAGAgent

    request_id = body.request_id
    query = body.query
    modelCodeList = body.model_code_list
    current_date_info = body.current_date_info
    schema_info = body.schema_info
    recall_type = body.recall_type
    use_vector = body.use_vector
    use_elastic = body.use_elastic

    table_rag = TableRAGAgent(request_id=request_id,
                              query=query,
                              modelCodeList=modelCodeList,
                              current_date_info=current_date_info,
                              schema_info=schema_info,
                              user_info="",
                              use_vector=use_vector,
                              use_elastic=use_elastic,)

    # only_recall：只做向量/ES 粗排；否则走完整选表链路
    if recall_type == "only_recall":
        result = await table_rag.run_recall(query=query)
    else:
        result = await table_rag.run(query=query)
    content = result.get("choosed_schema", {})
    return {"code": 200, "data": content, "requestId": body.request_id}


@router.post("/cal_engine")
async def cal_engine(body: CalEngineRequest):
    """根据用户获取数据和用户 query 生成指标计算公式"""
    from reactor_tool.util.llm_util import ask_llm

    prompt = Template(get_prompt("analysis")["cal_engine_prompt"]).render(
        query=body.query,
        data=body.data,
    )

    async for chunk in ask_llm(messages=prompt, model=os.getenv("CAL_ENGINE_MODEL", "qwen-vl-max"), only_content=True):
        expression = chunk
    return {"code": 200, "expression": expression, "request_id": body.request_id, "query": body.query}


@router.post("/auto_analysis")
async def auto_analysis(body: AutoAnalysisRequest):
    """自动多步数据分析；stream 时在独立线程跑 Agent，经 Queue 推 SSE。"""
    from reactor_tool.tool.auto_analysis import AutoAnalysisAgent

    if body.stream:
        queue = asyncio.Queue()
        async def _stream(queue):
            if not body.modelCodeList:
                yield ServerSentEvent(data="没有提供数据源，无法进行数据分析")
            else:
                while True:
                    data = await queue.get()
                    if data == "[DONE]":
                        yield ServerSentEvent(data=data)
                        break
                    if not isinstance(data, str):
                        data = json.dumps(data, ensure_ascii=False)
                    yield ServerSentEvent(data=data)

        def run_task(context, queue, body):
            if body.modelCodeList:
                context.run(lambda : asyncio.run(AutoAnalysisAgent(queue=queue, max_steps=body.max_steps, stream=body.stream).run(**body.model_dump())))

        thread = threading.Thread(target=run_task, args=(contextvars.copy_context(), queue, body), daemon=True)
        thread.start()
        return EventSourceResponse(
            _stream(queue),
            ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
            ping=15,
        )
    else:
        response = {"code": 200, "data": {}, "request_id": body.request_id}
        if not body.modelCodeList:
            response["data"] = "没有提供数据源，无法进行数据分析"
        else:
            response["data"] = await AutoAnalysisAgent(max_steps=body.max_steps).run(**body.model_dump())
        return response


@router.post("/nl2sql")
async def post_nl2sql(body: NL2SQLRequest):
    """自然语言转 SQL（NL2SQL），支持 SSE 流式与一次性返回。"""
    from reactor_tool.tool.nl2sql import NL2SQLAgent

    nl2sql_queue = asyncio.Queue()
    if body.stream:
        async def _stream(queue):
            if not body.query:
                yield ServerSentEvent(data="没有提供用户问题，无法进行nl2sql的执行")
            else:
                while True:
                    data = await queue.get()
                    if data == "[DONE]":
                        yield ServerSentEvent(data=data)
                        break
                    if not isinstance(data, str):
                        data = json.dumps(data, ensure_ascii=False)
                    yield ServerSentEvent(data=data)

        def run_task(context, queue, body:NL2SQLRequest):
            if body.query:
                context.run(lambda : asyncio.run(NL2SQLAgent(queue=queue).run(body)))

        thread = threading.Thread(target=run_task, args=(contextvars.copy_context(), nl2sql_queue, body), daemon=True)
        thread.start()
        return EventSourceResponse(
            _stream(nl2sql_queue),
            ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
            ping=15,
        )
    else:
        response = {"code": 200, "data": {}, "request_id": body.request_id, "status": "data"}
        if not body.query:
            response["err_msg"] = "没有提供用户问题，无法进行nl2sql的执行"
        else:
            response = await NL2SQLAgent().run(body)
        return response


@router.post("/sopRecall")
async def post_sop_recall(
    body: SopChooseRequest,
):
    """从候选 SOP 列表中按 query 语义择优，供 Plan 规划参考。"""
    from reactor_tool.tool.plan_sop import PlanSOP

    request_id = body.request_id
    query = body.query
    sop_list = body.sop_list
    pl_sop = PlanSOP(request_id)
    sop_mode, choosed_sop_string = pl_sop.sop_choose(query=query, sop_list=sop_list)

    return {"code": 200, "data": {"sop_mode": sop_mode, "choosed_sop_string": choosed_sop_string}, "requestId": body.request_id}


@router.post("/script_runner")
async def post_script_runner(body: ScriptRunnerRequest):
    """skill 脚本执行端点"""
    from reactor_tool.tool.script_runner import run_script_request

    response = await run_script_request(body)
    return response.model_dump(by_alias=True)


def _build_mrag_chunk(content: str, finish_reason: str | None = None) -> dict:
    """Unified error response for Java clients."""
    return {
        "id": "chatcmpl-mrag",
        "choices": [
            {
                "delta": {
                    "content": content,
                },
                "finishReason": finish_reason,
                "index": 0,
            }
        ],
        "created": int(time.time()),
        "model": "mrag-agent",
        "object": "chat.completion.chunk",
    }


def build_mrag_agent(kb_scope: str | list[str]):
    """按需加载 MRAG 实现，避免在未安装检索依赖时阻塞服务启动。"""
    from reactor_tool.tool.mrag.query import AgenticRAG

    return AgenticRAG(kb_id=kb_scope, n_round=3)


def _normalize_kb_scope_list(kb_scope: str | list[str]) -> list[str]:
    """将单库/多库参数统一为非空 list。"""
    if isinstance(kb_scope, list):
        return [item for item in kb_scope if item]
    return [kb_scope] if kb_scope else []


def _build_session_title(question: str) -> str:
    """会话标题：问题截断到 24 字。"""
    normalized = question.strip()
    if len(normalized) <= 24:
        return normalized
    return f"{normalized[:24]}..."


def _build_answer_preview(answer: str) -> str:
    """答案预览：压空白后截断到 120 字。"""
    preview = " ".join(answer.strip().split())
    if len(preview) <= 120:
        return preview
    return f"{preview[:120]}..."


def _ensure_mrag_session(session_id: str, question: str, kb_scope: str | list[str]) -> MRagSessionModel:
    """确保 MRAG 会话存在；不存在则按首轮问题创建。"""
    session_store = get_mrag_session_store()
    session = session_store.get_session(session_id)
    if session:
        return session

    normalized_scope = _normalize_kb_scope_list(kb_scope)
    now = datetime.now()
    session = MRagSessionModel(
        session_id=session_id,
        title=_build_session_title(question) or "新对话",
        kb_scope=normalized_scope,
        cover_kb_id=normalized_scope[0] if normalized_scope else None,
        latest_question=question,
        latest_answer_preview="",
        turn_count=0,
        status="RUNNING",
        create_time=now,
        modify_time=now,
    )
    session_store.create_session(session)
    return session


def _normalize_mrag_chunk(chunk) -> dict | None:
    """兼容 OpenAI SDK chunk、字典和纯文本三种返回形态。"""
    if chunk is None:
        return None

    if isinstance(chunk, str):
        return _build_mrag_chunk(chunk)

    if isinstance(chunk, dict):
        choices = chunk.get("choices") or []
        if not choices:
            return chunk
        choice = choices[0] or {}
        delta = choice.get("delta") or {}
        return _build_mrag_chunk(
            delta.get("content", ""),
            choice.get("finishReason") or choice.get("finish_reason"),
        )

    choices = getattr(chunk, "choices", None)
    if choices:
        choice = choices[0]
        delta = getattr(choice, "delta", None)
        return _build_mrag_chunk(
            getattr(delta, "content", "") or "",
            getattr(choice, "finish_reason", None) or getattr(choice, "finishReason", None),
        )

    model_dump = getattr(chunk, "model_dump", None)
    if callable(model_dump):
        return _normalize_mrag_chunk(model_dump())

    return _build_mrag_chunk(str(chunk))


@router.post("/mragQuery")
async def post_mrag_query(body: MultimodalRAGRequest):
    """MRAG 多模态知识检索端点。"""
    kb_scope = body.resolve_kb_scope(os.getenv("DEFAULT_KB_ID", ""))
    if not kb_scope:
        raise HTTPException(status_code=500, detail="DEFAULT_KB_ID is not configured")

    agent = build_mrag_agent(kb_scope)
    session_id = body.session_id.strip()
    turn_store = get_mrag_turn_store() if session_id else None
    turn = None
    if session_id:
        _ensure_mrag_session(session_id, body.question, kb_scope)
        turn = MRagTurnModel(
            turn_id=f"mrag_turn_{uuid.uuid4().hex}",
            session_id=session_id,
            question=body.question,
            status="RUNNING",
            request_kb_scope=_normalize_kb_scope_list(kb_scope),
            request_image_urls=list(body.image_urls),
            create_time=datetime.now(),
            modify_time=datetime.now(),
        )
        turn_store.create_turn(turn)

    def generator():
        has_payload = False
        answer_parts = []
        raw_chunks = []
        final_status = "SUCCESS"
        error_message = ""
        try:
            for chunk in agent.run(body.question, body.image_urls):
                payload = _normalize_mrag_chunk(chunk)
                if not payload:
                    continue
                has_payload = True
                raw_chunks.append(payload)
                delta = ((payload.get("choices") or [{}])[0].get("delta") or {}).get("content", "")
                if delta:
                    answer_parts.append(delta)
                yield json.dumps(payload, ensure_ascii=False)
        except TimeoutError:
            logger.exception("mragQuery timeout")
            final_status = "FAILED"
            error_message = "MRAG 检索超时，请稍后重试。"
            yield json.dumps(_build_mrag_chunk(error_message, "stop"), ensure_ascii=False)
        except Exception as e:
            logger.exception("mragQuery failed")
            final_status = "FAILED"
            error_message = f"MRAG 检索失败：{e}"
            yield json.dumps(_build_mrag_chunk(error_message, "stop"), ensure_ascii=False)
        else:
            if not has_payload:
                final_status = "FAILED"
                error_message = "MRAG 未返回有效内容。"
                yield json.dumps(_build_mrag_chunk(error_message, "stop"), ensure_ascii=False)
        finally:
            if turn and turn_store:
                answer_markdown = "".join(answer_parts)
                turn.answer_markdown = answer_markdown
                turn.status = final_status
                turn.error_message = error_message
                turn.raw_chunks = raw_chunks
                turn.modify_time = datetime.now()
                turn_store.update_turn(turn)

                session_store = get_mrag_session_store()
                session = session_store.get_session(session_id)
                if session:
                    normalized_scope = _normalize_kb_scope_list(kb_scope)
                    if session.turn_count == 0:
                        session.title = _build_session_title(body.question) or session.title
                    session.kb_scope = normalized_scope
                    session.cover_kb_id = normalized_scope[0] if normalized_scope else None
                    session.latest_question = body.question
                    session.latest_answer_preview = _build_answer_preview(answer_markdown or error_message)
                    session.turn_count = len(turn_store.list_turns(session_id))
                    session.status = final_status
                    session.modify_time = datetime.now()
                    session_store.update_session(session)

        yield "[DONE]"

    return EventSourceResponse(
        generator(),
        ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
        ping=15,
    )


def _docgen_params(body: DocgenRequest) -> dict:
    data = body.model_dump(by_alias=False, exclude_none=False)
    request_id = data.pop("request_id", None) or getattr(body, "request_id", None)
    # drop response-only noise if any
    data.pop("extra", None)
    return request_id, data


@router.post("/document_generate")
async def post_document_generate(body: DocgenRequest):
    """LeAgent-aligned document_generate (PDF/DOCX/HTML/Markdown)."""
    from reactor_tool.tool.docgen.service import run_document_generate

    try:
        request_id, params = _docgen_params(body)
        result = await run_document_generate(request_id, params)
        return JSONResponse(content={"requestId": request_id, **_camel_file_payload(result)})
    except Exception as e:
        logger.exception(f"document_generate failed: {e}")
        return _error_response(400, str(e))


@router.post("/slides_generate")
async def post_slides_generate(body: DocgenRequest):
    """LeAgent-aligned slides_generate (PPTX)."""
    from reactor_tool.tool.docgen.service import run_slides_generate

    try:
        request_id, params = _docgen_params(body)
        result = await run_slides_generate(request_id, params)
        return JSONResponse(content={"requestId": request_id, **_camel_file_payload(result)})
    except Exception as e:
        logger.exception(f"slides_generate failed: {e}")
        return _error_response(400, str(e))


@router.post("/excel_generator")
async def post_excel_generator(body: DocgenRequest):
    """LeAgent-aligned excel_generator."""
    from reactor_tool.tool.docgen.service import run_excel_generator

    try:
        request_id, params = _docgen_params(body)
        result = await run_excel_generator(request_id, params)
        return JSONResponse(content={"requestId": request_id, **_camel_file_payload(result)})
    except Exception as e:
        logger.exception(f"excel_generator failed: {e}")
        return _error_response(400, str(e))


@router.post("/checklist_generate")
async def post_checklist_generate(body: DocgenRequest):
    """LeAgent-aligned checklist_generate."""
    from reactor_tool.tool.docgen.service import run_checklist_generate

    try:
        request_id, params = _docgen_params(body)
        result = await run_checklist_generate(request_id, params)
        return JSONResponse(content={"requestId": request_id, **_camel_file_payload(result)})
    except Exception as e:
        logger.exception(f"checklist_generate failed: {e}")
        return _error_response(400, str(e))


@router.post("/template_filler")
async def post_template_filler(body: DocgenRequest):
    """LeAgent-aligned template_filler (Jinja2)."""
    from reactor_tool.tool.docgen.service import run_template_filler

    try:
        request_id, params = _docgen_params(body)
        result = await run_template_filler(request_id, params)
        return JSONResponse(content={"requestId": request_id, **_camel_file_payload(result)})
    except Exception as e:
        logger.exception(f"template_filler failed: {e}")
        return _error_response(400, str(e))


def _camel_file_payload(result: dict) -> dict:
    """Normalize service payload keys for Java clients."""
    file_info = result.get("fileInfo") or result.get("file_info") or []
    # ensure camelCase keys inside fileInfo
    norm_files = []
    for f in file_info:
        if not isinstance(f, dict):
            continue
        norm_files.append({
            "fileName": f.get("fileName") or f.get("file_name"),
            "ossUrl": f.get("ossUrl") or f.get("oss_url"),
            "domainUrl": f.get("domainUrl") or f.get("domain_url"),
            "downloadUrl": f.get("downloadUrl") or f.get("download_url"),
            "fileSize": f.get("fileSize") or f.get("file_size") or 0,
        })
    out = {
        "success": bool(result.get("success", True)),
        "message": result.get("message") or "ok",
        "fileInfo": norm_files,
        "outputPath": result.get("outputPath") or result.get("output_path"),
        "stats": result.get("stats") or {},
        "warnings": result.get("warnings") or [],
    }
    if result.get("rendered") is not None:
        out["rendered"] = result.get("rendered")
    for k in ("format", "sheet_names", "file_size_bytes", "rendered_length", "variables_used", "output_format"):
        if k in result:
            out[k] = result[k]
    return out
