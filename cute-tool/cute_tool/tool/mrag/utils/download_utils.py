# -*- coding: utf-8 -*-
"""HTTP 文件下载到本地路径。"""
import requests

from .logger_utils import logger


def download_file(
        url: str,
        filename: str
):
    """流式下载 url 到 filename；非 200 仅打日志不抛异常。"""
    logger.info(f"Downloading {url} -> {filename}")
    response = requests.get(url, stream=True, verify=False, timeout=300)
    if response.status_code == 200:
        with open(filename, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
        logger.info(f"Downloaded {filename}")
    else:
        logger.warning(f"Failed to download {filename}: HTTP {response.status_code}")
