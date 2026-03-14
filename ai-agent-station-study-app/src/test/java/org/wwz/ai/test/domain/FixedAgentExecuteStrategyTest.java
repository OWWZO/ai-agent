package org.wwz.ai.test.domain;

import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.execute.fixed.FixedAgentExecuteStrategy;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/9/13 15:39
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class FixedAgentExecuteStrategyTest {

    @Resource
    private FixedAgentExecuteStrategy fixedAgentExecuteStrategy;


}
