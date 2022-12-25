package org.example;

import org.example.reverseProxy.ReverseProxyServer;

public class Main {
    public static void main(String[] args) {
        new ReverseProxyServer(8000).start();
    }
}