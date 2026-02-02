# AI Agent Prompt 优化配置指南

## 问题分析

当前AI Agent系统的Prompt配置存在以下关键问题：

### 1. 配置与实现脱节
- **数据库Prompt配置**：虽然在Prompt中提到了MCP工具调用能力
- **Java代码实现**：Step1-Step4执行节点中没有实际调用MCP服务
- **结果**：无法获取真实的Grafana/Prometheus监控数据

### 2. 具体问题表现
- Step1AnalyzerNode：只进行任务分析，未调用MCP工具获取数据源信息
- Step2PrecisionExecutorNode：未执行实际的Grafana查询操作
- Step3QualitySupervisorNode：缺乏真实数据验证质量
- Step4LogExecutionSummaryNode：基于空数据生成报告

## 优化方案

### Step1 任务分析器优化

#### 当前配置问题
```sql
'**用户需求:** %s\n**执行步骤:** 第 %d 步 (最大 %d 步)\n**历史记录:**\n%s\n**当前任务:** %s\n\n# 任务分析指令\n\n## 核心职责\n作为智能任务分析器，你需要：\n1. **需求理解**: 深度解析用户的真实意图和具体需求\n2. **策略制定**: 基于需求制定最优的执行策略和工具选择\n3. **资源评估**: 评估所需的工具、数据源和执行步骤\n4. **智能决策**: 判断是否需要调用MCP工具或检索知识库'
```

#### 优化后配置
```sql
'**用户需求:** %s\n**执行步骤:** 第 %d 步 (最大 %d 步)\n**历史记录:**\n%s\n**当前任务:** %s\n\n# 智能任务分析器\n\n## 核心职责\n作为智能任务分析器，你需要：\n1. **需求理解**: 深度解析用户的真实意图和具体需求\n2. **数据源检查**: 首先调用MCP工具检查可用的数据源\n3. **策略制定**: 基于需求和数据源制定执行策略\n4. **工具选择**: 明确指定需要调用的MCP工具和参数\n\n## 必须执行的MCP工具调用\n\n### 1. 数据源检查\n- **工具**: grafana/list_datasources\n- **目的**: 获取可用的Prometheus数据源\n- **执行时机**: 任务分析阶段开始时\n\n### 2. 基础连通性测试\n- **工具**: grafana/query_prometheus\n- **查询**: up\n- **目的**: 验证Prometheus连接状态\n\n## 分析输出格式\n**数据源状态:**\n[MCP工具调用结果 - 可用数据源列表]\n\n**连通性检查:**\n[MCP工具调用结果 - up指标查询结果]\n\n**需求分析:**\n[用户真实需求的详细解析]\n\n**执行策略:**\n[基于数据源状态制定的具体执行计划]\n\n**MCP工具调用计划:**\n[下一步需要调用的具体MCP工具和参数]\n\n**完成度评估:** [0-100]%\n**任务状态:** [CONTINUE/COMPLETED]'
```

### Step2 精准执行器优化

#### 当前配置问题
```sql
'**用户需求:** %s\n**分析策略:** %s\n\n# 智能执行指令\n\n## 核心能力\n作为智能执行引擎，你具备：\n1. **自动化工具调用**: 根据需求自动选择和调用合适的MCP工具\n2. **数据获取与处理**: 从Grafana、Prometheus等数据源获取监控数据'
```

#### 优化后配置
```sql
'**用户需求:** %s\n**分析策略:** %s\n\n# 智能执行引擎\n\n## 核心能力\n作为智能执行引擎，你必须：\n1. **强制MCP工具调用**: 根据分析策略强制调用指定的MCP工具\n2. **数据获取**: 从Grafana/Prometheus获取真实监控数据\n3. **结果验证**: 确保每次MCP调用都有有效返回\n\n## 强制执行的MCP工具调用流程\n\n### 监控分析类任务必须执行：\n\n#### 1. 系统接口分析\n```\n# 获取HTTP请求指标\ngrafana/query_prometheus\nquery: rate(http_server_requests_seconds_count[5m])\nstart: now-1h\nend: now\n```\n\n#### 2. TPS/QPS分析\n```\n# 获取请求速率\ngrafana/query_prometheus\nquery: sum(rate(http_server_requests_seconds_count[5m])) by (uri, method)\nstart: now-1h\nend: now\n```\n\n#### 3. 响应时间分析\n```\n# 获取平均响应时间\ngrafana/query_prometheus\nquery: rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])\nstart: now-1h\nend: now\n```\n\n#### 4. 最大响应时间\n```\n# 获取最大响应时间\ngrafana/query_prometheus\nquery: http_server_requests_seconds_max\nstart: now-1h\nend: now\n```\n\n## 输出格式\n**执行目标:**\n[本轮具体的执行目标]\n\n**MCP工具调用记录:**\n[实际调用的MCP工具、参数和返回结果]\n\n**数据获取结果:**\n[从Prometheus获取的具体监控数据]\n\n**数据分析:**\n[对获取数据的分析和发现]\n\n**执行状态:**\n[SUCCESS/PARTIAL/FAILED]'
```

### Step3 质量监督优化

#### 优化后配置
```sql
'**用户需求:** %s\n**执行结果:** %s\n\n# 智能质量监督\n\n## 监督职责\n作为智能质量监督员，你需要：\n1. **数据验证**: 验证MCP工具调用是否成功执行\n2. **结果完整性**: 确保获取了完整的监控数据\n3. **数据准确性**: 验证Prometheus查询结果的合理性\n4. **需求匹配度**: 确保结果完全满足用户需求\n\n## 质量检查标准\n\n### MCP工具调用检查\n- ✅ grafana/list_datasources 调用成功\n- ✅ grafana/query_prometheus 调用成功\n- ✅ 查询语句语法正确\n- ✅ 返回数据非空\n- ✅ 时间范围合理\n\n### 数据质量检查\n- ✅ TPS/QPS数据存在且合理\n- ✅ 响应时间数据完整\n- ✅ 接口列表不为空\n- ✅ 指标值在合理范围内\n\n### 分析质量检查\n- ✅ 包含具体数值\n- ✅ 提供趋势分析\n- ✅ 识别性能瓶颈\n- ✅ 给出优化建议\n\n## 输出格式\n**MCP工具调用验证:**\n[验证每个MCP工具调用的成功状态]\n\n**数据质量评估:**\n[评估获取数据的完整性和准确性]\n\n**分析质量评估:**\n[评估分析结果的专业性和实用性]\n\n**问题识别:**\n[发现的具体问题和不足]\n\n**改进建议:**\n[具体的改进方案]\n\n**质量评分:** [0-100]分\n**评估结果:** [PASS/OPTIMIZE/FAIL]'
```

### Step4 报告生成器优化

#### 优化后配置
```sql
'**用户问题:** %s\n**执行过程:**\n%s\n\n# 智能报告生成器\n\n## 核心功能\n作为智能报告生成器，你需要：\n1. **数据整合**: 整合所有MCP工具调用获得的真实监控数据\n2. **专业报告**: 基于真实数据生成专业分析报告\n3. **可视化表达**: 使用表格清晰展示监控数据\n4. **行动建议**: 基于真实数据提供优化建议\n\n## 报告结构模板\n\n### 📊 系统接口监控分析报告\n\n#### 数据源信息\n- **Prometheus数据源**: [从MCP调用结果获取]\n- **查询时间范围**: [实际查询的时间范围]\n- **数据采集时间**: [报告生成时间]\n\n#### 系统接口概览\n| 接口路径 | HTTP方法 | TPS | 平均响应时间(ms) | 最大响应时间(ms) | 状态 |\n|---------|---------|-----|----------------|----------------|------|\n| [从实际数据填充] | [从实际数据填充] | [从实际数据填充] | [从实际数据填充] | [从实际数据填充] | [状态评估] |\n\n#### 性能分析\n**🚀 高性能接口 (响应时间 < 100ms)**\n- [基于真实数据列出]\n\n**⚠️ 需要关注的接口 (响应时间 100-500ms)**\n- [基于真实数据列出]\n\n**🔴 高延迟接口 (响应时间 > 500ms)**\n- [基于真实数据列出]\n\n#### TPS/QPS 分析\n**📈 请求量统计**\n- 总TPS: [计算得出的总TPS]\n- 峰值TPS: [从数据中获取的峰值]\n- 平均QPS: [按接口计算的平均QPS]\n\n#### 💡 优化建议\n**性能优化建议:**\n- [基于真实数据分析的具体建议]\n\n**监控告警建议:**\n- [基于实际响应时间设置的告警阈值]\n\n**容量规划建议:**\n- [基于TPS趋势的容量建议]\n\n## 输出要求\n- 必须包含从MCP工具获取的真实数据\n- 提供具体的数值和百分比\n- 使用状态图标（🟢🟡🟠🔴）\n- 给出基于数据的可操作建议\n\n请基于MCP工具调用获得的真实监控数据生成完整的分析报告：'
```

## Java代码实现建议

### 在各Step节点中添加MCP工具调用

#### Step1AnalyzerNode 修改建议
```java
// 在doApply方法中添加MCP工具调用
@Override
protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
    // 现有的分析逻辑...
    
    // 添加MCP工具调用
    if (requestParameter.getMessage().contains("监控") || requestParameter.getMessage().contains("Grafana") || requestParameter.getMessage().contains("Prometheus")) {
        // 调用MCP工具获取数据源
        String datasourceResult = callMcpTool("grafana/list_datasources", null);
        dynamicContext.setValue("datasourceResult", datasourceResult);
        
        // 调用MCP工具测试连通性
        String connectivityResult = callMcpTool("grafana/query_prometheus", Map.of("query", "up"));
        dynamicContext.setValue("connectivityResult", connectivityResult);
    }
    
    // 现有的分析逻辑...
}
```

#### Step2PrecisionExecutorNode 修改建议
```java
@Override
protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
    // 现有的执行逻辑...
    
    // 根据任务类型调用相应的MCP工具
    if (requestParameter.getMessage().contains("接口") && requestParameter.getMessage().contains("TPS")) {
        // 获取接口TPS数据
        String tpsResult = callMcpTool("grafana/query_prometheus", Map.of(
            "query", "rate(http_server_requests_seconds_count[5m])",
            "start", "now-1h",
            "end", "now"
        ));
        dynamicContext.setValue("tpsResult", tpsResult);
        
        // 获取响应时间数据
        String responseTimeResult = callMcpTool("grafana/query_prometheus", Map.of(
            "query", "rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])",
            "start", "now-1h",
            "end", "now"
        ));
        dynamicContext.setValue("responseTimeResult", responseTimeResult);
    }
    
    // 现有的执行逻辑...
}
```

## 测试验证

### 测试用例
用户输入："分析 Grafana Prometheus 监控的系统接口及其 TPS 和 QPS"

### 预期结果
1. **Step1**: 成功调用 `grafana/list_datasources` 和基础连通性测试
2. **Step2**: 成功调用多个 `grafana/query_prometheus` 获取TPS、QPS、响应时间数据
3. **Step3**: 验证所有MCP调用成功，数据完整性检查通过
4. **Step4**: 基于真实数据生成包含具体数值的监控分析报告

### 成功标准
- ✅ 所有MCP工具调用成功执行
- ✅ 获取到真实的Prometheus监控数据
- ✅ 生成包含具体TPS/QPS数值的报告
- ✅ 提供基于真实数据的优化建议

## 总结

通过以上优化，AI Agent系统将能够：
1. 在任务分析阶段就开始调用MCP工具
2. 在执行阶段强制调用相应的MCP工具获取真实数据
3. 在质量监督阶段验证MCP调用的成功性和数据完整性
4. 在报告生成阶段基于真实数据生成专业报告

这样就能解决当前Prompt配置与实际执行脱节的问题，确保AI Agent能够准确分析系统监控数据。