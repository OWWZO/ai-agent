# AI Agent Station Study - AI智能体学习平台 - @小傅哥 v2.2

## 项目简介

本项目是一个基于DDD架构的AI智能体学习平台，提供Auto Agent自动智能对话功能，支持流式响应和实时交互体验。

## 技术架构

- **架构模式**: DDD（领域驱动设计）
- **后端技术**: Spring Boot + Java
- **前端技术**: HTML5 + JavaScript + Tailwind CSS
- **通信方式**: Server-Sent Events (SSE) 流式响应
- **容器化**: Docker

## 相关文档

- docker 使用文档：[https://bugstack.cn/md/road-map/docker.html](https://bugstack.cn/md/road-map/docker.html)
- DDD 教程；
  - [DDD 概念理论](https://bugstack.cn/md/road-map/ddd-guide-01.html)
  - [DDD 建模方法](https://bugstack.cn/md/road-map/ddd-guide-02.html)
  - [DDD 工程模型](https://bugstack.cn/md/road-map/ddd-guide-03.html)
  - [DDD 架构设计](https://bugstack.cn/md/road-map/ddd.html)
  - [DDD 建模案例](https://bugstack.cn/md/road-map/ddd-model.html)

## API接口文档

### Auto Agent 智能对话接口

#### 接口概述

该接口提供AI智能体自动对话功能，支持流式响应，实时返回AI的思考过程和执行结果。

#### 接口信息

- **接口地址**: `POST /api/v1/agent/auto_agent`
- **请求方式**: POST
- **响应格式**: Server-Sent Events (SSE) 流式响应
- **Content-Type**: `application/json`
- **Accept**: `text/event-stream`

#### 请求参数

**请求体 (JSON格式)**

```json
{
  "aiAgentId": "3",
  "message": "检索小傅哥的相关项目，列出一份学习计划",
  "sessionId": "session_1642345678901_abc123def",
  "maxStep": 5
}
```

**参数说明**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| aiAgentId | String | 是 | AI智能体类型ID，目前支持："3"（Auto Agent - 自动智能对话体） |
| message | String | 是 | 用户输入的问题或指令，最大长度1000字符 |
| sessionId | String | 是 | 会话ID，用于标识唯一对话会话，格式：session_时间戳_随机字符串 |
| maxStep | Integer | 是 | 最大执行步数，可选值：1、2、3、5、10、20、50 |

#### 响应格式

**SSE流式响应**

响应采用Server-Sent Events格式，每条消息以`data: `开头，包含JSON格式的数据：

```
data: {"type":"analysis","subType":"analysis_status","step":1,"content":"开始分析用户需求...","completed":false,"timestamp":1642345678901,"sessionId":"session_1642345678901_abc123def"}

data: {"type":"execution","subType":"execution_process","step":2,"content":"正在执行搜索任务...","completed":false,"timestamp":1642345678902,"sessionId":"session_1642345678901_abc123def"}

data: {"type":"summary","subType":"summary_overview","step":5,"content":"## 学习计划\n\n基于检索结果，为您制定以下学习计划...","completed":true,"timestamp":1642345678905,"sessionId":"session_1642345678901_abc123def"}
```

**响应字段说明**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| type | String | 消息类型，详见下方类型说明 |
| subType | String | 消息子类型，详见下方子类型说明 |
| step | Integer | 当前执行步骤 |
| content | String | 消息内容，支持Markdown格式 |
| completed | Boolean | 是否完成 |
| timestamp | Long | 时间戳 |
| sessionId | String | 会话ID |

#### 消息类型说明

**主要类型 (type)**

| 类型 | 名称 | 图标 | 说明 |
|------|------|------|------|
| analysis | 分析阶段 | 🎯 | AI分析用户需求和制定策略 |
| execution | 执行阶段 | ⚡ | AI执行具体任务 |
| supervision | 监督阶段 | 🔍 | AI监督和质量检查 |
| summary | 总结阶段 | 📊 | AI总结结果和输出最终答案 |
| error | 错误信息 | ❌ | 执行过程中的错误信息 |
| complete | 完成 | ✅ | 任务执行完成标识 |

**子类型 (subType)**

| 子类型 | 说明 |
|--------|------|
| analysis_status | 任务状态 |
| analysis_history | 历史评估 |
| analysis_strategy | 执行策略 |
| analysis_progress | 完成度 |
| execution_target | 执行目标 |
| execution_process | 执行过程 |
| execution_result | 执行结果 |
| execution_quality | 质量检查 |
| assessment | 质量评估 |
| issues | 问题识别 |
| suggestions | 改进建议 |
| score | 质量评分 |
| pass | 检查结果 |
| completed_work | 已完成工作 |
| incomplete_reasons | 未完成原因 |
| evaluation | 效果评估 |
| summary_overview | 总结概览 |

#### 前端集成示例

**JavaScript调用示例**

```javascript
// 准备请求数据
const requestData = {
    aiAgentId: "3",
    message: "检索小傅哥的相关项目，列出一份学习计划",
    sessionId: "session_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9),
    maxStep: 5
};

// 发送POST请求
fetch('http://localhost:8091/api/v1/agent/auto_agent', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
    },
    body: JSON.stringify(requestData)
})
.then(response => {
    if (!response.ok) {
        throw new Error('网络请求失败: ' + response.status);
    }
    
    // 处理流式响应
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    
    function readStream() {
        reader.read().then(({ done, value }) => {
            if (done) {
                console.log('流式响应结束');
                return;
            }
            
            // 解码数据块
            const chunk = decoder.decode(value, { stream: true });
            
            // 处理SSE数据
            const lines = chunk.split('\n');
            for (let line of lines) {
                if (line.startsWith('data: ')) {
                    const data = line.substring(6).trim();
                    if (data && data !== '[DONE]') {
                        try {
                            const jsonData = JSON.parse(data);
                            handleSSEMessage(jsonData);
                        } catch (e) {
                            console.warn('无法解析JSON数据:', data);
                        }
                    }
                }
            }
            
            // 继续读取流
            readStream();
        });
    }
    
    readStream();
})
.catch(error => {
    console.error('请求错误:', error);
});

// 处理SSE消息
function handleSSEMessage(jsonData) {
    const { type, subType, step, content, completed, timestamp, sessionId } = jsonData;
    
    // 根据消息类型进行不同处理
    if (type === 'summary') {
        // 显示最终结果
        displayFinalResult(content);
    } else {
        // 显示思考过程
        displayThinkingProcess(type, subType, content, step);
    }
}
```

#### 错误处理

**常见错误码**

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| 400 | 请求参数错误 | 检查请求参数格式和必填字段 |
| 500 | 服务器内部错误 | 查看服务器日志，联系技术支持 |
| 503 | 服务不可用 | 检查服务状态，稍后重试 |

**错误响应示例**

```
data: {"type":"error","subType":null,"step":null,"content":"请求参数错误：message不能为空","completed":false,"timestamp":1642345678901,"sessionId":"session_1642345678901_abc123def"}
```

#### 使用注意事项

1. **会话管理**: 每次新对话建议生成新的sessionId
2. **连接管理**: SSE连接建立后，需要正确处理连接断开和重连
3. **内容渲染**: content字段支持Markdown格式，建议使用Markdown解析器渲染
4. **性能优化**: 对于长时间的对话，建议实现消息缓存和分页显示
5. **错误处理**: 需要处理网络异常、解析异常等各种错误情况

#### 部署配置

**CORS配置**

接口已配置跨域支持：
```java
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
```

**服务端口**

- 默认端口：8091
- 前端演示页面：http://localhost:8080/index.html

## 快速开始

1. **启动后端服务**
   ```bash
   mvn spring-boot:run
   ```

2. **访问前端页面**
   ```bash
   cd docs/dev-ops/nginx/html
   python3 -m http.server 8080
   ```
   
3. **打开浏览器访问**
   ```
   http://localhost:8080/index.html
   ```

