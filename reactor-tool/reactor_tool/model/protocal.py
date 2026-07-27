# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
"""Java ↔ Python 工具服务请求/响应协议（Pydantic）。

字段多用 alias 兼容 Java 侧 camelCase；file_id 由 requestId+fileName 派生。
"""
import hashlib
import os


from typing import Dict, Optional, Literal, List, Any


from pydantic import BaseModel, Field, computed_field, ConfigDict, field_validator


class StreamMode(BaseModel):
    """SSE 流式节流策略。

    args:
        mode: general 原样流 / token 按 token 数缓冲 / time 按秒缓冲
        token: token 模式下每 N 个 token 刷一次
        time: time 模式下每 N 秒刷一次
    """
    mode: Literal["general", "token", "time"] = Field(default="general")
    token: Optional[int] = Field(default=5, ge=1)
    time: Optional[int] = Field(default=5, ge=1)


class CIRequest(BaseModel):
    """代码解释器 / 通用任务请求（report 等也继承此结构）。"""
    request_id: str = Field(alias="requestId", description="Request ID")
    task: Optional[str] = Field(default=None, description="Task")
    file_names: Optional[List[str]] = Field(default=[], alias="fileNames", description="输入的文件列表")
    file_name: Optional[str] = Field(default=None, alias="fileName", description="返回的生成的文件名称")
    file_description: Optional[str] = Field(default=None, alias="fileDescription", description="返回的生成的文件描述")
    permission_profile: Literal["analysis", "workspace"] = Field(
        default="analysis",
        alias="permissionProfile",
        description="代码解释器权限档位，默认 analysis",
    )
    stream: bool = True
    stream_mode: Optional[StreamMode] = Field(default=StreamMode(), alias="streamMode", description="流式模式")
    origin_file_names: Optional[List[dict]] = Field(default=None, alias="originFileNames", description="原始文本信息")


class ReportRequest(CIRequest):
    """报告生成请求：在 CIRequest 上增加输出格式与模板类型。"""
    file_type: Literal["html", "markdown", "ppt"] = Field("html", alias="fileType", description="生成报告的文件类型")
    template_type: str = Field(default="html", alias="templateType", description="生成报告的模板样式类型")


class FileRequest(BaseModel):
    """单文件定位：requestId + fileName → file_id。"""
    request_id: str = Field(alias="requestId", description="Request ID")
    file_name: str = Field(alias="fileName", description="文件名称")

    @computed_field
    def file_id(self) -> str:
        """派生逻辑 file_id（MD5）。"""
        return get_file_id(self.request_id, self.file_name)


def get_file_id(request_id: str, file_name: str) -> str:
    """新规则：仅用 basename 参与哈希，避免路径污染。"""
    normalized_file_name = os.path.basename((file_name or "").strip())
    return hashlib.md5((request_id + normalized_file_name).encode("utf-8")).hexdigest()


def get_legacy_file_id(request_id: str, file_name: str) -> str:
    """兼容历史 file_id 规则：直接使用原始 fileName 参与哈希。"""
    return hashlib.md5((request_id + (file_name or "").strip()).encode("utf-8")).hexdigest()


class FileListRequest(BaseModel):
    """列文件请求：可按 requestId 全量，或 filters 精确过滤。"""
    request_id: str = Field(alias="requestId", description="Request ID")
    filters: Optional[List[FileRequest]] = Field(default=None, description="过滤条件")
    page: int = 1
    page_size: int = Field(default=10, alias="pageSize", description="分页大小")


class FileUploadRequest(FileRequest):
    """JSON 文本上传：带 description 与 content 正文。"""
    description: str = Field(description="返回的生成的文件描述")
    content: str = Field(description="返回的生成的文件内容")


class FileRegisterRequest(FileRequest):
    """登记本地已有文件：不上传 content，只写元数据并指向 localPath。"""
    description: Optional[str] = Field(default="", description="文件描述（可含 workspace 相对路径）")
    local_path: str = Field(alias="localPath", description="本地已存在文件的绝对路径")


class DeepSearchRequest(BaseModel):
    """深度搜索请求：查询 + 引擎列表 + 最大循环轮次。"""
    request_id: str = Field(description="Request ID")
    query: str = Field(description="搜索查询")
    max_loop: Optional[int] = Field(default=1, alias="maxLoop", description="最大循环次数")

    # 可选引擎: ddg, bing, jina, sogou, serp, exa
    search_engines: List[str] = Field(default=[], description="使用哪些搜索引擎")

    stream: bool = Field(default=True, description="是否流式响应")
    stream_mode: Optional[StreamMode] = Field(default=StreamMode(), alias="streamMode", description="流式模式")


class WebFetchRequest(BaseModel):
    """单网页抓取请求。"""

    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId", description="Request ID")
    url: str = Field(description="需要抓取的网页 URL")
    timeout_seconds: int = Field(default=30, alias="timeoutSeconds", ge=5, le=300, description="下载超时时间，单位秒")

    @field_validator("request_id")
    @classmethod
    def validate_request_id(cls, value: str) -> str:
        normalized = value.strip() if value is not None else ""
        if not normalized:
            raise ValueError("requestId 不能为空")
        return normalized

    @field_validator("url")
    @classmethod
    def validate_url(cls, value: str) -> str:
        normalized = value.strip() if value is not None else ""
        if not normalized:
            raise ValueError("url 不能为空")
        lowered = normalized.lower()
        if not (lowered.startswith("http://") or lowered.startswith("https://")):
            raise ValueError("url 仅支持 http 或 https 协议")
        return normalized



class TableRAGRequest(BaseModel):
    """表结构/列值 RAG：结合 Qdrant 向量与 ES 关键词做 schema 召回。"""
    request_id: str = Field(alias="requestId", description="Request ID")
    query: str = Field(description="用户问题")
    current_date_info: str = Field(alias="currentDateInfo", description="系统当前日期")
    model_code_list: List = Field(alias="modelCodeList", description="表信息")
    schema_info: List = Field(alias="schemaInfo", description="字段信息")
    stream: bool = Field(alias="stream",  default=True, description="是否流式响应")
    use_vector: Optional[bool] = Field(default=False, alias="useVector", description="使用qdrant 进行向量检索")
    use_elastic: Optional[bool] = Field(default=False, alias="useElastic", description="使用es检索")
    recall_type: Optional[str] = Field(default="only_recall", alias="recallType", description="recallType 为only_recall 时仅进行粗排")


class CalEngineRequest(BaseModel):
    """指标计算公式生成：基于已取数结果与用户 query。"""
    request_id: str = Field(description="Request ID")
    query: str = Field(description="用户取数查询")
    data: List[Dict] = Field(description="用户取数数据")


class AutoAnalysisRequest(BaseModel):
    """自动多步数据分析请求。"""
    request_id: str = Field(description="Request ID")
    task: str = Field(description="分析任务，请提供完整的分析任务，保持用户的原始语义，不要串改、引申")
    modelCodeList: List[str] = Field(description="数据模型 id，标识数据源")
    businessKnowledge: Optional[str] = Field(None, description="分析任务需要的业务知识，包括相关的分析维度、分析指标和指标计算公式、业务逻辑等")

    max_steps: Optional[int] = Field(10, description="最大分析步骤数")
    stream: bool = Field(default=True, description="是否流式返回")


class NL2SQLRequest(BaseModel):
    """自然语言转 SQL 请求（含方言与 schema）。"""
    request_id: str = Field(alias="requestId", description="Request ID")
    query: str = Field(description="用户问题")
    current_date_info: str = Field(alias="currentDateInfo", description="系统当前日期")
    table_id_list: List[str] = Field(alias="modelCodeList", description="表信息")
    column_info: List[Dict] = Field(alias="schemaInfo", description="字段信息")
    stream: bool = Field(alias="stream",  default=True, description="是否流式响应")
    dialect: str = Field(alias="dbType",  default="mysql", description="SQL方言类型")


class SopChooseRequest(BaseModel):
    """从候选 SOP 列表中按 query 语义择优召回。"""
    request_id: str = Field(alias="requestId", description="Request ID")
    query: str = Field(description="用户问题")
    sop_list: Optional[List[Dict]] = Field(default=[],
        alias="sopList", description="SOP 列表，包含每一个sop")


class ScriptRunnerFileInfo(BaseModel):
    """脚本执行产物信息"""

    model_config = ConfigDict(populate_by_name=True)

    file_name: str = Field(alias="fileName", description="文件名称")
    oss_url: Optional[str] = Field(default=None, alias="ossUrl", description="对象存储地址")
    domain_url: Optional[str] = Field(default=None, alias="domainUrl", description="可访问地址")
    download_url: Optional[str] = Field(default=None, alias="downloadUrl", description="下载地址")
    file_size: Optional[int] = Field(default=0, alias="fileSize", description="文件大小")


class ScriptRunnerRequest(BaseModel):
    """script_runner 请求协议"""

    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId", description="Request ID")
    skill_name: str = Field(alias="skillName", description="skill 名称")
    skill_base_path: str = Field(alias="skillBasePath", description="skill 根目录")
    script_name: str = Field(alias="scriptName", description="脚本名称")
    script_path: str = Field(alias="scriptPath", description="脚本相对路径")
    runtime: Literal["python", "node", "shell", "powershell", "bat"] = Field(description="脚本运行时")
    arguments: Dict[str, Any] = Field(default_factory=dict, description="结构化参数")
    argv: List[str] = Field(default_factory=list, description="原始命令行参数")
    timeout_seconds: int = Field(default=120, alias="timeoutSeconds", description="超时时间，单位秒")


class ScriptRunnerResponse(BaseModel):
    """script_runner 返回协议"""

    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId", description="Request ID")
    skill_name: str = Field(alias="skillName", description="skill 名称")
    script_name: str = Field(alias="scriptName", description="脚本名称")
    runtime: Literal["python", "node", "shell", "powershell", "bat"] = Field(description="脚本运行时")
    success: bool = Field(description="是否执行成功")
    exit_code: int = Field(alias="exitCode", description="进程退出码")
    stdout: str = Field(default="", description="标准输出")
    stderr: str = Field(default="", description="错误输出")
    summary: str = Field(default="", description="执行摘要")
    file_info: List[ScriptRunnerFileInfo] = Field(default_factory=list, alias="fileInfo", description="产出文件")

class MultimodalRAGRequest(BaseModel):
    """MRAG 查询请求"""

    question: str = Field(default="", min_length=1, description="文本检索问题")
    image_urls: List[str] = Field(default_factory=list, description="图片 URL 列表")
    kb_id: Optional[str] = Field(default="", description="知识库 ID，缺省时回退默认知识库")
    kb_ids: List[str] = Field(default_factory=list, description="知识库 ID 列表，非空时优先于 kb_id")
    session_id: Optional[str] = Field(default="", description="MRAG 会话 ID")

    @field_validator("question")
    @classmethod
    def validate_question(cls, value: str) -> str:
        normalized = value.strip() if value is not None else ""
        if not normalized:
            raise ValueError("question 不能为空")
        return normalized

    @field_validator("kb_id", mode="before")
    @classmethod
    def normalize_kb_id(cls, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    @field_validator("kb_ids", mode="before")
    @classmethod
    def normalize_kb_ids(cls, value: Any) -> List[str]:
        if value is None:
            return []
        if isinstance(value, str):
            return [item.strip() for item in value.replace("，", ",").split(",") if item.strip()]
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        return []

    @field_validator("session_id", mode="before")
    @classmethod
    def normalize_session_id(cls, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    def resolve_kb_scope(self, default_kb_id: str) -> str | List[str]:
        """优先使用显式多选知识库，其次兼容旧单库字段。"""
        if self.kb_ids:
            return self.kb_ids
        if self.kb_id:
            return self.kb_id
        return default_kb_id.strip()


class EmbeddingProxyRequest(BaseModel):
    """共享文本向量代理请求"""

    inputs: List[str] = Field(min_length=1, description="需要批量向量化的文本列表")
    normalize: bool = Field(default=True, description="是否执行 L2 归一化")

    @field_validator("inputs")
    @classmethod
    def validate_inputs(cls, value: List[str]) -> List[str]:
        normalized_inputs = []
        for item in value or []:
            normalized = item.strip() if item is not None else ""
            if not normalized:
                raise ValueError("inputs 中不能包含空字符串")
            normalized_inputs.append(normalized)
        if not normalized_inputs:
            raise ValueError("inputs 不能为空")
        return normalized_inputs


class EmbeddingProxyResponse(BaseModel):
    """共享文本向量代理返回"""

    vectors: List[List[float]] = Field(default_factory=list, description="批量向量结果")
    dimension: Optional[int] = Field(default=None, description="向量维度")
    model: Optional[str] = Field(default=None, description="实际使用的模型名称")
