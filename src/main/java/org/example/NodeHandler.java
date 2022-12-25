package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.example.loadBalancer.RoundRobinInterface;
import org.example.client.NodeStartupInitializer;
import org.example.Node;
import org.example.reverseProxy.ReverseProxyServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NodeHandler {

    private final ReverseProxyServer server;
    private final RoundRobinInterface balancer;

    // Locks för att låsa mellan trådarna då vi delar data. När en tråd väl är färdig får nästa tråd köra.
    // Varje anslutning/request får en egen tråd.
    private final ReentrantReadWriteLock lock;
    private boolean alive = true;

    private int portCounter;
    private final List<Node> activeNodes;
    private final List<Node> closingQueue;
    private final List<Node> startingQueue;
    private final int minimumAmountOfNodes;

    public NodeHandler(ReverseProxyServer server, RoundRobinInterface balancer) {
        this.server = server;
        this.balancer = balancer;
        this.activeNodes = new ArrayList<>(); // Alla noder som är aktiva och vi vill använda när vi vill hantera lastbalanseringen.
        this.startingQueue = new ArrayList<>(); // Vänta på att noder har startat upp innan vi lägger till dem i de aktiva noderna.
        this.closingQueue = new ArrayList<>(); // För att stänga ner noder som inte riktigt är redo att stängas ner än.
        this.lock = new ReentrantReadWriteLock();  // För säkerheten.
        this.minimumAmountOfNodes = 2;
        this.portCounter = 8080;

        this.start(minimumAmountOfNodes); // Startar x noder från början beroende på hur många vi hårdkodat in.
        this.startThread(); // Startar tråden.
    }

    public void addStartedNode(Node node) {
        try {
            // Locks för att låsa mellan trådarna då vi delar data. När en tråd väl är färdig får nästa tråd köra.
            // Varje anslutning/request får en egen tråd.
            lock.writeLock().lock();

            // Enbart för att printa ut inputen för debugging syften.
            var input = node.getProcess().getInputStream();
            var bytes = new byte[10000];
            var read = input.read(bytes);
            System.out.println(new String(bytes, 0, read));

            startingQueue.remove(node); // Tar bort noden från startingQueue-listan.
            activeNodes.add(node); // Lägger till noden till vår activeNode-lista.
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Node next() {
        try {
            lock.readLock().lock();
            return balancer.next(activeNodes);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Startar upp X-antal noder.
    private void start(int amount) {
        System.out.println("Starting " + amount + " nodes.");
        for (int i = 0; i < amount; i++) {
            var node = new Node(portCounter++); // Loopar och skapar en ny nod.
            startingQueue.add(node); // Lägger in noden i vår starting-queue.
            // Lägger in i startingQueue först istället för activeQueue då det tar några sekunder för en nod att starta(spring boot).

            try {
                node.start(); // Sedan så startar den noden.
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Itererar vi över alla starting noder varje gång.
    // Sen försöker vi ansluta till den noden och i initializern

    // Den kommer att starta en nod. Vänta tills den har startat och sen lägga in den i de aktiva noderna.
    private void checkup() {
        // Hanterar starting queue listan.
        var iterator = startingQueue.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();

            try {
                var bootstrap = new Bootstrap();
                bootstrap
                        .group(server.getWorkerGroup())
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                        .handler(new NodeStartupInitializer(node, this))
                        .connect("localhost", node.getPort())
                        .sync();

            } catch (Exception ignored) {

            }
        }

        // Hanterar closing queue listan.
        // Loopar genom alla noder som ska stängas.
        //
        iterator = closingQueue.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();

            // Den kollar om connectionslistan är tom (finns det någon som är ansluten till denna noden).
            // OM den gör det så väntar vi.
            // Är den tom däremot (om det inte längre finns några) så kan vi stoppa den.
            if (!node.getConnections().isEmpty()) continue;

            node.stop();
            iterator.remove(); // Tar bort den från själva closingQueue-listan.
        }

        // Hanterar active nodes listan med att kunna stoppa och starta nya noder.
        // Räknar hur många requests / sekund som noderna hanterar.
        var requests = 0.0;
        for (var node : activeNodes) {
            requests += node.getRequests();
            node.resetRequests();
        }

        requests = requests / (double) activeNodes.size();

        System.out.println("Requests/Second: " + requests);


         // Om den har mindre än 1 req / sek så stänger vi en nod fram tills minimum antalet.
        if (requests < 1.0 && activeNodes.size() > minimumAmountOfNodes) {
            closeOne();

            // OM det är mer än 7 req / sek då kommer den starta nya noder.
        } else if (requests >= 7.0) {
            int amount = (int) (requests / 7.0);
            amount -= startingQueue.size();

            if (amount > 0)
                start(amount);
        }
    }

    // Vi hämtar den sista noden.
    // Vi tar bort den från de aktiva noderna.
    // MEN dem som fortfarande är anslutna till noden kan fortsätta att prata med noden.
    // Avslutningsvis lägger vi till noden i vår closingQueue-lista.
    // Allt den gör är att vänta tills det längre inte finns några connections och sen så stoppar den.
    private void closeOne() {
        System.out.println("Closing a node.");

        var i = activeNodes.size() - 1;
        var node = activeNodes.get(i);
        activeNodes.remove(i);

        closingQueue.add(node);
    }

    // Stänger ner hela hanteraren.
    public void closeAll() {
        try {
            lock.writeLock().lock();
            alive = false;
            activeNodes.forEach(Node::stop);
            closingQueue.forEach(Node::stop);
            startingQueue.forEach(Node::stop);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Tråden är till för att hantera alla noder. Starta nya, stänga gamla osv.
    private void startThread() {
        var thread = new Thread(this::runThread);

        thread.start();
    }

    private void runThread() {
        try {
            lock.writeLock().lock(); // Låser.
            if (!alive) { // Fortsätt loopar tråden så länge vi är "alive".
                return;
            }
            // Checkup-metoden hanterar alla noder.
            checkup();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock(); //
        }

        // En gång per sekund så kommer den att göra hanteringar för alla noder som vi har.
        try {
            Thread.sleep(1000);
        } catch(Exception e) {
            e.printStackTrace();
        }

        runThread();
    }
}
