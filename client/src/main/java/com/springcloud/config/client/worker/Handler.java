package com.springcloud.config.client.worker;

import com.springcloud.config.client.listener.Listener;

import java.util.List;
import java.util.Map;

public interface Handler {

    /**
     * 添加监听器
     */
    void addListeners(String appName, String configFileName, String version, Map<String, Object> configValue, List<? extends Listener> listeners);

}
