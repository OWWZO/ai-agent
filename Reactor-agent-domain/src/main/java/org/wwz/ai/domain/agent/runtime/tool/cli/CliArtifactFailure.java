package org.wwz.ai.domain.agent.runtime.tool.cli;

import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI 产物物化失败项。
 * <p>
 * 失败项不进入 artifact registry，也不写入 Execution Ledger；但会回传给工具结果，
 * 让调用方知道本次调用实际成功登记了哪些文件。
 */
@Value
@Builder
public class CliArtifactFailure {

    /** 结构化失败原因。 */
    public static final String OUTSIDE_WORKSPACE = "outside_workspace";
    public static final String FILE_NOT_FOUND = "file_not_found";
    public static final String FILE_TOO_LARGE = "file_too_large";
    public static final String ARTIFACT_REGISTER_FAILED = "artifact_register_failed";
    public static final String ARTIFACT_UPLOAD_FAILED = "artifact_upload_failed";
    public static final String ARTIFACT_LIMIT_EXCEEDED = "artifact_limit_exceeded";
    public static final String OUTPUT_ROOT_MISSING = "artifact_output_root_missing";
    public static final String OUTPUT_ROOT_INVALID = "artifact_output_root_invalid";
    public static final String OUTPUT_ROOT_OUTSIDE_WORKSPACE = "artifact_output_root_outside_workspace";
    public static final String CAPTURE_ROOT_CONFLICT = "artifact_capture_root_conflict";
    public static final String CANDIDATE_OUTSIDE_CAPTURE_ROOT = "artifact_candidate_outside_capture_root";
    public static final String CAPTURE_SCAN_FAILED = "artifact_capture_scan_failed";

    /** 失败文件在 workspace 内的相对路径；无法定位时为空。 */
    String relativePath;

    /** 失败原因，取本类常量之一。 */
    String reason;

    /** 可读失败说明，不暴露绝对路径。 */
    String message;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("relativePath", relativePath);
        map.put("reason", reason);
        map.put("message", message);
        return map;
    }
}
