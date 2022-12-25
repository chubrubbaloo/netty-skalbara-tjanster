package org.example.reverseProxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class ReverseProxyInitializer extends ChannelInitializer<SocketChannel> {

    private final ReverseProxyServer server;

    public ReverseProxyInitializer(ReverseProxyServer server) {
        this.server = server;
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        var pipeline = channel.pipeline();

        pipeline.addLast(new ReverseProxyHandler(server)); // Lägger in vår handler i pipelinen. Det objektet kommer hantera alla meddelanden till kanalen.
    }
}
