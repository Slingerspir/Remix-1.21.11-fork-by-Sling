package cn.remix.ui.screen.impl.proxy;

import io.netty.handler.proxy.Socks5ProxyHandler;
import lombok.AllArgsConstructor;

import java.net.InetSocketAddress;

@AllArgsConstructor
public class Proxy {
    public String host;
    public int port;
    public String username;
    public String password;

    public Socks5ProxyHandler getHandler() {
        if (username != null && !username.isEmpty()) {
            return new Socks5ProxyHandler(new InetSocketAddress(host, port), username, password);
        }

        return new Socks5ProxyHandler(new InetSocketAddress(host, port));
    }
}