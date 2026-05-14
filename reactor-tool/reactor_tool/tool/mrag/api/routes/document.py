import os
import tempfile
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional, List

from fastapi import APIRouter, UploadFile, File, HTTPException, BackgroundTasks
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from reactor_tool.db.file_table_op import FileInfoOp, get_file_download_url, get_file_preview_url
from reactor_tool.model.protocal import get_file_id

from ...document import DocumentProcessor
from ...enums.source_type_enums import SourceTypeEnum
from ...enums.task_status_enums import TaskStatusEnum
from ...storage import VectorStore
from ...storage.models.kb_file_model import KBFileModel
from ...storage.models.kb_model import KBModel
from ...storage.store_factory import get_kb_doc_store, get_kb_store, get_kb_file_store
from ...utils import download_utils
from ...utils.logger_utils import logger
from ...utils.oss_utils import upload_oss

router = APIRouter(prefix="/documents", tags=["文档处理"])

# 支持的文件类型。这里只保留当前解析链路已验证可处理的格式，避免上传成功但后台解析失败。
SUPPORTED_FILE_TYPES = {
    # 文档类型
    'application/pdf': '.pdf',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
    'text/plain': '.txt',
    'text/markdown': '.md',
    'text/x-markdown': '.md',
    # 图片类型
    'image/jpeg': '.jpg',
    'image/jpg': '.jpg',
    'image/png': '.png',
    'image/gif': '.gif',
    'image/bmp': '.bmp',
    'image/webp': '.webp',
    'image/tiff': '.tiff',
    'image/tif': '.tif'
}

# 最大文件大小 (50MB)
MAX_FILE_SIZE = 50 * 1024 * 1024
FULL_CONTENT_READY = "READY"
FULL_CONTENT_PROCESSING = "PROCESSING"
FULL_CONTENT_FAILED = "FAILED"
FULL_CONTENT_UNAVAILABLE = "UNAVAILABLE"
FULL_CONTENT_FORMAT_MARKDOWN = "markdown"


def _get_supported_extensions() -> str:
    return ", ".join(sorted(set(SUPPORTED_FILE_TYPES.values())))


def _resolve_file_extension(file: UploadFile) -> str:
    """优先按 content_type 识别，识别不到时回退到文件后缀。"""
    content_type = (file.content_type or "").lower().strip()
    if content_type in SUPPORTED_FILE_TYPES:
        return SUPPORTED_FILE_TYPES[content_type]

    file_extension = Path(file.filename or "").suffix.lower()
    if file_extension in SUPPORTED_FILE_TYPES.values():
        return file_extension

    raise HTTPException(
        status_code=400,
        detail=f"不支持的文件类型: {content_type or file_extension or 'unknown'}。支持的类型: {_get_supported_extensions()}"
    )


def _is_s3_configured() -> bool:
    return all(
        os.getenv(env_name, "").strip()
        for env_name in ("S3_BUCKET_NAME", "S3_ACCESS_KEY", "S3_SECRET_KEY", "S3_ENDPOINT")
    )


async def _upload_to_local_file_storage(file: UploadFile, document_id: str, safe_filename: str):
    """
    直接复用本地文件服务的底层落盘逻辑，避免通过 HTTP 回调自身导致阻塞。
    """
    request_id = document_id
    stored_filename = f"{document_id}_{safe_filename}"
    original_filename = file.filename

    await file.seek(0)
    file.filename = stored_filename
    try:
        file_info = await FileInfoOp.add_by_file(
            file=file,
            file_id=get_file_id(request_id, stored_filename),
            request_id=request_id
        )
    finally:
        file.filename = original_filename

    download_url = get_file_download_url(file_id=file_info.request_id, file_name=file_info.filename)
    preview_url = get_file_preview_url(file_id=file_info.request_id, file_name=file_info.filename)
    return download_url, preview_url, file_info.filename


def _normalize_source_type(raw_source_type: Optional[str], title: Optional[str], file_url: Optional[str]) -> str:
    """兼容历史脏数据，统一给前端返回稳定来源语义。"""
    source_type = (raw_source_type or "").strip().lower()
    normalized_title = (title or "").strip()
    normalized_url = (file_url or "").strip().lower()

    if source_type == SourceTypeEnum.URL.value:
        return SourceTypeEnum.URL.value
    if source_type == SourceTypeEnum.FILE.value:
        if not normalized_title and normalized_url.startswith(("http://", "https://")):
            return SourceTypeEnum.URL.value
        return SourceTypeEnum.FILE.value
    if not normalized_title and normalized_url.startswith(("http://", "https://")):
        return SourceTypeEnum.URL.value
    return SourceTypeEnum.FILE.value


def _resolve_global_status(kb_file: KBFileModel) -> str:
    task_status = kb_file.task_status or {}
    status_value = task_status.get("global_status") or kb_file.file_status or ""
    return str(status_value).upper()


def _serialize_kb_file(kb_file: KBFileModel) -> dict:
    payload = kb_file.model_dump(mode="json")
    payload["source_type"] = _normalize_source_type(
        kb_file.source_type,
        kb_file.title,
        kb_file.file_url,
    )
    return payload


def _build_full_content_payload(
    kb_file: KBFileModel,
    content_status: str,
    content: str = "",
    error_message: str = "",
) -> dict:
    return {
        "kb_id": kb_file.kb_id,
        "file_id": kb_file.file_id,
        "title": kb_file.title or "",
        "file_url": kb_file.file_url or "",
        "source_type": _normalize_source_type(kb_file.source_type, kb_file.title, kb_file.file_url),
        "file_status": _resolve_global_status(kb_file),
        "content_status": content_status,
        "content_format": FULL_CONTENT_FORMAT_MARKDOWN,
        "content": content,
        "error_message": error_message,
    }


@router.post("/upload")
async def upload_document(file: UploadFile = File(...)):
    """
    上传文档和图片到OSS并返回访问链接
    
    支持的文件类型: 
    - 文档: PDF, DOC, DOCX, TXT, XLS, XLSX, PPT, PPTX
    - 图片: JPG, JPEG, PNG, GIF, BMP, WEBP, TIFF, TIF, SVG
    最大文件大小: 50MB
    
    返回:
        - document_id: 文档唯一标识
        - filename: 原始文件名
        - file_size: 文件大小(字节)
        - content_type: 文件类型
        - upload_time: 上传时间
        - permanent_url: 永久访问链接
        - presigned_url: 临时访问链接(30天有效)
    """
    print(file)
    try:
        # 验证文件类型
        file_extension = _resolve_file_extension(file)

        # 读取文件内容并验证大小
        file_content = await file.read()
        file_size = len(file_content)

        if file_size > MAX_FILE_SIZE:
            raise HTTPException(
                status_code=400,
                detail=f"文件大小超过限制。最大允许: {MAX_FILE_SIZE // (1024 * 1024)}MB，当前文件: {file_size // (1024 * 1024)}MB"
            )

        if file_size == 0:
            raise HTTPException(status_code=400, detail="文件为空")

        # 生成文档ID和文件名
        document_id = str(uuid.uuid4())

        # 确保文件名有正确的扩展名
        original_filename = file.filename or f"{document_id}{file_extension}"
        if not original_filename.lower().endswith(file_extension.lower()):
            safe_filename = f"{Path(original_filename).stem}{file_extension}"
        else:
            safe_filename = original_filename

        upload_date = datetime.now()

        if _is_s3_configured():
            # 创建临时文件
            with tempfile.NamedTemporaryFile(delete=False, suffix=file_extension) as temp_file:
                temp_file.write(file_content)
                temp_file_path = temp_file.name

            logger.info(f"文件暂存到: {temp_file_path}")
            try:
                # 上传到OSS
                # 使用日期作为目录结构: documents/YYYY/MM/DD/
                oss_dir = f"documents/{upload_date.year:04d}/{upload_date.month:02d}/{upload_date.day:02d}"

                logger.info(f"开始上传文档到OSS: {safe_filename}, 大小: {file_size} bytes")

                success, permanent_url, presigned_url = upload_oss(
                    file_path=temp_file_path,
                    dir_=oss_dir,
                    is_delete=False
                )

                if not success:
                    raise HTTPException(status_code=500, detail="文件上传到OSS失败")

                logger.info(f"文档上传成功: {document_id}, 永久链接: {permanent_url}")

                return JSONResponse(
                    status_code=200,
                    content={
                        "success": True,
                        "message": "文档上传成功",
                        "data": {
                            "document_id": document_id,
                            "filename": safe_filename,
                            "original_filename": original_filename,
                            "file_size": file_size,
                            "content_type": file.content_type,
                            "upload_time": upload_date.isoformat(),
                            "permanent_url": permanent_url,
                            "presigned_url": presigned_url,
                            "preview_url": permanent_url,
                            "storage_type": "s3",
                            "oss_path": f"{oss_dir}/{safe_filename}"
                        }
                    }
                )
            finally:
                if os.path.exists(temp_file_path):
                    os.remove(temp_file_path)

        logger.info(f"S3 未配置，改用本地文件服务保存文档: {safe_filename}")
        permanent_url, preview_url, stored_filename = await _upload_to_local_file_storage(
            file=file,
            document_id=document_id,
            safe_filename=safe_filename
        )

        logger.info(f"文档上传成功: {document_id}, 本地访问链接: {permanent_url}")

        return JSONResponse(
            status_code=200,
            content={
                "success": True,
                "message": "文档上传成功",
                "data": {
                    "document_id": document_id,
                    "filename": safe_filename,
                    "original_filename": original_filename,
                    "stored_filename": stored_filename,
                    "file_size": file_size,
                    "content_type": file.content_type,
                    "upload_time": upload_date.isoformat(),
                    "permanent_url": permanent_url,
                    "presigned_url": permanent_url,
                    "preview_url": preview_url,
                    "storage_type": "local",
                    "oss_path": stored_filename
                }
            }
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"文档上传过程中发生错误: {str(e)}")
        import traceback
        logger.error(traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"服务器内部错误: {str(e)}")


class CreateKnowledgeBaseRequest(BaseModel):
    kb_id: Optional[str] = None
    kb_name: Optional[str] = None
    kb_desc: Optional[str] = None
    chunk_type: Optional[str] = Field("fixed_size", description="chunk_type")
    chunk_size: Optional[int] = Field(None, description="chunk_size")
    chunk_overlap_size: Optional[int] = Field(None, description="chunk_overlap_size")


@router.post("/create_knowledge_base")
async def create_knowledge_base(request: CreateKnowledgeBaseRequest):
    kb_store = get_kb_store()
    if not request.kb_id:
        request.kb_id = uuid.uuid4().hex
    kb_id = request.kb_id
    kb_model = KBModel(
        kb_id=kb_id,
        kb_name=request.kb_name,
        kb_desc=request.kb_desc,
        chunk_type=request.chunk_type,
        chunk_size=request.chunk_size,
        chunk_overlap_size=request.chunk_overlap_size,
        create_time=datetime.now(),
        modify_time=datetime.now(),
    )
    kb_store.create_kb(kb_model)

    res = {
        "code": 200,
        "msg": "success",
        "data": kb_model.model_dump()
    }
    logger.info(f"create knowledge base, {res}")
    return res


class DeleteKnowledgeBaseRequest(BaseModel):
    kb_id: Optional[str]


@router.post("/delete_knowledge_base")
async def delete_knowledge_base(request: DeleteKnowledgeBaseRequest):
    kb_store = get_kb_store()
    kb_file_store = get_kb_file_store()
    kb_doc_store = get_kb_doc_store()
    if not request.kb_id:
        raise HTTPException(status_code=400, detail="kb_id 不能为空")
    kb_model = KBModel(kb_id=request.kb_id)
    deleted = kb_store.delete_kb(kb_model)
    if not deleted:
        raise HTTPException(status_code=404, detail="知识库不存在或已删除")

    deleted_file_count = kb_file_store.delete_by_kb_id(request.kb_id)
    kb_doc_store.delete_by_kb_id(request.kb_id)
    VectorStore().delete_text_by_kb_id(request.kb_id)
    VectorStore().delete_image_by_kb_id(request.kb_id)
    VectorStore().delete_page_by_kb_id(request.kb_id)

    return {
        "code": 200,
        "msg": "success",
        "data": {
            "kb_id": request.kb_id,
            "deleted_file_count": deleted_file_count,
        }
    }


class ListKnowledgeBaseRequest(BaseModel):
    page_no: Optional[int] = Field(1, description="page_no")
    page_size: Optional[int] = Field(10, description="page_size")


@router.post("/list_knowledge_base")
async def list_knowledge_base(request: ListKnowledgeBaseRequest):
    kb_store = get_kb_store()
    kb_models = kb_store.get_kbs(request.page_no, request.page_size)
    res = {
        "code": 200,
        "msg": "success",
        "data": {
            "list": [kb_model.model_dump() for kb_model in kb_models],
            "page_no": request.page_no,
            "page_size": request.page_size,
        }
    }
    logger.info(f"list knowledge base, {res}")
    return res


class File(BaseModel):
    filename: Optional[str] = Field(..., description="文件名")
    file_url: Optional[str] = Field(..., description="文件的url")
    file_type: Optional[str] = Field("file")


def add_file(filename, file_url, kb_id):
    tempdir = tempfile.gettempdir()
    # 下载文件到临时目录
    file_id = uuid.uuid4().hex
    work_dir = f"{tempdir}/{file_id}"
    os.makedirs(os.path.join(tempdir, file_id), exist_ok=True)

    kb_file = KBFileModel(
        kb_id=kb_id,
        file_id=file_id,
        file_url=file_url,
        title=filename,
        file_ext=os.path.splitext(filename)[1].lower(),
        source_type=SourceTypeEnum.FILE.value,
        task_status={"global_status": TaskStatusEnum.PENDING.value},
        file_status=TaskStatusEnum.PENDING.value,
        doc_count=0,
        create_time=datetime.now(),
        modify_time=datetime.now(),
        deleted=0
    )
    kb_file_store = get_kb_file_store()
    kb_file_store.add_file(kb_file)

    local_file_path = os.path.join(tempdir, file_id, filename)
    try:
        download_utils.download_file(file_url, local_file_path)

        kb_file.file_status = TaskStatusEnum.RUNNING.value
        kb_file.task_status = {"global_status": TaskStatusEnum.RUNNING.value}
        kb_file_store.update_file(kb_file)

        processor = DocumentProcessor(kb_id, file_id, work_dir, local_file_path, file_url)
        processor.process()

        kb_file.file_status = TaskStatusEnum.SUCCESS.value
        kb_file.task_status = {"global_status": TaskStatusEnum.SUCCESS.value}
        kb_file_store.update_file(kb_file)
    except Exception as e:
        logger.exception(f"处理知识库文件失败: kb_id={kb_id}, file_id={file_id}, filename={filename}")
        kb_file.file_status = TaskStatusEnum.FAILED.value
        kb_file.task_status = {
            "global_status": TaskStatusEnum.FAILED.value,
            "error_message": str(e),
        }
        kb_file_store.update_file(kb_file)
    finally:
        if os.path.exists(local_file_path):
            os.remove(local_file_path)


class AddFilesRequest(BaseModel):
    files: List[File] = Field(..., description="文件列表")
    kb_id: str = Field(..., description="知识库id")


@router.post("/add_files")
async def add_files(request: AddFilesRequest, background_tasks: BackgroundTasks):
    for file in request.files:
        filename = file.filename
        file_url = file.file_url

        background_tasks.add_task(
            add_file,
            filename=filename,
            file_url=file_url,
            kb_id=request.kb_id,
        )

    return {
        "code": 200,
        "msg": "success",
        "data": {}
    }


def add_web_url(url, kb_id):
    tempdir = tempfile.gettempdir()
    # 下载文件到临时目录
    file_id = uuid.uuid4().hex
    work_dir = f"{tempdir}/{file_id}"
    os.makedirs(os.path.join(tempdir, file_id), exist_ok=True)

    kb_file = KBFileModel(
        kb_id=kb_id,
        file_id=file_id,
        file_url=url,
        title="",
        file_ext=".md",
        source_type=SourceTypeEnum.URL.value,
        task_status={"global_status": TaskStatusEnum.PENDING.value},
        file_status=TaskStatusEnum.PENDING.value,
        doc_count=0,
        create_time=datetime.now(),
        modify_time=datetime.now(),
        deleted=0
    )
    kb_file_store = get_kb_file_store()
    kb_file_store.add_file(kb_file)

    local_file_path = os.path.join(tempdir, file_id, f"{file_id}.md")
    try:
        from ...utils import crawl_utils

        markdown_content = crawl_utils.crawl(url)
        if not markdown_content:
            kb_file.file_status = TaskStatusEnum.FAILED.value
            kb_file.task_status = {
                "global_status": TaskStatusEnum.FAILED.value,
                "error_message": "网页内容抓取失败",
            }
            kb_file_store.update_file(kb_file)
            return

        with open(local_file_path, "w", encoding="utf-8") as f:
            f.write(markdown_content)

        kb_file.file_status = TaskStatusEnum.RUNNING.value
        kb_file.task_status = {"global_status": TaskStatusEnum.RUNNING.value}
        kb_file_store.update_file(kb_file)

        processor = DocumentProcessor(kb_id, file_id, work_dir, local_file_path, url)
        processor.process()

        kb_file.file_status = TaskStatusEnum.SUCCESS.value
        kb_file.task_status = {"global_status": TaskStatusEnum.SUCCESS.value}
        kb_file_store.update_file(kb_file)
    except Exception as e:
        logger.exception(f"处理网页知识失败: kb_id={kb_id}, file_id={file_id}, url={url}")
        kb_file.file_status = TaskStatusEnum.FAILED.value
        kb_file.task_status = {
            "global_status": TaskStatusEnum.FAILED.value,
            "error_message": str(e),
        }
        kb_file_store.update_file(kb_file)
    finally:
        if os.path.exists(local_file_path):
            os.remove(local_file_path)


class AddWebUrlRequest(BaseModel):
    url: str = Field(..., description="url")
    kb_id: str = Field(..., description="知识库id")


@router.post("/add_web_url")
async def async_add_web_url(request: AddWebUrlRequest, background_tasks: BackgroundTasks):
    kb_id = request.kb_id
    url = request.url
    background_tasks.add_task(
        add_web_url,
        url, kb_id,
    )

    return {
        "code": 200,
        "msg": "success",
    }


class DeleteFileRequest(BaseModel):
    file_ids: List[str] = Field(..., description="文件id")
    kb_id: str = Field(..., description="知识库id")


@router.post("/delete_files")
async def delete_files(request: DeleteFileRequest):
    file_ids = request.file_ids
    kb_id = request.kb_id

    kb_file_store = get_kb_file_store()
    kb_file_store.delete_by_file_ids(kb_id, file_ids)
    get_kb_doc_store().delete_by_file_ids(kb_id, file_ids)

    vector_store = VectorStore()
    vector_store.delete_by_file_ids(kb_id, file_ids)

    return {
        "code": 200,
        "msg": "success",
        "data": {}
    }


class ListKBFilesRequest(BaseModel):
    kb_id: str
    page_no: Optional[int] = Field(1, description="page_no")
    page_size: Optional[int] = Field(10, description="page_size")


@router.post("/list_kb_files")
async def list_kb_files(request: ListKBFilesRequest):
    kb_id = request.kb_id
    page_no = request.page_no
    page_size = request.page_size
    kb_file_store = get_kb_file_store()
    records = kb_file_store.list_kb_files(kb_id, page_no, page_size)
    total = kb_file_store.count_kb_files(kb_id)
    return {
        "code": 200,
        "msg": "success",
        "data": {
            "total": total,
            "records": [_serialize_kb_file(record) for record in records],
            "page_no": page_no,
            "page_size": page_size,
        }
    }


class GetFileFullContentRequest(BaseModel):
    kb_id: str = Field(..., description="知识库id")
    file_id: str = Field(..., description="文件id")


@router.post("/get_file_full_content")
async def get_file_full_content(request: GetFileFullContentRequest):
    kb_file_store = get_kb_file_store()
    kb_doc_store = get_kb_doc_store()
    kb_file = kb_file_store.get_file(request.kb_id, request.file_id)
    if not kb_file:
        raise HTTPException(status_code=404, detail="文件不存在或已删除")

    global_status = _resolve_global_status(kb_file)
    if global_status in {TaskStatusEnum.PENDING.value, TaskStatusEnum.RUNNING.value}:
        return {
            "code": 200,
            "msg": "success",
            "data": _build_full_content_payload(
                kb_file,
                content_status=FULL_CONTENT_PROCESSING,
                error_message="正文仍在生成中，请稍后重试。",
            )
        }

    if global_status == TaskStatusEnum.FAILED.value:
        task_status = kb_file.task_status or {}
        error_message = str(task_status.get("error_message") or "文件处理失败，暂无可回显正文。")
        return {
            "code": 200,
            "msg": "success",
            "data": _build_full_content_payload(
                kb_file,
                content_status=FULL_CONTENT_FAILED,
                error_message=error_message,
            )
        }

    canonical_doc = kb_doc_store.get_canonical_doc(request.kb_id, request.file_id)
    if not canonical_doc or not (canonical_doc.text or "").strip():
        return {
            "code": 200,
            "msg": "success",
            "data": _build_full_content_payload(
                kb_file,
                content_status=FULL_CONTENT_UNAVAILABLE,
                error_message="当前文件暂无可回显正文。",
            )
        }

    return {
        "code": 200,
        "msg": "success",
        "data": _build_full_content_payload(
            kb_file,
            content_status=FULL_CONTENT_READY,
            content=canonical_doc.text or "",
        )
    }
