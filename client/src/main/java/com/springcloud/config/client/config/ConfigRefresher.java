package com.springcloud.config.client.config;

import com.springcloud.config.client.constant.Constant;
import com.springcloud.config.client.exception.ConfigException;
import com.springcloud.config.client.listener.Listener;
import com.springcloud.config.client.util.Safes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.*;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfigRefresher implements ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(ConfigRefresher.class);

    private final ConfigProcessor configProcessor;

    private ConfigurableApplicationContext applicationContext;

    private ConfigClientProperties configClientProperties;

    private AtomicBoolean ready = new AtomicBoolean(false);

    private Map<String, Listener> listenerMap = new ConcurrentHashMap<>(16);

    public ConfigRefresher(ConfigProcessor configProcessor, ConfigClientProperties configClientProperties) {
        this.configProcessor = configProcessor;
        this.configClientProperties = configClientProperties;
    }

    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    @Override
    public void onApplicationEvent(@Nullable ApplicationReadyEvent event) {
        // many Spring context
        if (this.ready.compareAndSet(false, true)) {
            registerEnvironmentListener();
        }
    }

    private void registerEnvironmentListener() {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        CompositePropertySource source = (CompositePropertySource) environment.getPropertySources().get(Constant.BOOTSTRAP_PROPERTIES);

        if (Objects.isNull(source)) {
            logger.error("can not resolve bootstrapProperties");
            return;
        }

        Optional<PropertySource<?>> propertySourceOptional = Safes.of(source.getPropertySources())
                .stream()
                .filter(it -> Constant.CONFIG_SERVICE.equals(it.getName()))
                .findAny();

        if (!propertySourceOptional.isPresent()) {
            logger.error("can not get configService from bootstrapProperpties");
            return;
        }

        String gitVersion = "";
        CompositePropertySource configService = (CompositePropertySource) propertySourceOptional.get();


        for (PropertySource<?> currentSource : Safes.of(configService.getPropertySources())) {
            Map<String, Object> configValue = new HashMap<>();

            MapPropertySource mapPropertySource = (MapPropertySource) currentSource;

            if (Constant.CONFIG_CLIENT.equals(mapPropertySource.getName())) {
                gitVersion = (String) mapPropertySource.getProperty(Constant.CONFIG_CLIENT_VERSION);
                continue;
            }

            for (String key : ((EnumerablePropertySource<?>) mapPropertySource).getPropertyNames()) {
                configValue.put(key, mapPropertySource.getProperty(key));
            }

            registerConfigListener(configClientProperties.getName(), mapPropertySource.getName(), gitVersion, configValue);
        }
    }

    private void registerConfigListener(String appName, String configFileName, String gitVersion, Map<String, Object> configValue) {
        Listener listener = listenerMap.computeIfAbsent(composeListenerUniqueKey(appName, configFileName), it -> new Listener() {
            @Override
            public void executeEvent() {
                applicationContext.publishEvent(new RefreshEvent(this, null, "Refresh git config"));
            }

            @Override
            public Executor getExecutor() {
                return null;
            }
        });

        try {
            configProcessor.addListener(appName, configFileName, gitVersion, configValue, listener);
        } catch (ConfigException e) {
            logger.error("add listener error", e);
        }
    }

    private String composeListenerUniqueKey(String appName, String configFileName) {
        return appName + configFileName;
    }
}
