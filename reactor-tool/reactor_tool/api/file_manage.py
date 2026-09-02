# -*- coding: utf-8 -*-
"""文件服务 HTTP API。

供 Java 主链路与前端调用：上传、列表、按 request 查文件、预览与下载。
注意：路径参数 file_id 当前实际传的是 request_id（历史兼容）。
"""

import mimetypes
import os
from urllib.parse import quote, unquote

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import JSONResponse, Response, FileResponse

from reactor_tool.model.protocal import (
    FileRequest,
    FileListRequest,
    FileUploadRequest,
    FileRegisterRequest,
    get_file_id,
    get_legacy_file_id,
)
from reactor_tool.util.middleware_util import RequestHandlerRoute
from reactor_tool.db.file_table_op import (
    FileInfoOp,
    get_file_preview_url,
    get_file_download_url,
    normalize_stored_file_name,
    normalize_stored_relative_path,
)


router = APIRouter(route_class=RequestHandlerRoute)


async def _get_file_info_by_request_and_name(request_id: str, raw_file_name: str):
    """优先命中相对路径 file_id，再回退 basename 与历史算法。"""
    relative_name = None
    try:
        relative_name = normalize_stored_relative_path(raw_file_name)
        file_info = await FileInfoOp.get_by_file_id(
            file_id=get_file_id(request_id, relative_name)
        )
        if file_info:
            return file_info, relative_name
    except ValueError:
        relative_name = None
    normalized_file_name = normalize_stored_file_name(raw_file_name)
    file_info = await FileInfoOp.get_by_file_id(
        file_id=get_file_id(request_id, normalized_file_name)
    )
    if file_info:
        return file_info, normalized_file_name
    legacy_file_id = get_legacy_file_id(request_id, raw_file_name)
    file_info = await FileInfoOp.get_by_file_id(file_id=legacy_file_id)
    return file_info, relative_name or normalized_file_name


@router.post("/get_file")
async def get_file(body: FileRequest):
    """按 file_id 查询单个文件的预览/下载地址。"""
    # 查询接口只返回稳定地址和元数据，不把文件内容再次放进 JSON 响应。
    file_info = await FileInfoOp.get_by_file_id(file_id=body.file_id)
    if file_info:
        preview_url = get_file_preview_url(
            file_id=file_info.request_id, file_name=file_info.filename
        )
        download_url = get_file_download_url(
            file_id=file_info.request_id, file_name=file_info.filename
        )
        return JSONResponse(
            content={
                "ossUrl": download_url,
                "downloadUrl": download_url,
                "domainUrl": preview_url,
                "requestId": body.request_id,
                "fileName": body.file_name,
            }
        )
    else:
        raise Exception("file not found")


@router.post("/upload_file")
async def upload_file(body: FileUploadRequest):
    """JSON 方式上传文本内容并登记元数据。"""
    # 存储名必须先归一化，再参与 file_id 计算，保证同名文件在不同入口生成一致主键。
    body.file_name = normalize_stored_file_name(body.file_name)
    body.request_id = body.request_id
    file_info = await FileInfoOp.add_by_content(
        filename=body.file_name,
        content=body.content,
        file_id=get_file_id(body.request_id, body.file_name),
        description=body.description,
        request_id=body.request_id,
    )
    preview_url = get_file_preview_url(
        file_id=file_info.request_id, file_name=file_info.filename
    )
    download_url = get_file_download_url(
        file_id=file_info.request_id, file_name=file_info.filename
    )
    return JSONResponse(
        content={
            "ossUrl": download_url,
            "downloadUrl": download_url,
            "domainUrl": preview_url,
            "fileSize": file_info.file_size,
        }
    )


@router.post("/upload_file_data")
async def upload_file_data(
    file: UploadFile = File(...), request_id: str = Form(alias="requestId")
):
    """multipart 二进制上传（工具产物、用户附件等）。"""
    # URL 解码后再清洗文件名，避免客户端编码差异导致落盘名和 file_id 不一致。
    file.filename = unquote(file.filename)
    file.filename = normalize_stored_file_name(file.filename)
    file_id = get_file_id(request_id, file.filename)
    file_info = await FileInfoOp.add_by_file(
        file=file, file_id=file_id, request_id=request_id
    )
    preview_url = get_file_preview_url(
        file_id=file_info.request_id, file_name=file_info.filename
    )
    download_url = get_file_download_url(
        file_id=file_info.request_id, file_name=file_info.filename
    )
    return JSONResponse(
        content={
            "downloadUrl": download_url,
            "domainUrl": preview_url,
            "fileSize": file_info.file_size,
        }
    )


@router.post("/register_file")
async def register_file(body: FileRegisterRequest):
    """登记本地已有文件：不拷贝内容，只写元数据并返回预览/下载 URL。"""
    # register 只建立元数据指针，真实内容仍由 local_path 指向的文件提供。
    body.file_name = normalize_stored_relative_path(body.file_name)
    file_id = get_file_id(body.request_id, body.file_name)
    file_info = await FileInfoOp.add_by_existing_path(
        filename=body.file_name,
        local_path=body.local_path,
        file_id=file_id,
        description=body.description or "",
        request_id=body.request_id,
    )
    preview_url = get_file_preview_url(
        file_id=file_info.request_id, file_name=file_info.filename
    )
    download_url = get_file_download_url(
        file_id=file_info.request_id, file_name=file_info.filename
    )
    return JSONResponse(
        content={
            "ossUrl": download_url,
            "downloadUrl": download_url,
            "domainUrl": preview_url,
            "fileSize": file_info.file_size,
            "fileName": file_info.filename,
            "requestId": body.request_id,
        }
    )


@router.post("/get_file_list")
async def get_file_list(body: FileListRequest):
    """列出会话下全部文件，或按 filters 中的 file_id 过滤。"""
    # 没有 filters 时按会话聚合；有 filters 时按精确 file_id 查询，分别对应历史两种调用方式。
    if not body.filters:
        file_infos = await FileInfoOp.get_by_request_id(body.request_id)
    else:
        file_infos = await FileInfoOp.get_by_file_ids(
            file_ids=[f.file_id for f in body.filters]
        )
    if not file_infos:
        return JSONResponse(content={"results": [], "totalSize": 0})
    total_size = sum([f.file_size for f in file_infos])
    results = []
    for file_info in file_infos:
        preview_url = get_file_preview_url(
            file_id=file_info.request_id, file_name=file_info.filename
        )
        download_url = get_file_download_url(
            file_id=file_info.request_id, file_name=file_info.filename
        )
        results.append(
            {
                "ossUrl": download_url,
                "downloadUrl": download_url,
                "domainUrl": preview_url,
                "requestId": file_info.request_id,
                "fileName": file_info.filename,
            }
        )
    return JSONResponse(content={"results": results, "totalSize": total_size})


@router.get("/download/{file_id}/{file_name:path}")
async def download_file(file_id: str, file_name: str):
    """下载文件。TODO：路径 file_id 实际是 request_id，后续统一改名。"""
    # file_id 参数在协议上仍承载 request_id，查询函数负责兼容新旧文件名算法。
    file_info, file_name = await _get_file_info_by_request_and_name(file_id, file_name)
    if not file_info or not os.path.exists(file_info.file_path):
        return Response(content="File not found", status_code=404)
    return FileResponse(file_info.file_path, filename=os.path.basename(file_name))


@router.get("/preview/{file_id}/{file_name:path}")
async def preview_file(file_id: str, file_name: str):
    """浏览器内联预览；md 强制 text/markdown，未知类型走 attachment。"""
    # TODO 目前 file_id 实际上是 request_id，后续统一修改
    file_info, file_name = await _get_file_info_by_request_and_name(file_id, file_name)
    if not file_info or not os.path.exists(file_info.file_path):
        return Response(content="File not found", status_code=404)

    # 可识别类型以内联方式交给浏览器；未知类型改为附件，避免浏览器误把任意二进制当页面执行。
    disposition = "inline"
    if file_name.endswith(".md"):
        content_type = "text/markdown"
    else:
        content_type, _ = mimetypes.guess_type(file_name)
    if not content_type:
        content_type = "application/octet-stream"
        disposition = "attachment"

    encoded_file_name = quote(file_name)

    return FileResponse(
        file_info.file_path,
        filename=os.path.basename(file_name),
        media_type=content_type,
        headers={
            "Content-Disposition": f"{disposition}; filename=\"{encoded_file_name}\"; filename*=UTF-8''{encoded_file_name}",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
    )
