package com.propertysearcher.scraper.browser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe round-robin proxy rotator for scraping pipelines.
 */
@Slf4j
@Component
public class ProxyRotator {

    private final List<ProxyNode> pool = new ArrayList<>();
    private final AtomicInteger pointer = new AtomicInteger(0);

    public ProxyRotator() {
        pool.add(new ProxyNode("127.0.0.1", 8080, Proxy.Type.HTTP, null, null));
    }

    public synchronized void registerProxy(ProxyNode node) {
        if (node != null && !pool.contains(node)) {
            pool.add(node);
            log.info("[PROXY_ROTATOR] Added proxy node: {}:{}", node.host(), node.port());
        }
    }

    public ProxyNode getNextProxy() {
        if (pool.isEmpty()) return null;
        int idx = Math.abs(pointer.getAndIncrement() % pool.size());
        return pool.get(idx);
    }

    public int getPoolSize() {
        return pool.size();
    }
}
