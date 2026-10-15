package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeRequestValidator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code Mode 入参校验：source / workspaceFile 互斥、脚本路径白名单、超时收敛。
 */
public class CodeModeRequestValidatorTest {

    private static final List<String> PREFIXES = List.of("scripts/", "jobs/");

    @Test
    public void sourceAndWorkspaceFileAreMutuallyExclusive() {
        CodeModeRequestValidator.Validation both = CodeModeRequestValidator.validateSourceTarget(
                "async function main(){}", "scripts/a.js", 1000, PREFIXES);
        Assert.assertFalse(both.isValid());
        Assert.assertEquals("CODE_MODE_INVALID_ARGUMENT", both.errorCode());

        CodeModeRequestValidator.Validation neither = CodeModeRequestValidator.validateSourceTarget(
                null, "  ", 1000, PREFIXES);
        Assert.assertFalse(neither.isValid());
        Assert.assertEquals("CODE_MODE_INVALID_ARGUMENT", neither.errorCode());

        Assert.assertTrue(CodeModeRequestValidator.validateSourceTarget(
                "async function main(){}", null, 1000, PREFIXES).isValid());
        Assert.assertTrue(CodeModeRequestValidator.validateSourceTarget(
                null, "scripts/a.js", 1000, PREFIXES).isValid());
    }

    @Test
    public void sourceOverLimitIsRejected() {
        CodeModeRequestValidator.Validation validation = CodeModeRequestValidator.validateSourceTarget(
                "x".repeat(50), null, 10, PREFIXES);
        Assert.assertFalse(validation.isValid());
        Assert.assertEquals("CODE_MODE_INVALID_SOURCE", validation.errorCode());
    }

    @Test
    public void workspaceFileMustBeRelativeJsUnderScriptPrefixes() {
        Assert.assertTrue(CodeModeRequestValidator.validateSourceTarget(
                null, "scripts/scrape.js", 1000, PREFIXES).isValid());
        Assert.assertTrue(CodeModeRequestValidator.validateSourceTarget(
                null, "jobs\\merge.js", 1000, PREFIXES).isValid());

        assertDenied("outputs/run.js");
        assertDenied("scripts/../../etc/passwd.js");
        assertDenied("scripts/run.txt");
        assertDenied("/abs/path/run.js");
        assertDenied("C:/tmp/run.js");
        assertDenied("other/run.js");
    }

    private void assertDenied(String workspaceFile) {
        CodeModeRequestValidator.Validation validation =
                CodeModeRequestValidator.validateSourceTarget(null, workspaceFile, 1000, PREFIXES);
        Assert.assertFalse(workspaceFile, validation.isValid());
        Assert.assertEquals(workspaceFile, "CODE_MODE_WORKSPACE_FILE_DENIED", validation.errorCode());
    }

    @Test
    public void inputsMustBeJsonObject() {
        Assert.assertNotNull(CodeModeRequestValidator.normalizeInputs(null));
        Assert.assertEquals(0, CodeModeRequestValidator.normalizeInputs(null).size());
        Map<String, Object> inputs = CodeModeRequestValidator.normalizeInputs(Map.of("a", 1));
        Assert.assertNotNull(inputs);
        Assert.assertEquals(1, inputs.get("a"));
        Assert.assertNull(CodeModeRequestValidator.normalizeInputs(List.of("a")));
        Assert.assertNull(CodeModeRequestValidator.normalizeInputs("text"));
    }

    @Test
    public void timeoutIsClampedToConfiguredMaximum() {
        Assert.assertEquals(180_000L,
                CodeModeRequestValidator.resolveTimeoutMs(null, 180_000L, 540_000L));
        Assert.assertEquals(540_000L,
                CodeModeRequestValidator.resolveTimeoutMs(10_000_000L, 180_000L, 540_000L));
        Assert.assertEquals(5_000L,
                CodeModeRequestValidator.resolveTimeoutMs(5_000, 180_000L, 540_000L));
        Assert.assertEquals(180_000L,
                CodeModeRequestValidator.resolveTimeoutMs(-1, 180_000L, 540_000L));
    }

    @Test
    public void normalizeInputsKeepsKeyOrder() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("b", 2);
        raw.put("a", 1);
        Assert.assertEquals(List.of("b", "a"),
                List.copyOf(CodeModeRequestValidator.normalizeInputs(raw).keySet()));
    }
}
