package org.wwz.ai.domain.agent.runtime.openui;

import java.util.List;

/**
 * 前端诊断的入参模型（plans/038 §4 第 3 步）。
 *
 * <p>v4 只剩 REPLACE 块，statementId / path 对模型已无意义，只保留错误码与描述；
 * {@code incomplete} 照常上送 —— REPLACE 块不需要 statementId，incomplete 程序也尽力修。</p>
 */
public record OpenUiDiagnosis(boolean incomplete, List<OpenUiFixError> errors) {

    public OpenUiDiagnosis {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
