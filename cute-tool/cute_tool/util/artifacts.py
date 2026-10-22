# -*- coding: utf-8 -*-
"""产物上传 seam。

仓库里有 7 处各自实现的"上传产物 → 收集 fileInfo"循环，失败策略分三种：

- ``raise``：上传异常直接抛给调用方（历史路径没有人真的这样用）。
- ``swallow``：异常吞掉并记日志，``file_info`` 只装成功的（
  ``direct_code_execution``、``docgen._upload_result``、``docread``、``dataprep``）。
- ``warn``：异常吞掉但额外把 ``"文件名: 错误"`` 追加到 ``errors`` 列表，
  由调用方合并进 stderr / summary（``script_runner``）。

:func:`upload_artifacts` 覆盖后两种；``on_error`` 决定收集哪些信息。
"""

from pathlib import Path
from typing import Any, Iterable

from loguru import logger

from cute_tool.util.file_util import upload_file_by_path


async def upload_artifacts(
    paths: Iterable[Any],
    request_id: str,
    *,
    on_error: str = "swallow",
    log_label: str = "upload",
) -> tuple[list[dict[str, Any]], list[str]]:
    """逐个上传本地产物，返回 ``(file_info, errors)``。

    - 文件不存在或上传返回空 → 两者都跳过（``upload_file_by_path`` 自己保证
      "不存在返回 None"）。
    - ``on_error="swallow"``：异常只记 warning，``errors`` 为空。
    - ``on_error="warn"``：异常同时写入 ``errors``，元素形如 ``"{文件名}: {错误}"``，
      与 ``script_runner`` 原有的 stderr 提示格式一致。

    任意单个文件失败都不影响其余文件和调用方主结果，与原有各处行为一致。
    """
    file_info: list[dict[str, Any]] = []
    errors: list[str] = []
    for raw_path in paths:
        path_str = str(getattr(raw_path, "file_path", raw_path) or "")
        if not path_str:
            continue
        try:
            uploaded = await upload_file_by_path(path_str, request_id=request_id)
            if uploaded:
                file_info.append(uploaded)
        except Exception as exc:  # noqa: BLE001 — 上传是增强能力，不能覆盖主结果
            logger.warning(
                "[{}] upload failed path={} err={}", log_label, path_str, exc
            )
            if on_error == "warn":
                errors.append(f"{Path(path_str).name}: {exc}")
    return file_info, errors
