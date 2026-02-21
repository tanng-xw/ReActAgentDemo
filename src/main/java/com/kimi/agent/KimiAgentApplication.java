package com.kimi.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能音乐助手应用程序入口
 * 
 * @author Kimi
 */
@SpringBootApplication
public class KimiAgentApplication {

    /**
     * 应用程序主入口方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(KimiAgentApplication.class, args);
    }
}
