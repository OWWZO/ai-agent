"""Compatibility runner for twitter-cli versions behind X's search API."""

from __future__ import annotations

import re
import sys
import time


_SEARCH_QUERY_ID_PATTERN = re.compile(
    r"queryId\s*:\s*[\"']([A-Za-z0-9_-]+)[\"']"
    r"[^}]{0,200}operationName\s*:\s*[\"']SearchTimeline[\"']"
)
_SCRIPT_URL_PATTERN = re.compile(
    r"(?:src|href)=[\"']"
    r"(https://abs\.twimg\.com/responsive-web/client-web[^\"']+\.js)"
    r"[\"']"
)
_QUERY_ID_FETCH_MAX_RETRIES = 2
_QUERY_ID_FETCH_BASE_DELAY_SEC = 0.5


def _extract_search_query_id(bundle: str) -> str | None:
    match = _SEARCH_QUERY_ID_PATTERN.search(bundle or "")
    return match.group(1) if match else None


def _authenticated_url_fetch(url: str, headers: dict[str, str] | None = None) -> str:
    import os

    from twitter_cli.client import _get_cffi_session
    from twitter_cli.constants import get_user_agent

    request_headers = {"User-Agent": get_user_agent()}
    auth_token = os.getenv("TWITTER_AUTH_TOKEN", "").strip()
    ct0 = os.getenv("TWITTER_CT0", "").strip()
    if auth_token and ct0:
        request_headers["Cookie"] = f"auth_token={auth_token}; ct0={ct0}"
    request_headers.update(headers or {})
    response = _get_cffi_session().get(url, headers=request_headers, timeout=20)
    response.raise_for_status()
    return response.text


def _fetch_with_retry(url: str) -> str:
    for attempt in range(_QUERY_ID_FETCH_MAX_RETRIES + 1):
        try:
            return _authenticated_url_fetch(url)
        except Exception:
            if attempt >= _QUERY_ID_FETCH_MAX_RETRIES:
                raise
            time.sleep(_QUERY_ID_FETCH_BASE_DELAY_SEC * (2**attempt))
    raise RuntimeError("query ID fetch retry loop ended unexpectedly")


def _prepare_twitter_cli() -> None:
    from twitter_cli import client, graphql

    # X's homepage no longer exposes the legacy ondemand.s marker. The header
    # is optional for read requests, so avoid failing every CLI construction.
    client.TwitterClient._ensure_client_transaction = lambda self: None

    original_graphql_get = client.TwitterClient._graphql_get

    def graphql_get_compat(self, operation_name, variables, features, *args, **kwargs):
        if operation_name == "SearchTimeline":
            return self._graphql_post(operation_name, variables, features)
        return original_graphql_get(
            self, operation_name, variables, features, *args, **kwargs
        )

    client.TwitterClient._graphql_get = graphql_get_compat

    if len(sys.argv) < 2 or sys.argv[1] != "search":
        return

    homepage = _fetch_with_retry("https://x.com/home")
    for script_url in _SCRIPT_URL_PATTERN.findall(homepage):
        try:
            query_id = _extract_search_query_id(_fetch_with_retry(script_url))
        except Exception:
            continue
        if query_id:
            graphql._cached_query_ids["SearchTimeline"] = query_id
            return
    raise RuntimeError("could not resolve SearchTimeline query ID from X homepage")


def main() -> None:
    _prepare_twitter_cli()
    from twitter_cli.cli import cli

    cli()


if __name__ == "__main__":
    main()
