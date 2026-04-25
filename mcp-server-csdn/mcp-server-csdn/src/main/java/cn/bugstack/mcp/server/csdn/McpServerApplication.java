package cn.bugstack.mcp.server.csdn;

import cn.bugstack.mcp.server.csdn.domain.service.CSDNArticleService;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.ICSDNService;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNApiProperties;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@SpringBootApplication
public class McpServerApplication implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(McpServerApplication.class);

    @Resource
    private CSDNApiProperties csdnApiProperties;

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
    /**
     * 构建并注册 ICSDNService 的 Spring Bean（CSDN API 调用的核心网关接口）
     * 基于 Retrofit 框架实现 HTTP 客户端，简化 RESTful API 调用
     * @return ICSDNService Retrofit 动态生成的接口实现类，用于调用 CSDN 业务 API
     */
    @Bean
    public ICSDNService csdnService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://bizapi.csdn.net/")
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        return retrofit.create(ICSDNService.class);
    }


    /**
     csdnTools() Bean（核心）：通过 MethodToolCallbackProvider 把 CSDNArticleService（CSDN 业务服务）注册为 Spring AI 工具对象—— 这一步是关键，它告诉 Spring AI：“这个 CSDNArticleService 里的方法是 AI 可以调用的工具”；
     */
    @Bean
    public ToolCallbackProvider csdnTools(CSDNArticleService csdnArticleService) {
        return MethodToolCallbackProvider.builder().toolObjects(csdnArticleService).build();
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("check csdn cookie ...");
        if (csdnApiProperties.getCookie() == null) {
            log.warn("csdn cookie key is null, please set it in application.yml");
        } else {
            log.info("csdn cookie  key is {}", csdnApiProperties.getCookie());
        }
    }

}
