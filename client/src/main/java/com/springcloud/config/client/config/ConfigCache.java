package com.springcloud.config.client.config;

import com.springcloud.config.client.exception.ConfigException;
import com.springcloud.config.client.listener.Listener;
import com.springcloud.config.client.util.Safes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 配置缓存数据
 */
public class ConfigCache {

    private static final Logger logger = LoggerFactory.getLogger(ConfigCache.class);

    private String appName;

    private String configFileName;

    private String commitVersion;

    private Map<String, Object> configValue = new HashMap<>(16);

    private CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void notifyListener() {
        Safes.of(listeners).forEach(this::doNotifyListener);
    }

    private void doNotifyListener(Listener listener) {
        Runnable job = () -> {
            ClassLoader myClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader appClassLoader = listener.getClass().getClassLoader();
            try {

                // 执行回调之前先将线程classloader设置为具体webapp的classloader，以免回调方法中调用spi接口是出现异常或错用（多应用部署才会有该问题）。
                Thread.currentThread().setContextClassLoader(appClassLoader);
                listener.executeEvent();

                logger.info("appName:{},configFileName:{},commitVersion:{} notify success",
                        appName, configFileName, commitVersion);
            } catch (ConfigException ex) {
                logger.error("appName:{},configFileName:{},commitVersion:{} notify fail,errCode:{},errMsg:{}",
                        appName, configFileName, commitVersion, ex.getErrorCode(), ex.getErrMsg());
            } catch (Throwable t) {
                logger.error("appName:{},configFileName:{},commitVersion:{} notify fail,throwable:{}",
                        appName, configFileName, commitVersion, t.getCause());
            } finally {
                Thread.currentThread().setContextClassLoader(myClassLoader);
            }
        };

        try {
            if (null != listener.getExecutor()) {
                listener.getExecutor().execute(job);
            } else {
                job.run();
            }
        } catch (Throwable t) {
            logger.error("appName:{},configFileName:{},commitVersion:{} notify fail,throwable:{}",
                    appName, configFileName, commitVersion, t.getCause());
        }
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getConfigFileName() {
        return configFileName;
    }

    public void setConfigFileName(String configFileName) {
        this.configFileName = configFileName;
    }

    public String getCommitVersion() {
        return commitVersion;
    }

    public void setCommitVersion(String commitVersion) {
        this.commitVersion = commitVersion;
    }

    public CopyOnWriteArrayList<Listener> getListeners() {
        return listeners;
    }

    public Map<String, Object> getConfigValue() {
        return configValue;
    }

    public void setConfigValue(Map<String, Object> configValue) {
        this.configValue = configValue;
    }
}
