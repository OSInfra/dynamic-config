package com.springcloud.config.client.listener;

import java.util.concurrent.Executor;

/**
 * 配置处理器-监听器
 */
public interface Listener {

    /**
     * 执行监听事件
     */
    void executeEvent();

    /**
     * 定义执行处理器
     */
    Executor getExecutor();


}
