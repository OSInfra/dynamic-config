package com.shuidihuzhu.infra.config;

import com.shuidihuzhu.infra.Exception.ConfigException;
import com.shuidihuzhu.infra.listener.Listener;

/**
 * 配置处理器接口
 */
public interface ConfigProcessor {


    /**
     * 注册监听器
     *
     * @param appName  应用名称
     * @param profile  激活的环境
     * @param listener 监听器
     */
    void addListener(String appName, String profile, Listener listener) throws ConfigException;

}
