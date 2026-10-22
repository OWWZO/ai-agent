package org.wwz.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * 手册 ${PYTHON} 与默认解释器
     */
    private String runtimePython = "python";

    private int bashTimeoutSec = 120;

    private int bashMaxTimeoutSec = 600;

    private int bashOutputMaxChars = 64_000;

    /**
     * 是否递归扫描 skill 根目录（支持 category/name/SKILL.md）。
     * 默认关闭，保持只扫一层的历史行为。
     */
    private boolean recursiveScan = false;

    /**
     * 递归扫描最大深度
     */
    private int maxScanDepth = 3;

    /**
     * 扫描时忽略的目录名；留空则使用内置默认集（.git / node_modules / __pycache__ 等）
     */
    private Set<String> excludedDirs = new LinkedHashSet<>();

    /**
     * 扫描结果缓存 TTL（秒），<= 0 表示每次强制全量扫描
     */
    private int scanCacheTtlSeconds = 30;

    /**
     * 是否对导入的 skill 做静态威胁扫描
     */
    private boolean threatScanEnabled = true;

    /**
     * system prompt 中技能索引的总字符预算；<= 0 表示不限制
     */
    private int promptIndexMaxChars = 6000;

    /**
     * 技能索引中单条描述的最大字符数
     */
    private int promptIndexDescriptionChars = 80;
}
