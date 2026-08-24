"""Platform dispatch for the authenticated social tool endpoints."""

from __future__ import annotations

from typing import Any

from . import reddit, twitter, xueqiu
from .common import SocialPlatformError, bounded_text, error_result


def execute_social(platform: str, params: dict[str, Any]) -> dict[str, Any]:
    normalized = bounded_text(platform, 30).casefold()
    operation = bounded_text(params.get("operation"), 40).casefold()
    try:
        if normalized == "twitter":
            return twitter.execute(params)
        if normalized == "reddit":
            return reddit.execute(params)
        if normalized == "xueqiu":
            return xueqiu.execute(params)
        raise SocialPlatformError("INVALID_INPUT", f"unsupported platform: {normalized}")
    except SocialPlatformError as exc:
        if exc.code == "NETWORK_TIMEOUT" and normalized == "twitter":
            error = SocialPlatformError(
                "NETWORK_TIMEOUT",
                "Twitter 网络请求超时，无法访问 x.com。请配置 "
                "REACTOR_TWITTER_PROXY（HTTP 或 SOCKS5）后重试。",
            )
            return error_result(normalized, operation, error)
        return error_result(normalized, operation, exc)
    except Exception:
        # Do not expose third-party exception text: it can contain request URLs
        # or implementation details that are not useful to the model.
        return error_result(
            normalized,
            operation,
            SocialPlatformError("UPSTREAM_ERROR", f"{normalized} request failed"),
        )
