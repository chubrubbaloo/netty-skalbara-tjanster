package org.example.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.example.Node;
import org.example.NodeHandler;

public class NodeStartupInitializer extends ChannelInitializer<SocketChannel> {


    private final Node node; // Noden.
    private final NodeHandler nodeHandler; // Nodehandlern.

    public NodeStartupInitializer(Node node, NodeHandler nodeHandler) {
        this.node = node;
        this.nodeHandler = nodeHandler;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

    }

    // Vår initializer där vi lägger in en handler i pipelinen.
    // Allt det här är till för att se att när vi ansluter till noden då kan vi lägga till den i våra aktiva noder och stänga av vår koppling här.

    // Vi startar noden vi lägger in den i vår kö och så väntar vi tills vi faktiskt kan ansluta till noden och då
    // lägger vi in den i den aktiva nod-listan (activeQueue) för då är vi 100% säkra på att den faktiskt är startad för att sedan kunna användas.
    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        channel
                .pipeline()
                .addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        nodeHandler.addStartedNode(node);
                        ctx.close();
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {

                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    }
                });
    }
}
