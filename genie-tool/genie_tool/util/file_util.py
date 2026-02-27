# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
import secrets
import string
import json
import os
from copy import deepcopy
from typing import List, Dict, Any

import aiohttp
from loguru import logger

from genie_tool.util.log_util import timer
from genie_tool.model.document import Doc


@timer()
async def get_file_content(file_name: str) -> str:
    # local file
    if file_name.startswith("/"):
        try:
            with open(file_name, "r", encoding='utf-8') as rf:
                return rf.read()
        except UnicodeDecodeError:
            # UTF-8失败时尝试GBK
            with open(file_name, "r", encoding='gbk') as rf:
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
    body = {
        "requestId": request_id,
        "fileName": file_name,
        "content": content,
        "description": content[:200],
    }
    async with aiohttp.ClientSession() as session:
        async with session.post(
            f"{os.getenv('FILE_SERVER_URL')}/upload_file", json=body, timeout=99999
        ) as response:
            result = json.loads(await response.text())
    return {
        "fileName": file_name,
        "ossUrl": result["downloadUrl"],
        "domainUrl": result["domainUrl"],
        "downloadUrl": result["downloadUrl"],
        "fileSize": len(content),
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
            f"{os.getenv('FILE_SERVER_URL')}/upload_file_data", data=data, timeout=99999
        ) as response:
            result = json.loads(await response.text())
    return {
        "fileName": file_name,
        "domainUrl": result["domainUrl"],
        "downloadUrl": result["downloadUrl"],
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
    if file_name.startswith("/"):
        return file_name
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
