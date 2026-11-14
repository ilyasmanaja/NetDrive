package server;

import java.io.PrintWriter;
import java.net.InetAddress;

public class ClientSession {
    private final String username;
    private final PrintWriter out;
    private final InetAddress address;
    private final int udpPort;

    public ClientSession(String username, PrintWriter out, InetAddress address, int udpPort) {
        this.username = username;
        this.out = out;
        this.address = address;
        this.udpPort = udpPort;
    }

    public String getUsername() {
        return username;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public synchronized void sendMessage(String message) {
        out.println(message);
        out.flush();
    }
}