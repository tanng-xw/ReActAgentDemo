package com.react.agentdemo.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 带缓冲的 ClientHttpResponse 包装类
 * 允许响应体被多次读取
 * 
 * @author Kimi
 */
public class BufferedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse original;
    private final byte[] body;

    /**
     * 构造函数
     * 
     * @param original 原始响应
     * @param body 已读取的响应体字节数组
     */
    public BufferedClientHttpResponse(ClientHttpResponse original, byte[] body) {
        this.original = original;
        this.body = body;
    }

    @Override
    public InputStream getBody() throws IOException {
        // 返回一个字节数组输入流，可以重复读取
        return new ByteArrayInputStream(body);
    }

    @Override
    public HttpHeaders getHeaders() {
        return original.getHeaders();
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return original.getStatusCode();
    }

    @Override
    public int getRawStatusCode() throws IOException {
        return original.getRawStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return original.getStatusText();
    }

    @Override
    public void close() {
        original.close();
    }
}
