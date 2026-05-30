import os
from typing import List

from fastapi import UploadFile
from sqlmodel import select

from reactor_tool.db.file_table import FileInfo
from reactor_tool.db.db_engine import async_session_local
from reactor_tool.util.log_util import timer


class _FileDB(object):
    def __init__(self):
        self._work_dir = os.getenv("FILE_SAVE_PATH", "file_db_dir")
        if not os.path.exists(self._work_dir):
            os.makedirs(self._work_dir)

    async def save(self, file_name, content, scope) -> str:
        if "." in file_name:
            file_name = os.path.basename(file_name)
        else:
            file_name = f"{file_name}.txt"

        # On Windows, characters like ":" are not allowed in directory names.
        # `scope` can contain ":" (e.g. "reactorsession-...:..."), which causes
        # `NotADirectoryError`. Normalize it into a filesystem-safe name.
        safe_scope = "".join(c if c not in '<>:"/\\|?*' else "_" for c in str(scope))

        save_path = os.path.join(self._work_dir, safe_scope)
        if not os.path.exists(save_path):
            os.makedirs(save_path)
        with open(f"{save_path}/{file_name}", "w", encoding='utf-8') as f:
            f.write(content)
        return f"{save_path}/{file_name}"
    
    async def save_by_data(self, file: UploadFile, scope: str = None) -> str:
        file_name = file.filename
        file_data = file.file.read()
        safe_scope = "".join(c if c not in '<>:"/\\|?*' else "_" for c in str(scope)) if scope else ""
        save_directory = self._work_dir if not safe_scope else os.path.join(self._work_dir, safe_scope)
        if not os.path.exists(save_directory):
            os.makedirs(save_directory)
        save_path = os.path.join(save_directory, file_name)
        with open(save_path, "wb") as f:
             f.write(file_data)
        return save_path


FileDB = _FileDB()


def normalize_stored_file_name(file_name: str) -> str:
    """统一文件服务对外暴露的文件名，避免子路径污染 fileId 与预览 URL。"""
    normalized = os.path.basename((file_name or "").strip())
    if not normalized:
        raise ValueError("file_name is empty")
    return normalized


class FileInfoOp(object):

    @classmethod
    @timer()
    async def add_by_content(cls, filename: str, content: str, file_id: str, description: str = None,
                             request_id: str = None) -> FileInfo:
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

    @staticmethod
    @timer()
    async def add(file_info: FileInfo) -> FileInfo:
        file_id = file_info.file_id
        f = await FileInfoOp.get_by_file_id(file_info.file_id)
        async with async_session_local() as session:
            if f:
                f.status = 1
                f.file_size = file_info.file_size
                session.add(f)
            else:
                session.add(file_info)
            await session.commit()
        return await FileInfoOp.get_by_file_id(file_id)

    @staticmethod
    @timer()
    async def get_by_file_id(file_id: str) -> FileInfo:
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.file_id == file_id)
            result = await session.execute(state)
            return result.scalars().one_or_none()

    @staticmethod
    @timer()
    async def get_by_file_ids(file_ids: List[str]) -> List[FileInfo]:
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.file_id.in_(file_ids))
            result = await session.execute(state)
            return result.scalars().all()

    @staticmethod
    @timer()
    async def get_by_request_id(request_id: str) -> List[FileInfo]:
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.request_id == request_id)
            result = await session.execute(state)
            return result.scalars().all()

def get_file_preview_url(file_id: str, file_name: str):
    normalized_file_name = normalize_stored_file_name(file_name)
    return f"{os.getenv('FILE_SERVER_URL')}/preview/{file_id}/{normalized_file_name}"


def get_file_download_url(file_id: str, file_name: str):
    normalized_file_name = normalize_stored_file_name(file_name)
    return f"{os.getenv('FILE_SERVER_URL')}/download/{file_id}/{normalized_file_name}"
