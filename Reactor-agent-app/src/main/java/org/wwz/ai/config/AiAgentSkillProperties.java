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
     * 是否启用会话工作区 bash（materialize skill 后执行脚本）
     */
    private boolean sandboxBashEnabled = true;

    /**
     * 是否挂载遗留 skill_author（默认关）
     */
    private boolean authoringEnabled = false;

    /**
     * 手册 ${PYTHON} 与默认解释器
     */
    private String runtimePython = "python";

    private int bashTimeoutSec = 120;

    private int bashMaxTimeoutSec = 600;

    private int bashOutputMaxChars = 64_000;
}
