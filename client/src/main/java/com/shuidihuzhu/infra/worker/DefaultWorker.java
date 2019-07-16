package com.shuidihuzhu.infra.worker;

import com.shuidihuzhu.infra.config.ConfigCache;
import com.shuidihuzhu.infra.listener.Listener;
import com.shuidihuzhu.infra.thread.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigClientStateHolder;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.springframework.cloud.config.client.ConfigClientProperties.*;

public class DefaultWorker implements Worker {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWorker.class);

    private static final ConcurrentHashMap<String, ConfigCache> cacheMap = new ConcurrentHashMap<>(16);

    private RestTemplate restTemplate;

    private ConfigClientProperties configClientProperties;

    private ScheduledExecutorService workerExecutor;

    private ScheduledExecutorService taskExecutor;

    public DefaultWorker(RestTemplate restTemplate, Environment environment, ConfigClientProperties configClientProperties) {
        this.restTemplate = restTemplate;
        this.configClientProperties = configClientProperties;
        this.workerExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("cloud-config-defaultworker"));

        this.taskExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2, new NamedThreadFactory("cloud-config-defaultworker-pulltask"));

        this.workerExecutor.scheduleWithFixedDelay(() -> {
            try {
                checkConfigData();
            } catch (Exception e) {
                logger.error("pull task error", e);
            }
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    private void checkConfigData() {

        if (cacheMap.size() > 0) {
            taskExecutor.execute(new PullTaskRunnable());
        }

    }

    @Override
    public void addListeners(String appName, String profile, List<? extends Listener> listeners) {

    }

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

            logger.info("Fetching config from server at : " + uri);

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
                logger.info("Connect Timeout Exception on Url - " + uri
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

            org.springframework.cloud.config.environment.Environment result = response.getBody();
            return result;
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

    class PullTaskRunnable implements Runnable {

        @Override
        public void run() {
            Exception error = null;
            String errorBody = null;
            try {
                String[] labels = new String[]{""};
                if (StringUtils.hasText(configClientProperties.getLabel())) {
                    labels = StringUtils
                            .commaDelimitedListToStringArray(configClientProperties.getLabel());
                }
                String state = ConfigClientStateHolder.getState();
                // Try all the labels until one works
                for (String label : labels) {
                    org.springframework.cloud.config.environment.Environment result = getRemoteEnvironment(restTemplate, configClientProperties,
                            label.trim(), state);
                    if (result != null) {
                        if (result.getPropertySources() != null) { // result.getPropertySources()


                            for (PropertySource source : result.getPropertySources()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) source
                                        .getSource();
                            }
                        }
                    }
                }
            } catch (HttpServerErrorException e) {
                error = e;
                if (MediaType.APPLICATION_JSON
                        .includes(e.getResponseHeaders().getContentType())) {
                    errorBody = e.getResponseBodyAsString();
                }
            } catch (Exception e) {
                error = e;
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
}
