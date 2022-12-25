package org.example.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.example.Node;

// Handlern för anslutningen till noden.
public class RelayHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final Node node;
    private final Channel channel;

    public RelayHandler(Node node, Channel channel) {
        this.node = node;
        this.channel = channel;
    }

    // När vi väl har anslutit till noden då sparar vi den anslutningen / kanalen i noden så att den kan komma ihåg den.
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        node.addConnection(ctx.channel());
    }

    // När vi kopplar bort oss så tar vi bort den kanalen från connection-listan.
    // På så sätt har vi kolla på vilka kanaler som är anslutna vid en given tidpunkt.
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        node.removeConnection(ctx.channel());
    }

    // Här hanterar vi addRequests.
    // Här räknar den hur många req / sek vi kan få.
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        channel.writeAndFlush(buf.copy());
        node.addRequest();
    }

    // Får vi exceptions så stänger vi ner kanalerna så att vi inte fortsätter att använda dem.
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        channel.close();
    }
}
