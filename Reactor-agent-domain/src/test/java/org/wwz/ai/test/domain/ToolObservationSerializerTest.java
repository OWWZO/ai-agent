package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对齐 LeAgent serialize_for_llm 的单元契约。
 */
public class ToolObservationSerializerTest {

    @Test
    public void successStringPassesThrough() {
        Assert.assertEquals("plain ok", ToolObservationSerializer.serializeSuccess("plain ok"));
    }

    @Test
    public void successObjectBecomesJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "document_generate");
        data.put("ok", true);
        data.put("produced_files", List.of(Map.of("file_name", "a.pdf")));

        String serialized = ToolObservationSerializer.serializeSuccess(data);
        Assert.assertTrue(serialized.contains("\"tool\":\"document_generate\""));
        Assert.assertTrue(serialized.contains("\"ok\":true"));
        Assert.assertTrue(serialized.contains("a.pdf"));
    }

    @Test
    public void failureWithDetailBecomesToolOkJson() {
        Map<String, Object> detail = Map.of("code", 400, "reason", "bad input");
        String serialized = ToolObservationSerializer.serializeFailure("boom", detail);
        Assert.assertTrue(serialized.contains("\"tool_ok\":false"));
        Assert.assertTrue(serialized.contains("\"error\":\"boom\""));
        Assert.assertTrue(serialized.contains("bad input"));
    }

    @Test
    public void failureWithoutDetailUsesErrorPrefix() {
        Assert.assertEquals("Error: network down", ToolObservationSerializer.serializeFailure("network down", null));
    }

    @Test
    public void truncateAppendsNotice() {
        String body = "a".repeat(200);
        int cap = 120;
        String truncated = ToolObservationSerializer.truncateForLlm(body, cap);
        Assert.assertTrue(truncated.endsWith(ToolObservationSerializer.TRUNCATION_NOTICE));
        Assert.assertEquals(cap, truncated.length());
        Assert.assertTrue(truncated.startsWith("aaa"));
    }

    @Test
    public void payloadFromDataSerializesViaCentralPath() {
        Map<String, Object> data = Map.of("tool", "excel_reader", "ok", true);
        ToolResultPayload payload = ToolResultPayload.fromData(data);
        Assert.assertNull(payload.getLlmObservation());
        String observation = ToolObservationSerializer.serializePayload(payload);
        Assert.assertTrue(observation.contains("excel_reader"));
        Assert.assertTrue(observation.contains("\"ok\":true") || observation.contains("\"ok\": true"));
    }

    @Test
    public void payloadWithPresetObservationIsNotForCentralOnlyPath() {
        ToolResultPayload payload = ToolResultPayload.text("already crafted");
        // text() 预填 llmObservation；BaseAgent 应保留。serializePayload 在未预填时才被调用。
        Assert.assertEquals("already crafted", payload.getLlmObservation());
    }
}
