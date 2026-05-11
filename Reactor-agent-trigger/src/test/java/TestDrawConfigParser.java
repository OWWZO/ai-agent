import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 独立测试DrawConfigParser的优化效果
 */
public class TestDrawConfigParser {
    
    public static void main(String[] args) {
        try {
            // 读取JSON文件
            String jsonPath = "/Users/fuzhengwei/coding/gitcode/KnowledgePlanet/ai-agent/ai-agent-station-front/docs/save.json";
            String jsonContent = new String(Files.readAllBytes(Paths.get(jsonPath)));
            
            System.out.println("=== JSON文件分析 ===");
            System.out.println("JSON文件读取成功，长度: " + jsonContent.length());
            
            // 解析JSON
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            JsonNode nodesArray = rootNode.get("nodes");
            JsonNode edgesArray = rootNode.get("edges");
            
            System.out.println("节点数量: " + nodesArray.size());
            System.out.println("边数量: " + edgesArray.size());
            
            // 分析节点类型
            System.out.println("\n=== 节点类型分析 ===");
            for (JsonNode node : nodesArray) {
                String nodeId = node.get("id").asText();
                String nodeType = node.get("type").asText();
                String title = "";
                if (node.has("data") && node.get("data").has("title")) {
                    title = node.get("data").get("title").asText();
                }
                System.out.println(nodeId + " -> " + nodeType + " (" + title + ")");
            }
            
            // 分析边关系
            System.out.println("\n=== 边关系分析 ===");
            for (JsonNode edge : edgesArray) {
                String sourceNodeId = edge.get("sourceNodeID").asText();
                String targetNodeId = edge.get("targetNodeID").asText();
                String sourcePortId = edge.has("sourcePortID") ? edge.get("sourcePortID").asText() : "无";
                System.out.println(sourceNodeId + " -> " + targetNodeId + " (端口: " + sourcePortId + ")");
            }
            
            // 检查tool-mcp类型节点
            System.out.println("\n=== tool-mcp节点检查 ===");
            for (JsonNode node : nodesArray) {
                if ("tool-mcp".equals(node.get("type").asText())) {
                    String nodeId = node.get("id").asText();
                    JsonNode data = node.get("data");
                    if (data != null && data.has("inputsValues")) {
                        JsonNode inputsValues = data.get("inputsValues");
                        if (inputsValues.has("toolMcpName")) {
                            JsonNode toolMcpName = inputsValues.get("toolMcpName");
                            if (toolMcpName.isArray() && toolMcpName.size() > 0) {
                                String value = toolMcpName.get(0).get("value").asText();
                                System.out.println("tool-mcp节点 " + nodeId + " 的引用ID: " + value);
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("测试失败: " + e.getMessage());
        }
    }
}