package com.shuidihuzhu.infra.config;

import com.shuidihuzhu.infra.listener.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigRefresher implements ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(ConfigRefresher.class);

    private final ConfigProcessor configProcessor;

    private ApplicationContext applicationContext;

    private ConfigClientProperties configClientProperties;

    private AtomicBoolean ready = new AtomicBoolean(false);

    private Map<String, Listener> listenerMap = new ConcurrentHashMap<>(16);

    public ConfigRefresher(ConfigProcessor configProcessor, ConfigClientProperties configClientProperties) {
        this.configProcessor = configProcessor;
        this.configClientProperties = configClientProperties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

    }
}
