package com.react.agentdemo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面控制器
 * 处理页面路由
 * 
 * @author Kimi
 */
@Controller
public class PageController {

    /**
     * 处理根路径请求，重定向到首页
     * 
     * @return 重定向到 index.html
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }
}
