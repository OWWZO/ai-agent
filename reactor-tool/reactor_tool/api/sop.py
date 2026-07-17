# -*- coding: utf-8 -*-
"""SOP 工作台 API：list / get / upsert / delete / status / recall_test。"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from reactor_tool.tool.sop_workspace import SopWorkspaceService

router = APIRouter(prefix="/sop", tags=["sop"])


class SopStepModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    title: str = Field(default="", description="步骤标题")
    steps: List[str] = Field(default_factory=list, description="子步骤")


class SopListRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(default="sop-list", alias="requestId")
    keyword: str = Field(default="")
    status: Optional[str] = Field(default=None)
    limit: int = Field(default=200, ge=1, le=500)


class SopGetRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(default="sop-get", alias="requestId")
    sop_id: str = Field(alias="sopId")


class SopUpsertRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(default="sop-upsert", alias="requestId")
    sop_id: Optional[str] = Field(default=None, alias="sopId")
    sop_name: str = Field(alias="sopName")
    sop_desc: str = Field(default="", alias="sopDesc")
    sop_type: str = Field(default="list", alias="sopType")
    sop_steps: List[SopStepModel] = Field(default_factory=list, alias="sopSteps")
    status: str = Field(default="online")


class SopDeleteRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(default="sop-delete", alias="requestId")
    sop_id: str = Field(alias="sopId")


class SopStatusRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(default="sop-status", alias="requestId")
    sop_id: str = Field(alias="sopId")
    status: str = Field(description="online | offline | draft")


class SopRecallTestRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(default="sop-recall-test", alias="requestId")
    query: str = Field(description="测试 query")


def _ok(data: Any, request_id: str) -> Dict[str, Any]:
    return {"code": 200, "data": data, "requestId": request_id}


def _service(request_id: str) -> SopWorkspaceService:
    return SopWorkspaceService(request_id=request_id)


@router.post("/list")
async def list_sops(body: SopListRequest):
    try:
        items = _service(body.request_id).list_sops(
            keyword=body.keyword,
            status=body.status,
            limit=body.limit,
        )
        return _ok({"list": items}, body.request_id)
    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@router.post("/get")
async def get_sop(body: SopGetRequest):
    try:
        item = _service(body.request_id).get_sop(body.sop_id)
        if not item:
            raise HTTPException(status_code=404, detail=f"SOP 不存在: {body.sop_id}")
        return _ok(item, body.request_id)
    except HTTPException:
        raise
    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@router.post("/upsert")
async def upsert_sop(body: SopUpsertRequest):
    try:
        item = _service(body.request_id).upsert_sop(
            sop_id=body.sop_id,
            sop_name=body.sop_name,
            sop_desc=body.sop_desc,
            sop_type=body.sop_type,
            sop_steps=[step.model_dump() for step in body.sop_steps],
            status=body.status,
        )
        return _ok(item, body.request_id)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@router.post("/delete")
async def delete_sop(body: SopDeleteRequest):
    try:
        _service(body.request_id).delete_sop(body.sop_id)
        return _ok({"sopId": body.sop_id, "deleted": True}, body.request_id)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@router.post("/status")
async def set_sop_status(body: SopStatusRequest):
    try:
        item = _service(body.request_id).set_status(body.sop_id, body.status)
        return _ok(item, body.request_id)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@router.post("/recall_test")
async def recall_test(body: SopRecallTestRequest):
    if not (body.query or "").strip():
        raise HTTPException(status_code=400, detail="query 不能为空")
    try:
        result = _service(body.request_id).recall_test(body.query.strip())
        return _ok(result, body.request_id)
    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error)) from error
