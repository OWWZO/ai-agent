package org.wwz.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 机制配置
 */
@Data
@ConfigurationProperties(prefix = "autobots.autoagent.skill")
public class AiAgentSkillProperties {

    /**
     * 是否启用 skill 机制
     */
    private boolean enabled = false;

    /**
     * skill 根目录列表
     */
    private List<String> directories = new ArrayList<>();

    /**
     * ReAct 是否注入 skill 工具
     */
    private boolean reactEnabled = true;

    /**
     * PlanSolve 是否注入 skill 工具
     */
    private boolean planSolveEnabled = true;

    /**
     * read_tool 默认最大返回字符数
     */
    private int maxReadChars = 12000;

    /**
     * list_directory_tool 默认最大返回条数
     */
    private int maxListEntries = 200;

    /**
     * glob_tool 默认最大匹配数
     */
    private int maxGlobResults = 100;

    /**
     * grep_tool 默认最大匹配数
     */
    private int maxGrepMatches = 100;

    /**
     * script_runner_tool 默认超时时间
     */
    private int defaultScriptTimeoutSeconds = 120;
}
