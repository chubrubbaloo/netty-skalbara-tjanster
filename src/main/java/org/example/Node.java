package org.example;

import io.netty.channel.Channel;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Getter
public class Node {

    private final ReentrantReadWriteLock lock;
    private final int port;
    private Process process;
    private int requests;
    private List<Channel> connections; // Lista på alla kanaler som är anslutna.

    public Node(int port) {
        this.port = port;
        this.requests = 0;
        this.connections = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void start() throws IOException {
        var args = new String[] {
                "java",
                "-jar",
                "spring.jar",
                "--server.port=" + port
        };

        this.process = Runtime
                .getRuntime()
                .exec(args);

        System.out.println("Started node: " + process.pid());
    }

    public void stop() {
        if (process == null)
            return;

        process.destroyForcibly();
        System.out.println("Destroyed node: " + process.pid());
        process = null;
    }


    public void addConnection(Channel channel) {
        try {
            lock.writeLock().lock();
            this.connections.add(channel);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Lägger till en request för varje meddelande.
    public void addRequest() {
        try {
            lock.writeLock().lock();
            this.requests++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeConnection(Channel channel) {
        try {
            lock.writeLock().lock();
            this.connections.remove(channel);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Varje sek så resettar vi den till 0 för att kunna räkna ut hur många req / sek.
    public void resetRequests() {
        try {
            lock.writeLock().lock();
            this.requests = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
