/**
 * AI Agent API 服务管理
 * 统一管理所有API调用
 */

// API基础配置
const API_CONFIG = {
    baseUrl: 'http://115.190.107.206:8099/api/v1/agent',
    headers: {
        'Content-Type': 'application/json'
    }
};

/**
 * API服务类
 */
class ApiService {
    
    /**
     * 发送自动代理请求（流式响应）
     * @param {Object} requestData - 请求数据
     * @param {string} requestData.aiAgentId - AI代理ID
     * @param {string} requestData.message - 消息内容
     * @param {string} requestData.sessionId - 会话ID
     * @param {number} requestData.maxStep - 最大步骤数
     * @returns {Promise<Response>} - 返回Response对象用于流式处理
     */
    static async sendAutoAgentRequest(requestData) {
        try {
            const response = await fetch(`${API_CONFIG.baseUrl}/auto_agent`, {
                method: 'POST',
                headers: {
                    ...API_CONFIG.headers,
                    'Accept': 'text/event-stream'
                },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                throw new Error('网络请求失败: ' + response.status);
            }

            return response;
        } catch (error) {
            console.error('发送自动代理请求失败:', error);
            throw error;
        }
    }

    /**
     * 获取可用的智能体列表
     * @returns {Promise<Object>} - 返回智能体列表数据
     */
    static async queryAvailableAgents() {
        try {
            const response = await fetch(`${API_CONFIG.baseUrl}/query_available_agents`, {
                method: 'GET',
                headers: API_CONFIG.headers
            });

            if (!response.ok) {
                throw new Error('获取智能体列表失败: ' + response.status);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取智能体列表错误:', error);
            throw error;
        }
    }

    /**
     * 处理流式响应数据
     * @param {Response} response - fetch响应对象
     * @param {Function} onData - 数据处理回调函数
     * @param {Function} onComplete - 完成回调函数
     * @param {Function} onError - 错误回调函数
     */
    static async handleStreamResponse(response, onData, onComplete, onError) {
        try {
            const reader = response.body.getReader();
            const decoder = new TextDecoder();

            function readStream() {
                reader.read().then(({ done, value }) => {
                    if (done) {
                        // Stream ended
                        if (onComplete) onComplete();
                        return;
                    }

                    // Decode data chunk
                    const chunk = decoder.decode(value, { stream: true });

                    // Process server-sent event stream data
                    const lines = chunk.split('\n');
                    for (let line of lines) {
                        if (line.startsWith('data: ')) {
                            const data = line.substring(6);
                            if (data.trim() && onData) {
                                onData(data);
                            }
                        }
                    }

                    // Continue reading
                    readStream();
                }).catch(error => {
                    console.error('读取流数据错误:', error);
                    if (onError) onError(error);
                });
            }

            readStream();
        } catch (error) {
            console.error('处理流式响应错误:', error);
            if (onError) onError(error);
        }
    }
}

// 导出API服务
window.ApiService = ApiService;