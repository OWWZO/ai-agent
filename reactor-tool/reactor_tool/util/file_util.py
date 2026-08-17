# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
"""文件读写与产物上传工具。

支持本地路径 / HTTP 文件服务两种源；上传可走 HTTP 端点或本地存储根目录。
"""
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
    """读取文件正文：本地路径直接读，否则按 URL 从文件服务下载。"""
    # 工具调用既可能拿到工作区路径，也可能拿到文件服务 URL；先判定来源，避免把本地路径当成 HTTP 地址。
    if _is_local_file_reference(file_name):
        local_path = _normalize_local_path(file_name)
        try:
            with open(local_path, "r", encoding='utf-8') as rf:
                return rf.read()
        except UnicodeDecodeError:
            # UTF-8失败时尝试GBK
            with open(local_path, "r", encoding='gbk') as rf:
                return rf.read()
    # 远端响应按块读取，统一在内存中解码；UTF-8 失败时兼容历史中文文件常见的 GBK 编码。
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
    """批量下载文件内容，单文件失败时写入占位文案，不中断整体。"""
    file_contents = []
    for file_name in file_names:
        # 单个附件不可用不应吞掉整个请求，调用方仍能看到原文件名和失败占位内容。
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
    """按字符近似 token，截断文件列表以适配模型上下文窗口。"""
    truncated_files = []
    token_size = 0
    for f_a in files:
        # 深拷贝后再截断，避免上下文裁剪反向修改会话中仍需复用的原始文档。
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
    """上传文本产物：HTTP 调文件服务，或写入本地 FILE_STORAGE 根目录。"""
    if file_type == "markdown":
        file_type = "md"
    if not file_name.endswith(file_type):
        file_name = f"{file_name}.{file_type}"
    storage_target = _get_file_storage_target()
    if _is_http_endpoint(storage_target):
        # 生产环境由文件服务生成稳定 URL；本地模式只返回可直接访问的落盘路径，保持同一响应结构。
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
    """按本地路径上传二进制产物（multipart 或拷贝到本地存储根）。"""
    if not os.path.exists(file_path):
        return None
    file_name = os.path.basename(file_path)
    file_size = os.path.getsize(file_path)
    storage_target = _get_file_storage_target()
    if _is_http_endpoint(storage_target):
        # 二进制产物必须走 multipart；文件服务负责登记元数据并返回前端可消费的地址。
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
    """密码学安全的字母数字随机串。"""
    characters = string.ascii_letters + string.digits
    secure_random = secrets.SystemRandom()
    return "".join(secure_random.choice(characters) for _ in range(length))


def flatten_search_file(s_file: Dict[str, Any]) -> List[Dict[str, Any]]:
    """展开搜索结果文件 JSON 为扁平文档列表。"""
    flat_files = []
    try:
        contents = json.loads(s_file["content"])
        for k, v in contents.items():
            flat_files.extend(v)
    except Exception as e:
        logger.warning(f"parser file error: {e}")
    return flat_files


@timer()
async def get_file_path(
    file_name: str,
    word_dir: str,
    workspace_root: str | None = None,
) -> str | None:
    """解析输入文件路径：绝对本地路径 / 工作区相对名 / HTTP(S) URL。"""
    name = (file_name or "").strip()
    if not name:
        return None

    if _is_local_file_reference(name):
        # 本地引用已经是可用路径，不重复复制，避免同一文件在工作区产生无意义副本。
        return str(_normalize_local_path(name))

    # 裸文件名或相对路径：先在会话工作区 / input 落点解析，避免被误当成 HTTP URL。
    local_hit = _resolve_workspace_relative_file(
        name,
        workspace_root=workspace_root,
        work_dir=word_dir,
    )
    if local_hit is not None:
        return str(local_hit)

    if not _looks_like_http_url(name):
        print(f"下载文件失败: {name}（工作区未找到，且不是可下载 URL）")
        logger.warning(
            "Input file not found in workspace and not a downloadable URL: {}",
            name,
        )
        return None

    # 远端文件只取 basename 写入调用方工作目录，避免 URL 中的路径层级改变落盘位置。
    b_content = b""
    file_path = os.path.join(word_dir, os.path.basename(name.split("?", 1)[0]))
    async with aiohttp.ClientSession() as session:
        try:
            async with session.get(name, timeout=99999) as response:
                response.raise_for_status()
                while True:
                    chunk = await response.content.read(1024)
                    if not chunk:
                        break
                    b_content += chunk
        except aiohttp.ClientError as e:
            print(f"下载文件失败: {name} ({e})")
            return None
        except TimeoutError:
            print(f"下载文件超时: {name}")
            return ""
    with open(file_path, "wb") as f:
        f.write(b_content)
    return file_path


@timer()
async def download_all_files_in_path(
    file_names: list[str],
    work_dir: str,
    workspace_root: str | None = None,
) -> List[Dict[str, Any]]:
    file_paths = []
    for file_name in file_names or []:
        # 保持输入顺序并逐项记录失败，权限策略可以据此只允许访问成功落盘的文件。
        try:
            resolved = await get_file_path(
                file_name=file_name,
                word_dir=work_dir,
                workspace_root=workspace_root,
            )
            file_paths.append(
                {
                    "file_name": os.path.basename(str(file_name).split("?", 1)[0]),
                    "file_path": resolved or "",
                }
            )
        except Exception as e:
            logger.warning(f"Failed to download file {file_name}. Exception: {e}")
            file_paths.append(
                {
                    "file_name": os.path.basename(str(file_name).split("?", 1)[0]),
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


def _looks_like_http_url(file_name: str) -> bool:
    """仅 http(s) 视为远端下载；裸文件名不得走 HTTP。"""
    lowered = (file_name or "").strip().lower()
    return lowered.startswith("http://") or lowered.startswith("https://")


def _resolve_workspace_relative_file(
    file_name: str,
    *,
    workspace_root: str | None,
    work_dir: str | None,
) -> Path | None:
    """在会话工作区解析相对/裸文件名；禁止 .. 穿越出根目录。"""
    name = (file_name or "").strip().replace("\\", "/")
    if not name or name.startswith("/") or _looks_like_http_url(name) or _is_local_file_reference(name):
        return None

    roots: list[Path] = []
    for raw in (workspace_root, work_dir):
        text = (raw or "").strip()
        if not text:
            continue
        try:
            root = Path(text).expanduser().resolve()
        except OSError:
            continue
        if root not in roots:
            roots.append(root)

    basename = Path(name).name
    for root in roots:
        candidates = [root / name]
        if basename and basename != name:
            candidates.append(root / basename)
        candidates.append(root / "input" / basename)
        if work_dir:
            try:
                wd = Path(work_dir).expanduser().resolve()
            except OSError:
                wd = None
            if wd is not None and wd != root:
                candidates.append(wd / basename)
                candidates.append(wd / name)

        seen: set[Path] = set()
        for candidate in candidates:
            try:
                resolved = candidate.resolve()
            except OSError:
                continue
            if resolved in seen:
                continue
            seen.add(resolved)
            try:
                resolved.relative_to(root)
            except ValueError:
                # 候选可能落在 work_dir（input）而非 workspace 根；再对 work_dir 做 containment。
                if work_dir:
                    try:
                        wd = Path(work_dir).expanduser().resolve()
                        resolved.relative_to(wd)
                    except (OSError, ValueError):
                        continue
                else:
                    continue
            if resolved.is_file():
                return resolved
    return None


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
    # requestId 只参与目录层级，文件名由调用方先完成扩展名/路径规范化。
    target_directory = Path(storage_root).expanduser().resolve() / _sanitize_local_request_scope(request_id)
    target_directory.mkdir(parents=True, exist_ok=True)
    return target_directory / file_name


def _sanitize_local_request_scope(request_id: str) -> str:
    """将 requestId 转换为兼容本地文件系统的目录名，同时保留可读性。"""
    sanitized = _sanitize_local_path_segment(request_id, fallback="request")
    if sanitized == request_id:
        return sanitized
    # 清洗可能让不同 requestId 变成同名目录，追加摘要保留可读名并降低碰撞风险。
    digest = hashlib.md5(request_id.encode("utf-8")).hexdigest()[:8]
    return f"{sanitized}-{digest}"


def _sanitize_local_path_segment(segment: str, fallback: str) -> str:
    """清洗单个路径片段，兼容 Windows 非法字符与保留名称。"""
    # 这里处理的是目录片段而不是完整路径，禁止分隔符和 Windows 保留设备名穿透目录边界。
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
