package com.shuidihuzhu.infra.configuration;

import com.shuidihuzhu.infra.worker.DefaultWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.client.ConfigClientProperties;
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

    @Bean
    @ConditionalOnMissingBean(DefaultWorker.class)
    @ConditionalOnProperty(value = "spring.cloud.config.enabled", matchIfMissing = true)
    public DefaultWorker defaultWorker(RestTemplate restTemplate, ConfigurableEnvironment environment, ConfigClientProperties configClientProperties) {
        return new DefaultWorker(restTemplate, environment, configClientProperties);
    }
}
