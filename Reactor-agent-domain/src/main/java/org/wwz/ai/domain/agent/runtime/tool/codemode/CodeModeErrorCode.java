package org.wwz.ai.domain.agent.runtime.tool.codemode;

/**
 * Code Mode 错误码。
 * <p>
 * 统一前缀 {@code CODE_MODE_}，便于前端和账本按前缀归类；
 * 新增错误码必须同步到本枚举，不要在调用处散写字符串。
 * </p>
 */
public enum CodeModeErrorCode {

    /** 工具参数不是 JSON 对象，或字段类型非法。 */
    CODE_MODE_INVALID_ARGUMENT,
    /** 源码为空 / 过大 / 没有 main(context) 入口。 */
    CODE_MODE_INVALID_SOURCE,
    /** workspaceFile 不存在。 */
    CODE_MODE_WORKSPACE_FILE_NOT_FOUND,
    /** workspaceFile 越出脚本目录白名单或路径不合法。 */
    CODE_MODE_WORKSPACE_FILE_DENIED,
    /** 整体 wall-clock 超时。 */
    CODE_MODE_TIMEOUT,
    /** 用户停止本轮对话。 */
    CODE_MODE_CANCELLED,
    /** main 返回值超过输出上限。 */
    CODE_MODE_OUTPUT_TOO_LARGE,
    /** 内部 capability 调用次数超过上限。 */
    CODE_MODE_TOOL_CALL_LIMIT,
    /** capability 策略拒绝（非白名单 CLI、越界路径等）。 */
    CODE_MODE_CAPABILITY_DENIED,
    /** capability 依赖未装配（opencli 缺失、浏览器离线等）。 */
    CODE_MODE_CAPABILITY_UNAVAILABLE,
    /** expectedVersion 与实际文件 hash 不一致。 */
    CODE_MODE_FILE_CONFLICT,
    /** 文件超过读写上限。 */
    CODE_MODE_FILE_TOO_LARGE,
    /** 累计写入字节超过内存预算。 */
    CODE_MODE_MEMORY_LIMIT,
    /** 脚本运行期异常。 */
    CODE_MODE_RUNTIME_ERROR;

    public String code() {
        return name();
    }
}
