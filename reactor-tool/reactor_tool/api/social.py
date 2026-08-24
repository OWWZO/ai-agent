"""HTTP routes for authenticated, read-only social platform tools."""

from __future__ import annotations

import asyncio
from typing import Optional

from fastapi import APIRouter
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from loguru import logger

from reactor_tool.tool import social as social_tools
from reactor_tool.util.middleware_util import RequestHandlerRoute


router = APIRouter(route_class=RequestHandlerRoute)


class SocialToolRequest(BaseModel):
    """Login-backed platform request; credentials stay in the Python environment."""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    request_id: str = Field(alias="requestId", min_length=1)
    operation: str = Field(min_length=1, max_length=40)
    query: Optional[str] = None
    target: Optional[str] = None
    tweet_id: Optional[str] = Field(default=None, alias="tweetId")
    username: Optional[str] = None
    subreddit: Optional[str] = None
    post_id: Optional[str] = Field(default=None, alias="postId")
    symbol: Optional[str] = None
    sort: Optional[str] = None
    time_filter: Optional[str] = Field(default=None, alias="timeFilter")
    stock_type: int = Field(default=10, alias="stockType", ge=1, le=100)
    limit: int = Field(default=10, ge=1, le=100)


async def _post_social(platform: str, body: SocialToolRequest):
    try:
        params = body.model_dump(by_alias=False, exclude_none=True)
        result = await asyncio.to_thread(social_tools.execute_social, platform, params)
        return {"code": 200, "data": result, "requestId": body.request_id}
    except Exception:
        logger.exception("{} social tool failed", platform)
        return JSONResponse(
            status_code=502,
            content={
                "code": 502,
                "message": f"{platform} tool failed",
                "requestId": body.request_id,
            },
        )


@router.post("/twitter")
async def post_twitter(body: SocialToolRequest):
    """Read Twitter/X with credentials configured in reactor-tool."""
    return await _post_social("twitter", body)


@router.post("/reddit")
async def post_reddit(body: SocialToolRequest):
    """Read Reddit with the in-process cookie client."""
    return await _post_social("reddit", body)


@router.post("/xueqiu")
async def post_xueqiu(body: SocialToolRequest):
    """Read Xueqiu with the in-process Cookie HTTP client."""
    return await _post_social("xueqiu", body)
