# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
import hashlib
import secrets
import string
import json
import os
import shutil
from copy import deepcopy
from pathlib import Path
from typing import List, Dict, Any

import aiohttp
from loguru import logger

from reactor_tool.util.log_util import timer
from reactor_tool.model.document import Doc


@timer()
async def get_file_content(file_name: str) -> str:
    # local file
    if _is_local_file_reference(file_name):
        local_path = _normalize_local_path(file_name)
        try:
            with open(local_path, "r", encoding='utf-8') as rf:
                return rf.read()
        except UnicodeDecodeError:
            # UTF-8失败时尝试GBK
            with open(local_path, "r", encoding='gbk') as rf:
                return rf.read()
    # file server
    else:
        b_content = b""
        async with aiohttp.ClientSession() as session:
            async with session.get(file_name, timeout=99999) as response:
                while True:
                    chunk = await response.content.read(1024)
                    if not chunk:
                        break
                    b_content += chunk
        try:
            return b_content.decode("utf-8")
        except UnicodeDecodeError:
            return b_content.decode("gbk", errors='ignore')


@timer()
async def download_all_files(file_names: list[str]) -> List[Dict[str, Any]]:
    file_contents = []
    for file_name in file_names:
        try:
            file_contents.append(
                {
                    "file_name": file_name,
                    "content": await get_file_content(file_name),
                }
            )
        except Exception as e:
            logger.warning(f"Failed to download file {file_name}. Exception: {e}")
            file_contents.append(
                {
                    "file_name": file_name,
                    "content": "Failed to get content.",
                }
            )
    return file_contents


@timer()
def truncate_files(
    files: List[Dict[str, Any]] | List[Doc], max_tokens: int
) -> List[Dict[str, Any]] | List[Doc]:
    """近似计算 token 数"""
    truncated_files = []
    token_size = 0
    for f_a in files:
        f = deepcopy(f_a)
        if token_size >= max_tokens:
            break
        if isinstance(f, Doc):
            dct = f.to_dict()
            dct["content"] = dct["content"][: max_tokens - token_size]
            token_size += len(dct["content"] or "")
            f = Doc(**dct)
        else:
            f["content"] = f["content"][: max_tokens - token_size]
            token_size += len(f.get("content", ""))
        truncated_files.append(f)
    return truncated_files


@timer()
async def upload_file(
    content: str,
    file_name: str,
    file_type: str,
    request_id: str,
):
    if file_type == "markdown":
        file_type = "md"
    if not file_name.endswith(file_type):
        file_name = f"{file_name}.{file_type}"
    storage_target = _get_file_storage_target()
    if _is_http_endpoint(storage_target):
        body = {
            "requestId": request_id,
            "fileName": file_name,
            "content": content,
            "description": content[:200],
        }
        async with aiohttp.ClientSession() as session:
            async with session.post(
                f"{storage_target}/upload_file", json=body, timeout=99999
            ) as response:
                result = json.loads(await response.text())
        return {
            "fileName": file_name,
            "ossUrl": result["downloadUrl"],
            "domainUrl": result["domainUrl"],
            "downloadUrl": result["downloadUrl"],
            "fileSize": len(content),
        }

    target_file = _write_local_text_file(
        storage_root=storage_target,
        request_id=request_id,
        file_name=file_name,
        content=content,
    )
    local_reference = str(target_file)
    return {
        "fileName": file_name,
        "ossUrl": local_reference,
        "domainUrl": local_reference,
        "downloadUrl": local_reference,
        "fileSize": len(content.encode("utf-8")),
    }


@timer()
async def upload_file_by_path(
    file_path: str,
    request_id: str,
):
    if not os.path.exists(file_path):
        return None
    file_name = os.path.basename(file_path)
    file_size = os.path.getsize(file_path)
    storage_target = _get_file_storage_target()
    if _is_http_endpoint(storage_target):
        data = aiohttp.FormData()
        data.add_field("requestId", request_id)
        data.add_field(
            "file",
            open(file_path, "rb"),
            filename=file_name,
            content_type="application/octet-stream",
        )
        async with aiohttp.ClientSession() as session:
            async with session.post(
                f"{storage_target}/upload_file_data", data=data, timeout=99999
            ) as response:
                result = json.loads(await response.text())
        return {
            "fileName": file_name,
            "ossUrl": result["downloadUrl"],
            "domainUrl": result["domainUrl"],
            "downloadUrl": result["downloadUrl"],
            "fileSize": file_size,
        }

    target_file = _copy_file_to_local_storage(
        storage_root=storage_target,
        request_id=request_id,
        source_file=Path(file_path),
    )
    local_reference = str(target_file)
    return {
        "fileName": file_name,
        "ossUrl": local_reference,
        "domainUrl": local_reference,
        "downloadUrl": local_reference,
        "fileSize": file_size,
    }


def generate_data_id(prefix: str = ""):
    """生成数据业务主键，规则：前缀 - 15位随机字符串（包含数字和字母）"""
    return f"{prefix}_{generate_secure_random_string(15)}"


def generate_secure_random_string(length):
    characters = string.ascii_letters + string.digits
    secure_random = secrets.SystemRandom()
    return "".join(secure_random.choice(characters) for _ in range(length))


def flatten_search_file(s_file: Dict[str, Any]) -> List[Dict[str, Any]]:
    flat_files = []
    try:
        contents = json.loads(s_file["content"])
        for k, v in contents.items():
            flat_files.extend(v)
    except Exception as e:
        logger.warning(f"parser file error: {e}")
    return flat_files


@timer()
async def get_file_path(file_name: str, word_dir: str) -> str:
    if _is_local_file_reference(file_name):
        return str(_normalize_local_path(file_name))
    else:
        b_content = b""
        file_path = os.path.join(word_dir, os.path.basename(file_name))
        async with aiohttp.ClientSession() as session:
            try:
                async with session.get(file_name, timeout=99999) as response:
                    response.raise_for_status() # 检查HTTP状态码，如果不是2xx则抛出异常
                    while True:
                        chunk = await response.content.read(1024)
                        if not chunk:
                            break
                        b_content += chunk
            except aiohttp.ClientError as e:
                print(f"下载文件失败: {e}")
                return None # 或者抛出异常
            except TimeoutError:
                print(f"下载文件超时: {file_name}")
                return ""
        with open(file_path, "wb") as f: 
            f.write(b_content)
        return file_path


@timer()
async def download_all_files_in_path(file_names: list[str], work_dir: str) -> List[Dict[str, Any]]:
    file_paths = []
    for file_name in file_names:
        try:
            file_paths.append(
                {
                    "file_name": os.path.basename(file_name),
                    "file_path": await get_file_path(file_name=file_name, word_dir=work_dir),
                }
            )
        except Exception as e:
            logger.warning(f"Failed to download file {file_name}. Exception: {e}")
            file_paths.append(
                {
                    "file_name": os.path.basename(file_name),
                    "file_path": "",
                }
            )
    return file_paths


def _get_file_storage_target() -> str:
    """读取文件存储目标，既支持 HTTP 文件服务，也支持本地目录。"""
    storage_target = (os.getenv("FILE_SERVER_URL") or "").strip()
    if not storage_target:
        raise ValueError("FILE_SERVER_URL is not configured")
    return storage_target


def _is_http_endpoint(storage_target: str) -> bool:
    """判断当前文件存储目标是否为 HTTP 文件服务。"""
    lowered = storage_target.lower()
    return lowered.startswith("http://") or lowered.startswith("https://")


def _is_local_file_reference(file_name: str) -> bool:
    """判断字符串是否指向本地文件。兼容 Windows 盘符路径。"""
    if not file_name:
        return False
    if file_name.startswith("file://"):
        return True
    if file_name.startswith("/") or file_name.startswith("\\\\"):
        return True
    return len(file_name) >= 3 and file_name[1] == ":" and file_name[2] in {"\\", "/"}


def _normalize_local_path(file_name: str) -> Path:
    """将本地文件引用归一化为 Path。"""
    if file_name.startswith("file://"):
        normalized = file_name[len("file://"):]
        if normalized.startswith("/") and len(normalized) >= 3 and normalized[2] == ":":
            normalized = normalized[1:]
        return Path(normalized)
    return Path(file_name)


def _build_local_storage_path(storage_root: str, request_id: str, file_name: str) -> Path:
    """按 requestId 隔离本地产物目录，避免不同会话互相覆盖。"""
    target_directory = Path(storage_root).expanduser().resolve() / _sanitize_local_request_scope(request_id)
    target_directory.mkdir(parents=True, exist_ok=True)
    return target_directory / file_name


def _sanitize_local_request_scope(request_id: str) -> str:
    """将 requestId 转换为兼容本地文件系统的目录名，同时保留可读性。"""
    sanitized = _sanitize_local_path_segment(request_id, fallback="request")
    if sanitized == request_id:
        return sanitized
    digest = hashlib.md5(request_id.encode("utf-8")).hexdigest()[:8]
    return f"{sanitized}-{digest}"


def _sanitize_local_path_segment(segment: str, fallback: str) -> str:
    """清洗单个路径片段，兼容 Windows 非法字符与保留名称。"""
    invalid_chars = '<>:"/\\|?*'
    translated = "".join("_" if char in invalid_chars or ord(char) < 32 else char for char in segment)
    sanitized = translated.strip().rstrip(". ")
    if not sanitized or sanitized in {".", ".."}:
        sanitized = fallback

    reserved_names = {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    }
    if sanitized.upper() in reserved_names:
        sanitized = f"_{sanitized}"
    return sanitized[:120]


def _write_local_text_file(storage_root: str, request_id: str, file_name: str, content: str) -> Path:
    """将文本内容直接落到本地目录，模拟文件服务上传。"""
    target_file = _build_local_storage_path(storage_root, request_id, file_name)
    target_file.write_text(content, encoding="utf-8")
    return target_file


def _copy_file_to_local_storage(storage_root: str, request_id: str, source_file: Path) -> Path:
    """将已有文件复制到本地目录，模拟文件服务上传。"""
    target_file = _build_local_storage_path(storage_root, request_id, source_file.name)
    if source_file.resolve() != target_file.resolve():
        shutil.copy2(source_file, target_file)
    return target_file
