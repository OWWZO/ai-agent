import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 简单的解析器测试
 */
public class SimpleParserTest {
    
    public static void main(String[] args) {
        try {
            // 读取JSON文件
            String jsonPath = "/Users/fuzhengwei/coding/gitcode/KnowledgePlanet/ai-agent/ai-agent-station-front/docs/save.json";
            String jsonContent = new String(Files.readAllBytes(Paths.get(jsonPath)));
            
            System.out.println("JSON文件读取成功，长度: " + jsonContent.length());
            
            // 检查edges部分
            if (jsonContent.contains("\"edges\"")) {
                System.out.println("✓ JSON包含edges部分");
                
                // 检查sourcePortID
                if (jsonContent.contains("sourcePortID")) {
                    System.out.println("✓ JSON包含sourcePortID字段");
                } else {
                    System.out.println("✗ JSON不包含sourcePortID字段");
                }
                
                // 统计edges数量
                String[] edgeMatches = jsonContent.split("\"sourceNodeID\"");
                System.out.println("发现的边数量: " + (edgeMatches.length - 1));
                
            } else {
                System.out.println("✗ JSON不包含edges部分");
            }
            
        } catch (IOException e) {
            System.out.println("读取文件失败: " + e.getMessage());
        }
    }
}