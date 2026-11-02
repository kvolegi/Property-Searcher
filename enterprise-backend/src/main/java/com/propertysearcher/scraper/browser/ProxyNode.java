package com.propertysearcher.scraper.browser;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Proxy configuration node representing an HTTP or SOCKS residential proxy endpoint.
 */
public record ProxyNode(
        String host,
        int port,
        Proxy.Type type,
        String username,
        String password
) {
    public Proxy toJavaProxy() {
        return new Proxy(type != null ? type : Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }
}
