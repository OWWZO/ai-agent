package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.trigger.http.admin.AiClientAdminController;
import org.wwz.ai.trigger.http.agent.AgentFileController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 锁定已收敛入口：trigger 不再直连 infrastructure。
 */
public class TriggerCaseBoundaryTest {

    @Test
    public void agentFileControllerShouldDependOnCaseOnly() {
        List<String> fieldTypes = fieldTypes(AgentFileController.class);
        Assert.assertTrue(fieldTypes.contains(
                "org.wwz.ai.application.agent.file.ConversationFileApplicationService"));
        Assert.assertFalse(fieldTypes.stream().anyMatch(type -> type.startsWith("org.wwz.ai.infrastructure")));
    }

    @Test
    public void aiClientAdminControllerShouldDependOnCaseOnly() {
        List<String> fieldTypes = fieldTypes(AiClientAdminController.class);
        Assert.assertTrue(fieldTypes.contains(
                "org.wwz.ai.application.agent.admin.AiClientAdminApplicationService"));
        Assert.assertFalse(fieldTypes.stream().anyMatch(type -> type.startsWith("org.wwz.ai.infrastructure")));
    }

    private static List<String> fieldTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());
    }
}
