package chat;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente de consola.
 * Usa comandos tipo:
 *   /join general 230.0.0.1 5000
 *   /msg general Hola :)
 *   /sticker general corazon
 *   /pm Juan hola en privado
 *   /leave general
 *   /quit
 */
public class ChatClient {

    private final String nick;
    private final InetAddress serverHost;
    private final int serverPort;

    // socket para privados
    private final DatagramSocket privSocket;

    // sala -> RoomSession
    private final Map<String, RoomSession> rooms = new ConcurrentHashMap<>();

    // usuario -> ip:puertoPriv (actualizado desde USER_LIST)
    private final Map<String, InetSocketAddress> directory = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    private static class RoomSession {
        String name;
        String maddr;
        int mport;
        MulticastSocket msock;
        InetAddress group;

        RoomSession(String name, String maddr, int mport, MulticastSocket msock, InetAddress group) {
            this.name = name;
            this.maddr = maddr;
            this.mport = mport;
            this.msock = msock;
            this.group = group;
        }
    }

    public ChatClient(String nick, String host, int port) throws Exception {
        this.nick = nick;
        this.serverHost = InetAddress.getByName(host);
        this.serverPort = port;
        this.privSocket = new DatagramSocket(0); // puerto aleatorio libre
        System.out.println("Cliente " + nick + " iniciado. Puerto privados: " + privSocket.getLocalPort());
    }

    // --------- envío de comandos al servidor ---------

    private void sendJoin(String sala, String maddr, int mport) throws IOException {
        String msg = "JOIN|" + nick + "|" + sala + "|" + maddr + "|" + mport + "|" + privSocket.getLocalPort();
        sendToServer(msg);
    }

    private void sendLeave(String sala) throws IOException {
        String msg = "LEAVE|" + nick + "|" + sala;
        sendToServer(msg);
    }

    private void sendToServer(String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, serverHost, serverPort);
        new DatagramSocket().send(p);
    }

    // --------- recepción de privados ---------

    private void startPrivateListener() {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    privSocket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    handlePrivate(msg);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error en privados: " + e.getMessage());
                    }
                }
            }
        }, "PrivListener");
        t.setDaemon(true);
        t.start();
    }

    private void handlePrivate(String msg) {
        // Formato: PM|from|to|contenido
        String[] parts = msg.split("\\|", 4);
        if (parts.length == 4 && "PM".equals(parts[0])) {
            String from = parts[1];
            String to = parts[2];
            String content = parts[3];
            System.out.println("[PM] " + from + " -> " + to + ": " + content);
        } else {
            System.out.println("[Privado] " + msg);
        }
    }

    // --------- gestión de salas ---------

    private void joinRoom(String sala, String maddr, int mport) throws IOException {
        if (rooms.containsKey(sala)) {
            System.out.println("Ya estás en la sala " + sala);
            return;
        }

        MulticastSocket ms = new MulticastSocket(mport);
        InetAddress group = InetAddress.getByName(maddr);
        ms.joinGroup(group);

        RoomSession rs = new RoomSession(sala, maddr, mport, ms, group);
        rooms.put(sala, rs);
        startRoomListener(rs);

        sendJoin(sala, maddr, mport);

        System.out.println("Unido a sala " + sala + " (" + maddr + ":" + mport + ")");
    }

    private void leaveRoom(String sala) throws IOException {
        RoomSession rs = rooms.remove(sala);
        if (rs == null) {
            System.out.println("No estás en la sala " + sala);
            return;
        }
        rs.msock.leaveGroup(rs.group);
        rs.msock.close();
        sendLeave(sala);
        System.out.println("Saliste de la sala " + sala);
    }

    private void startRoomListener(RoomSession rs) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    rs.msock.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    handleRoomMessage(rs, msg);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error en sala " + rs.name + ": " + e.getMessage());
                    }
                    break;
                }
            }
        }, "Room-" + rs.name);
        t.setDaemon(true);
        t.start();
    }

    private void handleRoomMessage(RoomSession rs, String msg) {
        // USER_LIST|sala|nick@ip:port;...
        if (msg.startsWith("USER_LIST|")) {
            String[] parts = msg.split("\\|", 3);
            if (parts.length == 3) {
                String sala = parts[1];
                if (!sala.equals(rs.name)) return;

                directory.clear();
                if (!parts[2].isEmpty()) {
                    String[] users = parts[2].split(";");
                    for (String u : users) {
                        String[] up = u.split("@");
                        if (up.length != 2) continue;
                        String nick = up[0];
                        String[] hp = up[1].split(":");
                        if (hp.length != 2) continue;
                        try {
                            InetAddress ip = InetAddress.getByName(hp[0]);
                            int port = Integer.parseInt(hp[1]);
                            directory.put(nick, new InetSocketAddress(ip, port));
                        } catch (Exception ignored) { }
                    }
                }

                System.out.println("[Sala " + sala + "] Usuarios activos: " + directory.keySet());
            }
            return;
        }

        // Mensajes de sala: pueden ser XML-ish
        System.out.println("[Sala " + rs.name + "] " + msg);
    }

    // --------- envío de mensajes públicos ---------

    private void sendText(String sala, String text) throws IOException {
        RoomSession rs = rooms.get(sala);
        if (rs == null) {
            System.out.println("No estás en sala " + sala);
            return;
        }

        String xml = "<msg><usr>" + nick + "</usr><sala>" + sala +
                "</sala><content>" + text + "</content></msg>";
        byte[] data = xml.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, rs.group, rs.mport);
        rs.msock.send(p);
    }

    private void sendSticker(String sala, String stickerName) throws IOException {
        RoomSession rs = rooms.get(sala);
        if (rs == null) {
            System.out.println("No estás en sala " + sala);
            return;
        }

        File f = new File("stickers", stickerName + ".png");
        if (!f.exists()) {
            System.out.println("Sticker no encontrado: " + f.getAbsolutePath());
            return;
        }

        byte[] bytes = readAllBytes(f);
        String b64 = Base64.getEncoder().encodeToString(bytes);

        String xml = "<sticker><usr>" + nick + "</usr><sala>" + sala +
                "</sala><name>" + stickerName +
                "</name><data>" + b64 + "</data></sticker>";

        byte[] data = xml.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, rs.group, rs.mport);
        rs.msock.send(p);

        System.out.println("Sticker " + stickerName + " enviado a sala " + sala);
    }

    // En este diseño el receptor solo ve el XML en consola;
    // si luego hacemos GUI, ahí decodificamos Base64 y dibujamos la imagen.

    // --------- envío de privados ---------

    private void sendPrivate(String destinatario, String content) throws IOException {
        InetSocketAddress addr = directory.get(destinatario);
        if (addr == null) {
            System.out.println("No conozco al usuario " + destinatario + " en la USER_LIST");
            return;
        }
        String msg = "PM|" + nick + "|" + destinatario + "|" + content;
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, addr.getAddress(), addr.getPort());
        privSocket.send(p);
        System.out.println("[PM] (tú -> " + destinatario + "): " + content);
    }

    // --------- util ---------

    private static byte[] readAllBytes(File f) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    // --------- loop de comandos ---------

    private void commandLoop() throws IOException {
        startPrivateListener();

        System.out.println("Comandos:");
        System.out.println("  /join sala maddr mport");
        System.out.println("  /leave sala");
        System.out.println("  /msg sala mensaje...");
        System.out.println("  /sticker sala nombreSticker");
        System.out.println("  /pm usuario mensaje...");
        System.out.println("  /quit");

        Scanner sc = new Scanner(System.in);

        while (running) {
            System.out.print("> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine();
            if (line.trim().isEmpty()) continue;

            if (line.startsWith("/join ")) {
                String[] p = line.split("\\s+");
                if (p.length == 4) {
                    String sala = p[1];
                    String maddr = p[2];
                    int mport = Integer.parseInt(p[3]);
                    joinRoom(sala, maddr, mport);
                } else {
                    System.out.println("Uso: /join sala maddr mport");
                }
            } else if (line.startsWith("/leave ")) {
                String[] p = line.split("\\s+");
                if (p.length == 2) {
                    leaveRoom(p[1]);
                } else {
                    System.out.println("Uso: /leave sala");
                }
            } else if (line.startsWith("/msg ")) {
                String[] p = line.split("\\s+", 3);
                if (p.length >= 3) {
                    sendText(p[1], p[2]);
                } else {
                    System.out.println("Uso: /msg sala texto...");
                }
            } else if (line.startsWith("/sticker ")) {
                String[] p = line.split("\\s+");
                if (p.length == 3) {
                    sendSticker(p[1], p[2]);
                } else {
                    System.out.println("Uso: /sticker sala nombreSticker");
                }
            } else if (line.startsWith("/pm ")) {
                String[] p = line.split("\\s+", 3);
                if (p.length >= 3) {
                    sendPrivate(p[1], p[2]);
                } else {
                    System.out.println("Uso: /pm usuario mensaje...");
                }
            } else if (line.equals("/quit")) {
                running = false;
                break;
            } else {
                System.out.println("Comando no reconocido");
            }
        }

        // cierre
        for (String sala : new ArrayList<>(rooms.keySet())) {
            try { leaveRoom(sala); } catch (Exception ignored) {}
        }
        privSocket.close();
        System.out.println("Cliente cerrado.");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Uso: java chat.ChatClient <nick> <hostServidor> <puertoServidor>");
            return;
        }
        String nick = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        ChatClient c = new ChatClient(nick, host, port);
        c.commandLoop();
    }
}
