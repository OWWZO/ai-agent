# -*- coding: utf-8 -*-
"""文件落盘与元数据 CRUD。

- FileDB: 按 request_id(scope) 将内容写到本地目录
- FileInfoOp: 元数据增查（同 file_id 覆盖更新）
- get_file_*_url: 拼预览/下载 URL，供 Java 与前端消费
"""
import os
import re
from typing import List

from fastapi import UploadFile
from sqlmodel import select

from reactor_tool.db.file_table import FileInfo
from reactor_tool.db.db_engine import async_session_local
from reactor_tool.util.log_util import timer


class _FileDB(object):
    """本地文件存储：按 scope 分子目录，避免不同会话文件互相覆盖。"""

    def __init__(self):
        self._work_dir = os.getenv("FILE_SAVE_PATH", "file_db_dir")
        if not os.path.exists(self._work_dir):
            os.makedirs(self._work_dir)

    async def save(self, file_name, content, scope) -> str:
        """将文本内容写入 scope 目录，返回落盘路径。"""
        # 文件名和 scope 分别清洗：前者控制单文件名，后者控制会话目录，不能把完整用户路径直接拼入落盘路径。
        if "." in file_name:
            file_name = os.path.basename(file_name)
        else:
            file_name = f"{file_name}.txt"
        file_name = normalize_stored_file_name(file_name)

        # Windows 目录名不允许 ":" 等字符；request_id 常含冒号，需清洗
        safe_scope = "".join(c if c not in '<>:"/\\|?*' else "_" for c in str(scope))

        save_path = os.path.join(self._work_dir, safe_scope)
        if not os.path.exists(save_path):
            os.makedirs(save_path)
        file_path = os.path.join(save_path, file_name)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        return file_path

    async def save_by_data(self, file: UploadFile, scope: str = None) -> str:
        """将 UploadFile 二进制写入本地，返回落盘路径。"""
        # 上传流可能已被上游读取，先尝试回到开头；只有确认仍为空才走底层 file 对象的同步兜底读取。
        file_name = normalize_stored_file_name(file.filename)
        # 必须二进制读取；await file.read() 兼容已消费/未 seek 的 stream
        try:
            await file.seek(0)
        except Exception:
            try:
                file.file.seek(0)
            except Exception:
                pass
        file_data = await file.read()
        if not file_data:
            # fallback sync read once more after seek
            try:
                file.file.seek(0)
                file_data = file.file.read()
            except Exception:
                file_data = b""
        if not file_data:
            raise ValueError(f"uploaded file is empty: {file_name}")
        safe_scope = "".join(c if c not in '<>:"/\\|?*' else "_" for c in str(scope)) if scope else ""
        save_directory = self._work_dir if not safe_scope else os.path.join(self._work_dir, safe_scope)
        if not os.path.exists(save_directory):
            os.makedirs(save_directory)
        save_path = os.path.join(save_directory, file_name)
        with open(save_path, "wb") as f:
            f.write(file_data)
        return save_path


FileDB = _FileDB()


_INVALID_FILE_NAME_CHARS = re.compile(r'[<>:"/\\|?*\x00-\x1f]')
_WINDOWS_RESERVED_FILE_NAMES = {
    "CON", "PRN", "AUX", "NUL",
    *(f"COM{index}" for index in range(1, 10)),
    *(f"LPT{index}" for index in range(1, 10)),
}
_MAX_STORED_FILE_NAME_LENGTH = 120


def normalize_stored_file_name(file_name: str) -> str:
    """统一文件名，避免 Windows 非法字符、保留名和超长路径导致落盘失败。"""
    # 归一化结果同时用于落盘、file_id 计算和 URL 生成，必须在所有入口保持一致。
    normalized = os.path.basename((file_name or "").strip())
    if not normalized:
        raise ValueError("file_name is empty")

    normalized = _INVALID_FILE_NAME_CHARS.sub("_", normalized).rstrip(" .")
    if not normalized:
        raise ValueError("file_name is empty")

    stem, suffix = os.path.splitext(normalized)
    if stem.upper() in _WINDOWS_RESERVED_FILE_NAMES:
        stem = f"_{stem}"

    suffix = suffix[:_MAX_STORED_FILE_NAME_LENGTH - 1]
    max_stem_length = max(1, _MAX_STORED_FILE_NAME_LENGTH - len(suffix))
    normalized = f"{stem[:max_stem_length]}{suffix}"
    return normalized


class FileInfoOp(object):
    """FileInfo 表操作：按内容/上传流写入，并维护 file_id 唯一索引。"""

    @classmethod
    @timer()
    async def add_by_content(cls, filename: str, content: str, file_id: str, description: str = None,
                             request_id: str = None) -> FileInfo:
        """文本内容落盘 + 写元数据。"""
        # 先落盘再写元数据，确保数据库中的 file_path 指向已经存在的文件。
        filename = normalize_stored_file_name(filename)
        file_path = await FileDB.save(filename, content, scope=request_id)
        file_info = FileInfo(
            file_id=file_id,
            filename=filename,
            file_path=file_path,
            description=description,
            file_size=os.path.getsize(file_path),
            status=1,
            request_id=request_id
        )
        return await cls.add(file_info)

    @staticmethod
    @timer()
    async def add_by_file(file: UploadFile, file_id: str, request_id: str = None) -> FileInfo:
        """multipart 上传流落盘 + 写元数据。"""
        file.filename = normalize_stored_file_name(file.filename)
        file_path = await FileDB.save_by_data(file, scope=request_id)

        file_info = FileInfo(
            file_id=file_id,
            filename=file.filename,
            file_path=file_path,
            description="",
            file_size=os.path.getsize(file_path),
            status=1,
            request_id=request_id
        )
        return await FileInfoOp.add(file_info)

    @classmethod
    @timer()
    async def add_by_existing_path(
        cls,
        filename: str,
        local_path: str,
        file_id: str,
        description: str = None,
        request_id: str = None,
    ) -> FileInfo:
        """登记已落盘文件：不拷贝内容，file_path 指向已有绝对路径。"""
        # register 与 upload 的区别是只登记引用；因此必须先验证源文件存在，再把绝对路径写入元数据。
        filename = normalize_stored_file_name(filename)
        if not local_path or not os.path.isfile(local_path):
            raise FileNotFoundError(f"local file not found: {local_path}")
        abs_path = os.path.abspath(local_path)
        file_info = FileInfo(
            file_id=file_id,
            filename=filename,
            file_path=abs_path,
            description=description or "",
            file_size=os.path.getsize(abs_path),
            status=1,
            request_id=request_id,
        )
        return await cls.add(file_info)

    @staticmethod
    @timer()
    async def add(file_info: FileInfo) -> FileInfo:
        """插入或按 file_id 覆盖更新（同 ID 视为重新上传）。"""
        # file_id 是逻辑唯一键；重复上传更新同一元数据行，避免列表接口出现同一文件的多个活动版本。
        file_id = file_info.file_id
        f = await FileInfoOp.get_by_file_id(file_info.file_id)
        async with async_session_local() as session:
            if f:
                f.status = 1
                f.file_size = file_info.file_size
                f.file_path = file_info.file_path
                f.filename = file_info.filename
                f.description = file_info.description
                f.request_id = file_info.request_id
                session.add(f)
            else:
                session.add(file_info)
            await session.commit()
        return await FileInfoOp.get_by_file_id(file_id)

    @staticmethod
    @timer()
    async def get_by_file_id(file_id: str) -> FileInfo:
        """按逻辑 file_id 查单条。"""
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.file_id == file_id)
            result = await session.execute(state)
            return result.scalars().one_or_none()

    @staticmethod
    @timer()
    async def get_by_file_ids(file_ids: List[str]) -> List[FileInfo]:
        """批量按 file_id 查询。"""
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.file_id.in_(file_ids))
            result = await session.execute(state)
            return result.scalars().all()

    @staticmethod
    @timer()
    async def get_by_request_id(request_id: str) -> List[FileInfo]:
        """按会话/请求 ID 列出全部产物文件。"""
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.request_id == request_id)
            result = await session.execute(state)
            return result.scalars().all()


def get_file_preview_url(file_id: str, file_name: str):
    """拼预览 URL：/preview/{request_id}/{file_name}。"""
    normalized_file_name = normalize_stored_file_name(file_name)
    return f"{os.getenv('FILE_SERVER_URL')}/preview/{file_id}/{normalized_file_name}"


def get_file_download_url(file_id: str, file_name: str):
    """拼下载 URL：/download/{request_id}/{file_name}。"""
    normalized_file_name = normalize_stored_file_name(file_name)
    return f"{os.getenv('FILE_SERVER_URL')}/download/{file_id}/{normalized_file_name}"
