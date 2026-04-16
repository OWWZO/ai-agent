# -*- coding: utf-8 -*-
"""
skill 脚本执行入口。
"""
from loguru import logger

from reactor_tool.model.protocal import (
    ScriptRunnerFileInfo,
    ScriptRunnerRequest,
    ScriptRunnerResponse,
)
from reactor_tool.tool.script_runtime import (
    cleanup_workspace,
    collect_generated_files,
    execute_script,
    prepare_workspace,
    resolve_script_path,
    resolve_workspace_script,
)
from reactor_tool.util.file_util import upload_file_by_path
from reactor_tool.util.log_util import timer


@timer()
async def run_script_request(body: ScriptRunnerRequest) -> ScriptRunnerResponse:
    """执行 skill 脚本，并将生成产物上传到现有文件服务。"""
    prepared_workspace = None
    try:
        resolve_script_path(body.skill_base_path, body.script_path)
        prepared_workspace = prepare_workspace(body.skill_base_path, body.arguments)
        workspace_script_path = resolve_workspace_script(prepared_workspace, body.script_path)

        execution_result = await execute_script(
            runtime=body.runtime,
            script_path=workspace_script_path,
            working_directory=prepared_workspace.skill_root,
            arguments=body.arguments,
            arguments_file=prepared_workspace.arguments_file,
            argv=body.argv,
            timeout_seconds=body.timeout_seconds,
        )

        file_info = []
        upload_errors = []
        for generated_file in collect_generated_files(prepared_workspace):
            try:
                uploaded_file = await upload_file_by_path(
                    file_path=str(generated_file),
                    request_id=body.request_id,
                )
                if uploaded_file:
                    file_info.append(ScriptRunnerFileInfo.model_validate(uploaded_file))
            except Exception as exc:
                logger.warning(
                    "[script_runner] request_id={} skill={} script={} upload failed, file={}, error={}",
                    body.request_id,
                    body.skill_name,
                    body.script_name,
                    generated_file,
                    exc,
                )
                upload_errors.append(f"{generated_file.name}: {exc}")

        response_stderr = execution_result.stderr
        response_summary = execution_result.summary
        if upload_errors:
            # 上传产物属于附加能力，不应该覆盖脚本本身的执行结果。
            upload_warning = "产物上传失败: " + "; ".join(upload_errors)
            response_stderr = _merge_messages(response_stderr, upload_warning)
            if execution_result.success:
                response_summary = "脚本执行成功（产物上传失败）"

        response = ScriptRunnerResponse(
            request_id=body.request_id,
            skill_name=body.skill_name,
            script_name=body.script_name,
            runtime=body.runtime,
            success=execution_result.success,
            exit_code=execution_result.exit_code,
            stdout=execution_result.stdout,
            stderr=response_stderr,
            summary=response_summary,
            file_info=file_info,
        )
        logger.info(
            "[script_runner] request_id={} skill={} script={} success={} exit_code={} files={}",
            body.request_id,
            body.skill_name,
            body.script_name,
            response.success,
            response.exit_code,
            len(response.file_info),
        )
        return response
    except Exception as exc:
        logger.exception(
            "[script_runner] request_id={} skill={} script={} rejected, type={}, repr={}",
            body.request_id,
            body.skill_name,
            body.script_name,
            type(exc).__name__,
            repr(exc),
        )
        return ScriptRunnerResponse(
            request_id=body.request_id,
            skill_name=body.skill_name,
            script_name=body.script_name,
            runtime=body.runtime,
            success=False,
            exit_code=-1,
            stdout="",
            stderr=_format_exception_message(exc),
            summary="脚本执行被拒绝",
            file_info=[],
        )
    finally:
        cleanup_workspace(prepared_workspace)


def _merge_messages(base_message: str, appended_message: str) -> str:
    """合并 stderr 文本，避免出现多余空行。"""
    if not base_message:
        return appended_message
    if not appended_message:
        return base_message
    return f"{base_message.rstrip()}\n{appended_message}"


def _format_exception_message(exc: Exception) -> str:
    """把空异常补成可读文本，避免前端只看到空 rejected。"""
    message = str(exc).strip()
    if message:
        return message
    return f"{type(exc).__name__}: {repr(exc)}"
