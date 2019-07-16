package com.shuidihuzhu.infra.config;

import com.shuidihuzhu.infra.Exception.ConfigException;
import com.shuidihuzhu.infra.listener.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 默认处理器
 */
public class DefaultConfigProcessor implements ConfigProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigProcessor.class);

    @Override
    public void addListener(String appName, String profile, Listener listener) throws ConfigException {

    }


}
