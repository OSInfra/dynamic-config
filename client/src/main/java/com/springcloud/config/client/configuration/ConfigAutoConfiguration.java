package com.springcloud.config.client.configuration;

import com.springcloud.config.client.config.ConfigRefresher;
import com.springcloud.config.client.config.DefaultConfigProcessor;
import com.springcloud.config.client.worker.DefaultHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties
public class ConfigAutoConfiguration {

    @Autowired
    private ConfigurableEnvironment environment;

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigClientProperties configClientProperties() {
        return new ConfigClientProperties(this.environment);
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "spring.cloud.config.enabled", matchIfMissing = true)
    public DefaultHandler defaultHandler(RestTemplate restTemplate, ConfigClientProperties configClientProperties) {
        return new DefaultHandler(restTemplate, configClientProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultConfigProcessor defaultConfigProcessor(DefaultHandler defaultHandler) {
        return new DefaultConfigProcessor(defaultHandler);
    }

    @Bean
    @ConditionalOnBean(ConfigServicePropertySourceLocator.class)
    public ConfigRefresher configRefresher(DefaultConfigProcessor defaultConfigProcessor, ConfigClientProperties configClientProperties) {
        return new ConfigRefresher(defaultConfigProcessor, configClientProperties);
    }
}
