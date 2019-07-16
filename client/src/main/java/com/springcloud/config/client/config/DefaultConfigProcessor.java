package com.springcloud.config.client.config;

import com.google.common.collect.Lists;
import com.springcloud.config.client.exception.ConfigException;
import com.springcloud.config.client.listener.Listener;
import com.springcloud.config.client.worker.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


/**
 * 默认处理器
 */
public class DefaultConfigProcessor implements ConfigProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigProcessor.class);

    private Handler handler;

    public DefaultConfigProcessor(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void addListener(String appName, String configFileName, String version, Map<String, Object> configValue, Listener listener) throws ConfigException {
        handler.addListeners(appName, configFileName, version, configValue, Lists.newArrayList(listener));
    }

}
