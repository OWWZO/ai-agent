# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
import asyncio
import contextvars
import json
import math
import os
import threading
import time

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
    ImageGenerationRequest,
    MultimodalRAGRequest,
    EmbeddingProxyRequest,
    EmbeddingProxyResponse,
    WebFetchRequest,
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
    """统一错误响应结构，便于 Java 侧直连排障。"""
    return JSONResponse(status_code=status_code, content={"message": message})


def _normalize_vector(vector: list[float]) -> list[float]:
    """按 L2 范数归一化单条向量。"""
    if not vector:
        return vector
    norm = math.sqrt(sum(component * component for component in vector))
    if norm <= 0:
        return vector
    return [float(component / norm) for component in vector]


def _normalize_vector_batch(vectors: list[list[float]], normalize: bool) -> list[list[float]]:
    """根据请求参数决定是否执行批量归一化。"""
    if not normalize:
        return vectors
    return [_normalize_vector(vector) for vector in vectors]


@router.post("/code_interpreter")
async def post_code_interpreter(
    body: CIRequest,
):
    # 按需导入重型依赖，避免仅使用轻量路由时被 smolagents 等可选依赖阻塞。
    from reactor_tool.tool.code_interpreter import code_interpreter_agent

     # 处理文件路径
    if body.file_names:
        for idx, f_name in enumerate(body.file_names):
            if not f_name.startswith("/") and not f_name.startswith("http"):
                body.file_names[idx] = f"{os.getenv('FILE_SERVER_URL')}/preview/{body.request_id}/{f_name}"

    async def _stream():
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


@router.post("/report")
async def post_report(
    body: ReportRequest,
):
    from reactor_tool.tool.report import report

    # 处理文件路径
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


@router.post("/image_generation")
async def post_image_generation(body: ImageGenerationRequest):
    """图片生成端点，支持文生图与图生图两种模式。"""
    from reactor_tool.tool.image_generation import generate_images

    def _normalize_image_reference(reference: str) -> str:
        normalized = (reference or "").strip()
        if not normalized:
            return ""
        if normalized.startswith("/") or normalized.startswith("http") or normalized.startswith("data:"):
            return normalized

        file_server_url = (os.getenv("FILE_SERVER_URL") or "").rstrip("/")
        if not file_server_url:
            return normalized
        return f"{file_server_url}/preview/{body.request_id}/{normalized}"

    if body.file_names:
        body.file_names = [
            normalized
            for reference in body.file_names
            if (normalized := _normalize_image_reference(reference))
        ]
    if body.mask_file_names:
        body.mask_file_names = [
            _normalize_image_reference(reference) for reference in body.mask_file_names
        ]

    async def _run_generation():
        try:
            return await generate_images(body)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        except HTTPException:
            raise
        except Exception as exc:
            logger.exception("image_generation request failed")
            raise HTTPException(status_code=500, detail=str(exc)) from exc

    async def _stream():
        yield ServerSentEvent(
            data=json.dumps(
                {
                    "requestId": body.request_id,
                    "data": "开始执行图片生成任务...",
                    "isFinal": False,
                },
                ensure_ascii=False,
            )
        )
        try:
            result = await _run_generation()
        except HTTPException as exc:
            yield ServerSentEvent(
                data=json.dumps(
                    {
                        "requestId": body.request_id,
                        "data": exc.detail,
                        "isFinal": True,
                    },
                    ensure_ascii=False,
                )
            )
            yield ServerSentEvent(data="[DONE]")
            return

        yield ServerSentEvent(
            data=json.dumps(
                {
                    **result,
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

    result = await _run_generation()
    return result


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
    """
    text_2_sql
    """
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
    """统一输出与 Java 侧兼容的 OpenAI SSE 片段结构。"""
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


def build_mrag_agent(kb_id: str):
    """按需加载 MRAG 实现，避免在未安装检索依赖时阻塞服务启动。"""
    from reactor_tool.tool.mrag.query import AgenticRAG

    return AgenticRAG(kb_id=kb_id, n_round=3)


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
    kb_id = (body.kb_id or os.getenv("DEFAULT_KB_ID", "")).strip()
    if not kb_id:
        raise HTTPException(status_code=500, detail="DEFAULT_KB_ID is not configured")

    agent = build_mrag_agent(kb_id)

    def generator():
        has_payload = False
        try:
            for chunk in agent.run(body.question, body.image_urls):
                payload = _normalize_mrag_chunk(chunk)
                if not payload:
                    continue
                has_payload = True
                yield json.dumps(payload, ensure_ascii=False)
        except TimeoutError:
            logger.exception("mragQuery timeout")
            yield json.dumps(_build_mrag_chunk("MRAG 检索超时，请稍后重试。", "stop"), ensure_ascii=False)
        except Exception as e:
            logger.exception("mragQuery failed")
            yield json.dumps(_build_mrag_chunk(f"MRAG 检索失败：{e}", "stop"), ensure_ascii=False)
        else:
            if not has_payload:
                yield json.dumps(_build_mrag_chunk("MRAG 未返回有效内容。", "stop"), ensure_ascii=False)

        yield "[DONE]"

    return EventSourceResponse(
        generator(),
        ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
        ping=15,
    )


