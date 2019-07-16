package com.springcloud.config.client.worker;

import com.google.common.collect.Lists;
import com.springcloud.config.client.config.ConfigCache;
import com.springcloud.config.client.listener.Listener;
import com.springcloud.config.client.thread.NamedThreadFactory;
import com.springcloud.config.client.util.Safes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigClientStateHolder;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.springframework.cloud.config.client.ConfigClientProperties.*;

@Order
public class DefaultHandler implements Handler, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(DefaultHandler.class);

    private static final ConcurrentHashMap<String, ConfigCache> cacheMap = new ConcurrentHashMap<>(16);

    private RestTemplate restTemplate;

    private ConfigClientProperties configClientProperties;

    private ApplicationContext applicationContext;

    private ScheduledExecutorService workerExecutor;

    private ScheduledExecutorService taskExecutor;

    private final Lock lock = new ReentrantLock();

    private final Condition done = lock.newCondition();

    public DefaultHandler(RestTemplate restTemplate, ConfigClientProperties configClientProperties) {
        this.restTemplate = restTemplate;
        this.configClientProperties = configClientProperties;
        this.taskExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("cloud-config-defaulthandler-schedule"));

        this.workerExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("cloud-config-defaulthandler-pulltask"));

        this.taskExecutor.scheduleWithFixedDelay(() -> {
            try {
                checkConfigData();
            } catch (Exception e) {
                logger.error("pull task error", e);
            }
        }, 1000L, 5000L, TimeUnit.MILLISECONDS);
    }

    private void checkConfigData() {

        if (cacheMap.size() > 0) {
            try {
                lock.lock();
                workerExecutor.execute(new PullTaskRunnable());
                done.await();
            } catch (InterruptedException e) {
                logger.error("execute schedule task error", e);
            } finally {
                lock.unlock();
            }
        }

    }

    @Override
    public void addListeners(String appName, String configFileName, String version, Map<String, Object> configValue, List<? extends Listener> listeners) {
        ConfigCache configCache = addCacheIfAbsent(appName, configFileName, configValue, version);
        Safes.of(listeners).forEach(configCache::addListener);
    }

    private ConfigCache addCacheIfAbsent(String appName, String configFileName, Map<String, Object> configValue, String version) {
        String cacheMapKey = appName + configFileName;
        ConfigCache configCache = cacheMap.get(cacheMapKey);
        if (Objects.nonNull(configCache)) {
            return configCache;
        }
        configCache = new ConfigCache();
        configCache.setAppName(appName);
        configCache.setConfigFileName(configFileName);
        configCache.setCommitVersion(version);
        configCache.setConfigValue(configValue);
        cacheMap.putIfAbsent(cacheMapKey, configCache);
        return configCache;
    }

    /**
     * 获取git config配置
     */
    private org.springframework.cloud.config.environment.Environment getRemoteEnvironment(RestTemplate restTemplate,
                                                                                          ConfigClientProperties properties, String label, String state) {
        if (restTemplate == null) {
            restTemplate = getSecureRestTemplate(properties);
        }
        String path = "/{name}/{profile}";
        String name = properties.getName();
        String profile = properties.getProfile();
        String token = properties.getToken();
        int noOfUrls = properties.getUri().length;
        if (noOfUrls > 1) {
            logger.info("Multiple Config Server Urls found listed.");
        }

        Object[] args = new String[]{name, profile};
        if (StringUtils.hasText(label)) {
            if (label.contains("/")) {
                label = label.replace("/", "(_)");
            }
            args = new String[]{name, profile, label};
            path = path + "/{label}";
        }
        ResponseEntity<org.springframework.cloud.config.environment.Environment> response = null;

        for (int i = 0; i < noOfUrls; i++) {
            ConfigClientProperties.Credentials credentials = properties.getCredentials(i);
            String uri = credentials.getUri();
            String username = credentials.getUsername();
            String password = credentials.getPassword();

//            logger.info("Fetching config from server at : " + uri);

            try {
                HttpHeaders headers = new HttpHeaders();
                addAuthorizationToken(properties, headers, username, password);
                if (StringUtils.hasText(token)) {
                    headers.add(TOKEN_HEADER, token);
                }
                if (StringUtils.hasText(state) && properties.isSendState()) {
                    headers.add(STATE_HEADER, state);
                }
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

                final HttpEntity<Void> entity = new HttpEntity<>((Void) null, headers);
                response = restTemplate.exchange(uri + path, HttpMethod.GET, entity,
                        org.springframework.cloud.config.environment.Environment.class, args);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                    throw e;
                }
            } catch (ResourceAccessException e) {
                logger.info("Connect Timeout exception on Url - " + uri
                        + ". Will be trying the next url if available");
                if (i == noOfUrls - 1) {
                    throw e;
                } else {
                    continue;
                }
            }

            if (response == null || response.getStatusCode() != HttpStatus.OK) {
                return null;
            }

            return response.getBody();
        }

        return null;
    }

    private void addAuthorizationToken(ConfigClientProperties configClientProperties,
                                       HttpHeaders httpHeaders, String username, String password) {
        String authorization = configClientProperties.getHeaders().get(AUTHORIZATION);

        if (password != null && authorization != null) {
            throw new IllegalStateException(
                    "You must set either 'password' or 'authorization'");
        }

        if (password != null) {
            byte[] token = Base64Utils.encode((username + ":" + password).getBytes());
            httpHeaders.add("Authorization", "Basic " + new String(token));
        } else if (authorization != null) {
            httpHeaders.add("Authorization", authorization);
        }
    }

    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    class PullTaskRunnable implements Runnable {

        @Override
        public void run() {
            try {
                lock.lock();
                String[] labels = new String[]{""};
                if (StringUtils.hasText(configClientProperties.getLabel())) {
                    labels = StringUtils.commaDelimitedListToStringArray(configClientProperties.getLabel());
                }
                String state = ConfigClientStateHolder.getState();
                // Try all the labels until one works
                for (String label : labels) {
                    org.springframework.cloud.config.environment.Environment result = getRemoteEnvironment(restTemplate, configClientProperties,
                            label.trim(), state);
                    if (result != null) {
//                        log(result);
                        String appName = result.getName();
                        String version = result.getVersion();
                        if (result.getPropertySources() != null) { // result.getPropertySources()

                            for (PropertySource source : result.getPropertySources()) {
                                Map<String, Object> configValue = new HashMap<>();
                                String cacheMapKey = appName + source.getName();

                                ConfigCache configCache = cacheMap.get(cacheMapKey);

                                for (Map.Entry<?, ?> curSource : source.getSource().entrySet()) {
                                    configValue.put(curSource.getKey().toString(), curSource.getValue());
                                }

                                if (Objects.isNull(configCache)) {
                                    newCacheIfNull(appName, source.getName(), configValue, version);
                                    continue;
                                }

                                if (version.equals(configCache.getCommitVersion())) {
                                    continue;
                                }

                                //遍历所有配置信息
                                Set<String> changeConfig = changes(configCache.getConfigValue(), configValue).keySet();
                                if (changeConfig.size() == 0) {
                                    continue;
                                }

                                configCache.setCommitVersion(version);
                                configCache.setConfigValue(configValue);
                                cacheMap.put(cacheMapKey, configCache);
                                configCache.notifyListener();
                            }
                        }
                    }
                }
                done.signal();
            } catch (Exception e) {
                logger.warn("pull task error", e);
                workerExecutor.schedule(this, 3000, TimeUnit.MILLISECONDS);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 如果cache为空，说明git新增了新的配置文件，需要重新添加监听器
     */
    private void newCacheIfNull(String appName, String configFileName, Map<String, Object> configValue, String version) {
        this.addListeners(appName, configFileName, version, configValue, Lists.newArrayList(new Listener() {
            @Override
            public void executeEvent() {
                applicationContext.publishEvent(new RefreshEvent(this, null, "Refresh git config"));
            }

            @Override
            public Executor getExecutor() {
                return null;
            }
        }));
    }

    private Map<String, Object> changes(Map<String, Object> before,
                                        Map<String, Object> after) {
        Map<String, Object> result = new HashMap<String, Object>();
        for (String key : before.keySet()) {
            if (!after.containsKey(key)) {
                result.put(key, null);
            } else if (!equal(before.get(key), after.get(key))) {
                result.put(key, after.get(key));
            }
        }
        for (String key : after.keySet()) {
            if (!before.containsKey(key)) {
                result.put(key, after.get(key));
            }
        }
        return result;
    }

    private boolean equal(Object one, Object two) {
        if (one == null && two == null) {
            return true;
        }
        if (one == null || two == null) {
            return false;
        }
        return one.equals(two);
    }

    private void log(org.springframework.cloud.config.environment.Environment result) {
        if (logger.isDebugEnabled()) {
            List<PropertySource> propertySourceList = result.getPropertySources();
            if (propertySourceList != null) {
                int propertyCount = 0;
                for (PropertySource propertySource : propertySourceList) {
                    propertyCount += propertySource.getSource().size();
                }
                logger.debug(String.format(
                        "Environment %s has %d property sources with %d properties.",
                        result.getName(), result.getPropertySources().size(),
                        propertyCount));
            }
        }
    }

    private RestTemplate getSecureRestTemplate(ConfigClientProperties client) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (client.getRequestReadTimeout() < 0) {
            throw new IllegalStateException("Invalid Value for Read Timeout set.");
        }
        requestFactory.setReadTimeout(client.getRequestReadTimeout());
        RestTemplate template = new RestTemplate(requestFactory);
        Map<String, String> headers = new HashMap<>(client.getHeaders());
        if (headers.containsKey(AUTHORIZATION)) {
            headers.remove(AUTHORIZATION); // To avoid redundant addition of header
        }
        if (!headers.isEmpty()) {
            template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
                    new ConfigServicePropertySourceLocator.GenericRequestHeaderInterceptor(headers)));
        }

        return template;
    }

    public void destroy() {
        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }

        if (workerExecutor != null) {
            workerExecutor.shutdown();
        }
    }
}
