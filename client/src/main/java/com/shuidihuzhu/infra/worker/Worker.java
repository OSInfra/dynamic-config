package com.shuidihuzhu.infra.worker;

import com.shuidihuzhu.infra.listener.Listener;

import java.util.List;

public interface Worker {

    /**
     * 添加监听器
     */
    void addListeners(String appName, String profile, List<? extends Listener> listeners);

}
