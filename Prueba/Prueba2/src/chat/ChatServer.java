package chat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Servidor de presencia.
 * - Recibe JOIN / LEAVE por UDP unicast.
 * - Mantiene la lista de usuarios por sala.
 * - Cada vez que cambia, manda USER_LIST por multicast a la sala.
 */
public class ChatServer {

    private static class ClientInfo {
        InetAddress ip;
        int privPort;

        ClientInfo(InetAddress ip, int privPort) {
            this.ip = ip;
            this.privPort = privPort;
        }
    }

    private static class Room {
        String name;
        String maddr;
        int mport;
        Map<String, ClientInfo> clients = new HashMap<>();

        Room(String name, String maddr, int mport) {
            this.name = name;
            this.maddr = maddr;
            this.mport = mport;
        }
    }

    // sala → Room
    private final Map<String, Room> rooms = new HashMap<>();
    private final DatagramSocket socket;

    public ChatServer(int port) throws SocketException {
        socket = new DatagramSocket(port);
        System.out.println("Servidor iniciado en puerto " + port);
    }

    public void loop() throws IOException {
        byte[] buf = new byte[4096];

        while (true) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);

            // Formato control:
            // JOIN|nick|sala|maddr|mport|privPort
            // LEAVE|nick|sala
            String[] parts = msg.split("\\|");
            String cmd = parts[0];

            if ("JOIN".equals(cmd) && parts.length == 6) {
                handleJoin(parts, p.getAddress());
            } else if ("LEAVE".equals(cmd) && parts.length == 3) {
                handleLeave(parts);
            } else {
                System.out.println("Comando desconocido: " + msg);
            }
        }
    }

    private void handleJoin(String[] parts, InetAddress addr) throws IOException {
        String nick = parts[1];
        String sala = parts[2];
        String maddr = parts[3];
        int mport = Integer.parseInt(parts[4]);
        int privPort = Integer.parseInt(parts[5]);

        Room room = rooms.get(sala);
        if (room == null) {
            room = new Room(sala, maddr, mport);
            rooms.put(sala, room);
        }

        room.clients.put(nick, new ClientInfo(addr, privPort));
        System.out.println("JOIN: " + nick + " -> sala " + sala);
        sendUserList(room);
    }

    private void handleLeave(String[] parts) throws IOException {
        String nick = parts[1];
        String sala = parts[2];
        Room room = rooms.get(sala);
        if (room == null) return;

        room.clients.remove(nick);
        System.out.println("LEAVE: " + nick + " de sala " + sala);

        if (room.clients.isEmpty()) {
            rooms.remove(sala);
            System.out.println("Sala " + sala + " vacía, eliminada");
        } else {
            sendUserList(room);
        }
    }

    /**
     * Envía por multicast a la sala:
     * USER_LIST|sala|nick1@ip:privPort;nick2@ip:privPort;...
     */
    private void sendUserList(Room room) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("USER_LIST|").append(room.name).append("|");
        boolean first = true;
        for (Map.Entry<String, ClientInfo> e : room.clients.entrySet()) {
            if (!first) sb.append(";");
            first = false;
            ClientInfo ci = e.getValue();
            sb.append(e.getKey())
              .append("@")
              .append(ci.ip.getHostAddress())
              .append(":")
              .append(ci.privPort);
        }

        String payload = sb.toString();
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);

        InetAddress maddr = InetAddress.getByName(room.maddr);
        DatagramPacket p = new DatagramPacket(data, data.length, maddr, room.mport);
        socket.send(p);

        System.out.println("USER_LIST enviada a sala " + room.name + ": " + payload);
    }

    public static void main(String[] args) throws Exception {
        int port = 4446;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        ChatServer server = new ChatServer(port);
        server.loop();
    }
}

