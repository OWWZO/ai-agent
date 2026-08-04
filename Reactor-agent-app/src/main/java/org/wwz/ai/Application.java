package org.wwz.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Reactor Agent Spring Boot 启动入口。
 *
 * <p>应用模块负责装配各层 Bean、开启事务并扫描 infrastructure DAO；业务规则仍由
 * domain/case 层承载，启动类本身不参与请求编排。</p>
 */
@SpringBootApplication
@Configurable
@EnableTransactionManagement
@MapperScan("org.wwz.ai.infrastructure.dao")
public class Application {

    public static void main(String[] args){
        // 由 Spring Boot 负责创建完整应用上下文和关闭阶段的资源生命周期。
        SpringApplication.run(Application.class, args);
    }

}
