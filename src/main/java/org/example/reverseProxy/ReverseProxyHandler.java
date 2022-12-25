package org.example.reverseProxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.example.client.RelayInitializer;

// Hanterar inkommande meddelanden.
public class ReverseProxyHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final ReverseProxyServer server;
    private Channel channel;

    public ReverseProxyHandler(ReverseProxyServer server) {
        this.server = server;
    }

    // Anslutningen.
    // När vi väl har anslutit då ansluter vi till en nod
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        var bootstrap = new Bootstrap();

        // Ansluter till en nod.
        var node = server.getNodeHandler().next();

        try {
            // Kopplar den med bootstrapen.
            this.channel = bootstrap
                    .group(server.getWorkerGroup())
                    .channel(NioSocketChannel.class)
                    .handler(new RelayInitializer(node, ctx.channel())) // Originella kanelen.
                    .connect("localhost", node.getPort())
                    .channel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channel.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        channel.writeAndFlush(buf.copy()); // När vi får in ett meddelande då använder vi bara den anslutningen och skickar
        // meddelandet så det alltid går till samma nod.
    }

    // Liten enkel felhantering.
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
        channel.close();
    }
}
