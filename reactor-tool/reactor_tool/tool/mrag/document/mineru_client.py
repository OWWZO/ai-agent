# -*- coding: utf-8 -*-
from __future__ import annotations

import os
import time
from dataclasses import dataclass

import requests


def _normalize_mineru_api_base_url(api_base_url: str | None) -> str:
    """兼容历史的 MINERU_BASE_URL=/extract/task 配置，统一转成 /api/v4 根路径。"""
    candidate = (api_base_url or "").strip().rstrip("/")
    if not candidate:
        return ""

    if candidate.endswith("/extract/task"):
        return candidate[: -len("/extract/task")]

    if "/extract-results/batch/" in candidate:
        return candidate.split("/extract-results/batch/")[0]

    return candidate


@dataclass(frozen=True)
class MinerUUploadTarget:
    batch_id: str
    upload_url: str
    data_id: str
    file_name: str


class MinerUClient:
    """MinerU 官方托管上传客户端。"""

    def __init__(self, api_key: str | None = None, api_base_url: str | None = None):
        resolved_api_base_url = api_base_url or os.getenv("MINERU_API_BASE_URL") or os.getenv("MINERU_BASE_URL")
        self.api_key = (api_key or os.getenv("MINERU_API_KEY") or "").strip()
        self.api_base_url = _normalize_mineru_api_base_url(resolved_api_base_url)
        self.timeout = int(os.getenv("API_TIMEOUT", 300))

        self.headers = {
            "Authorization": f"Bearer {self.api_key}",
        }

    def prepare_file_upload(self, file_name: str, data_id: str, model_version: str = "vlm") -> MinerUUploadTarget:
        """向 MinerU 申请单文件上传地址。"""
        response = requests.post(
            f"{self.api_base_url}/file-urls/batch",
            headers={**self.headers, "Content-Type": "application/json"},
            json={
                "model_version": model_version,
                "files": [
                    {
                        "name": file_name,
                        "data_id": data_id,
                    }
                ],
            },
            timeout=self.timeout,
        )
        response.raise_for_status()

        payload = response.json()
        if payload.get("code") not in (0, 200, None):
            raise RuntimeError(f"MinerU batch upload prepare failed: {payload}")

        data = payload.get("data") or {}
        batch_id = data.get("batch_id")
        file_urls = data.get("file_urls") or []
        if not batch_id or not file_urls:
            raise RuntimeError(f"MinerU batch upload prepare returned incomplete data: {payload}")

        return MinerUUploadTarget(
            batch_id=batch_id,
            upload_url=file_urls[0],
            data_id=data_id,
            file_name=file_name,
        )

    def upload_file(self, upload_url: str, local_file_path: str) -> None:
        """把本地文件 PUT 到 MinerU 返回的签名地址。"""
        with open(local_file_path, "rb") as file_obj:
            response = requests.put(upload_url, data=file_obj, timeout=self.timeout)
        response.raise_for_status()

    def wait_batch_result(
            self,
            batch_id: str,
            file_name: str,
            data_id: str | None = None,
            timeout_seconds: int = 300,
            poll_interval_seconds: int = 5,
    ) -> str:
        """轮询批量任务结果，返回当前文件对应的 full_zip_url。"""
        deadline = time.time() + timeout_seconds
        while time.time() <= deadline:
            response = requests.get(
                f"{self.api_base_url}/extract-results/batch/{batch_id}",
                headers=self.headers,
                timeout=self.timeout,
            )
            response.raise_for_status()

            payload = response.json()
            if payload.get("code") not in (0, 200, None):
                raise RuntimeError(f"MinerU batch result request failed: {payload}")

            extract_results = (payload.get("data") or {}).get("extract_result") or []
            for item in extract_results:
                item_data_id = item.get("data_id")
                item_file_name = item.get("file_name")
                if data_id and item_data_id != data_id:
                    continue
                if not data_id and item_file_name != file_name:
                    continue

                state = (item.get("state") or item.get("status") or "").lower()
                if state in {"done", "success", "completed"}:
                    full_zip_url = item.get("full_zip_url")
                    if not full_zip_url:
                        raise RuntimeError(f"MinerU batch result missing full_zip_url: {item}")
                    return full_zip_url

                if state in {"failed", "error"}:
                    raise RuntimeError(item.get("err_msg") or item.get("message") or f"MinerU batch parse failed: {item}")

            time.sleep(poll_interval_seconds)

        raise TimeoutError(f"Timed out waiting for MinerU batch result: batch_id={batch_id}, file_name={file_name}")
