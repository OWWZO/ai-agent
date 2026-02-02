package org.wwz.ai.trigger.http.admin.util;

import org.wwz.ai.infrastructure.dao.po.AiClientConfig;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * DrawConfigParser测试类
 * 用于验证修复后的解析器能正确处理节点连接
 */
public class DrawConfigParserTest {

    public static void main(String[] args) {
        try {
            // 读取测试JSON文件
            String configData = Files.readString(Paths.get("/Users/fuzhengwei/coding/gitcode/KnowledgePlanet/ai-agent/ai-agent-station-front/docs/save.json"));
            
            // 解析配置数据
            List<AiClientConfig> configList = DrawConfigParser.parseConfigData(configData);
            
            // 输出解析结果
            System.out.println("解析结果总数: " + configList.size());
            System.out.println("详细配置关系:");
            
            for (int i = 0; i < configList.size(); i++) {
                AiClientConfig config = configList.get(i);
                System.out.printf("%d. %s(%s) -> %s(%s), extParam: %s%n", 
                    i + 1,
                    config.getSourceType(), config.getSourceId(),
                    config.getTargetType(), config.getTargetId(),
                    config.getExtParam());
            }
            
            // 验证是否解析出了预期的连接关系
            if (configList.size() > 0) {
                System.out.println("✓ 成功解析出配置关系");
            } else {
                System.out.println("✗ 未解析出任何配置关系");
            }
            
            // 验证是否包含了sourcePortID信息
            boolean hasPortInfo = configList.stream()
                .anyMatch(config -> config.getExtParam() != null && 
                         config.getExtParam().contains("sourcePortId"));
            
            System.out.println("是否包含端口信息: " + hasPortInfo);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("测试失败: " + e.getMessage());
        }
    }
}