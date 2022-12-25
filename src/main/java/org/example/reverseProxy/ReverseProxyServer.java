package org.example.reverseProxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.example.NodeHandler;
import org.example.loadBalancer.RoundRobin;

import java.util.Scanner;

public class ReverseProxyServer {

    private final NodeHandler nodeHandler;

    private final int port;

    // Motorn i netty det som får allt att fungera.
    // Hanterar alla events som t ex inkommande meddelande, utgående meddelande, fel som uppstår, anslutningar som kommer in eller någon som bortkopplar sig.
    // Netty vill att det ska vara eventdrivet för det är effektivt för högre belastningar.
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public ReverseProxyServer(int port) {
        this.port = port;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
        this.nodeHandler = new NodeHandler(this, new RoundRobin()); // När man skapar nodehandlern så lägger vi in vår RoundRobinAlgoritm.
    }

    public void start() {
        var serverBootstrap = new ServerBootstrap(); // För att starta igång själva servern.

        try {
            // Konfigurerar servern för att ta in anslutningar.
            var channel = serverBootstrap
                    .group(bossGroup, workerGroup) // Lägger in våra EventLoopGroups.
                    .channel(NioServerSocketChannel.class)// Vilket typ av protokoll vi ska använda (TCP).
                    .childHandler(new ReverseProxyInitializer(this)) // Lägger in vår ReverseProxyInitializer i vår handler för våra kanaler (logik för våra meddelanden).
                    .bind(port).sync().channel(); // Binder och synkar porten vi valt till kanalen.

            var scanner = new Scanner(System.in);
            while (!scanner.nextLine().equals("exit")) {

            }

            // Stänger ner hela proxy servern och alla EventGroupLoops.
            nodeHandler.closeAll();
            channel.close();

            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public NodeHandler getNodeHandler() {
        return nodeHandler;
    }
}
