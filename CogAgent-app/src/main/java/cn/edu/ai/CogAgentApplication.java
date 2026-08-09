package cn.edu.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI Agent 平台启动类
 */
@EnableAsync
@SpringBootApplication
public class CogAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CogAgentApplication.class, args);
    }
}
