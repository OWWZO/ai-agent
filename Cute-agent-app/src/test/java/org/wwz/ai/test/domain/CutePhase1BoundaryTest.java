package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;

/**
 * 锁定 Phase 1 明确延后的共享配置边界。
 */
public class CutePhase1BoundaryTest {

    @Test
    public void shouldKeepCuteConfigAsTransitionalSharedConfiguration() {
        Assert.assertEquals(
                "org.wwz.ai.domain.agent.cute.config",
                CuteConfig.class.getPackageName()
        );
        Assert.assertNotNull(AnnotatedElementUtils.findMergedAnnotation(CuteConfig.class, Configuration.class));
    }
}
