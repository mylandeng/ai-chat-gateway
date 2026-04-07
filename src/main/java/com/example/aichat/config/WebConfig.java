package com.example.aichat.config;

import com.example.aichat.interceptor.ApiKeyInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private ApiKeyInterceptor apiKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor)
            .addPathPatterns("/api/chat/**")
            .addPathPatterns("/api/sessions/**")
            .addPathPatterns("/api/usage/**")
            .addPathPatterns("/api/templates/**")
            .addPathPatterns("/api/rag/**")
            .addPathPatterns("/api/agents/**")
            .addPathPatterns("/api/workflows/**")
            .excludePathPatterns("/api/models")
            .excludePathPatterns("/api/keys/**")
            .excludePathPatterns("/api/tenants/**")
            .excludePathPatterns("/api/templates/market")
            .excludePathPatterns("/api/rag/share/**");
    }
}
