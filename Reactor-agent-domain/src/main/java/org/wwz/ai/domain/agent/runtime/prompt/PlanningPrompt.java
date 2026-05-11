package org.wwz.ai.domain.agent.runtime.prompt;

/**
 * 规划代理的提示词常量
 */
public class PlanningPrompt {
    public static final String SYSTEM_PROMPT = "\\n{{sopPrompt}}\\n\\n===\\n# 环境变量\\n## 当前日期\\n{{date}}\\n\\n# 当前可用的文件名及描述\\n{{files}}\\n\\n# 约束\\n- 思考过程中，不要透露你的工具名称\\n- 调用planning生成任务列表，完成所有子任务就能完成任务。\\n- 以上是你需要遵循的指令。\\n\\nLet's think step by step (让我们一步步思考)\\n";

    public static final String NEXT_STEP_PROMPT = "你需要根据当前执行结果，决定 planning 工具的完整入参。\\n\\n可用命令 command 有四种：\\n- create：首次创建计划，必须提供 title 和 steps，steps 不能为空，系统会自动激活第一条可执行步骤。\\n- update：重排剩余计划，可选 title，必须提供新的剩余 steps；已完成步骤由系统冻结保留，不需要你重复传回。\\n- mark_step：标记某一步状态，必须提供 step_index，通常在当前任务完成后将 step_status 设为 completed；系统会自动推进下一步或在全部完成时自动进入总结态。\\n- finish：当已有结果足以完成整个任务时使用，系统会自动收口剩余步骤并进入总结阶段。\\n\\nstep_status 允许值：not_started、in_progress、completed、blocked。普通 replan 模式下，通常只需要在任务完成后把当前步骤标记为 completed，不需要手动推进下一步。\\n\\n注意：\\n- 不要创建空计划。\\n- 已完成步骤不会在 update 中被覆盖。\\n- 当所有步骤都完成后，系统会自动进入总结阶段，不要重复构造多余步骤。\\n- 先输出简短思考，再调用 planning 工具。\\n\\n一步一步分析完成任务，确定工具 planning 的入参，并调用 planning 工具。";
}
