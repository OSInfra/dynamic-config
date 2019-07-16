package com.springcloud.config.client.config;

import com.springcloud.config.client.exception.ConfigException;
import com.springcloud.config.client.listener.Listener;

import java.util.Map;

/**
 * 配置处理器接口
 */
public interface ConfigProcessor {


    /**
     * 注册监听器
     *
     * @param appName        应用名称
     * @param configFileName 配置文件名称
     * @param version        git提交版本号
     * @param configValue    配置文件对应的配置值
     * @param listener       监听器
     */
    void addListener(String appName, String configFileName, String version, Map<String, Object> configValue, Listener listener) throws ConfigException;

}
