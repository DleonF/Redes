package chat;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

/**
 * Cliente con interfaz gráfica:
 *  - Salas por multicast (JOIN / LEAVE con el servidor).
 *  - Mensajes públicos (texto / emojis).
 *  - Stickers desde carpeta stickers/.
 *  - Mensajes privados vía UDP unicast.
 *  - Audio fragmentado en varios datagramas UDP (audiofrag) y reensamblado.
 */
public class ChatClientGUI {

    private static final int AUDIO_CHUNK_SIZE = 4000; // bytes crudos por fragmento

    private final String nick;
    private final InetAddress serverHost;
    private final int serverPort;

    // socket para privados
    private final DatagramSocket privSocket;

    // sala -> sesión multicast
    private final Map<String, RoomSession> rooms = new ConcurrentHashMap<>();

    // usuario -> ip:puertoPriv (se actualiza desde USER_LIST de la sala activa)
    private final Map<String, InetSocketAddress> directory = new ConcurrentHashMap<>();

    private volatile boolean running = true;
    private volatile String activeRoom = null;

    // ---- Sesión de sala ----
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

    // ---- Estructura para reensamblar audio ----
    private static class AudioAssembly {
        String sala;
        String user;
        String name;
        int total;
        byte[][] parts;
        int received;

        AudioAssembly(String sala, String user, String name, int total) {
            this.sala = sala;
            this.user = user;
            this.name = name;
            this.total = total;
            this.parts = new byte[total][];
            this.received = 0;
        }
    }

    // clave: sala|user|name
    private final Map<String, AudioAssembly> audioAssemblies = new ConcurrentHashMap<>();

    private String audioKey(String sala, String user, String name) {
        return sala + "|" + user + "|" + name;
    }

    // ==== GUI ====
    private JFrame frame;
    private JTextPane chatPane;
    private JTextField msgField;
    private JComboBox<String> roomCombo;
    private DefaultComboBoxModel<String> roomComboModel;
    private JTextField salaField;
    private JTextField maddrField;
    private JTextField mportField;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;

    // stickers disponibles
    private static final String[] STICKER_NAMES = {
            "astuto", "beso", "cansado", "corazon",
            "enamorado", "enojado", "feliz", "gato",
            "lagrima", "lengua", "ok", "risa",
            "rock", "sonrisa", "triste"
    };

    // ---- Constructor ----
    public ChatClientGUI(String nick, String host, int port) throws Exception {
        this.nick = nick;
        this.serverHost = InetAddress.getByName(host);
        this.serverPort = port;
        this.privSocket = new DatagramSocket(0);
        System.out.println("Cliente GUI " + nick + " iniciado. Puerto privados: " + privSocket.getLocalPort());

        SwingUtilities.invokeAndWait(this::buildGUI);
        startPrivateListener();
    }

    // ==== Construcción de GUI ====
    private void buildGUI() {
        frame = new JFrame("Chat UDP - " + nick);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });

        // ---- Panel central: chat + lista usuarios ----
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(new TitledBorder("Mensajes"));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(new TitledBorder("Usuarios sala activa"));

        JSplitPane centerSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                chatScroll,
                userScroll
        );
        centerSplit.setResizeWeight(0.8);

        frame.add(centerSplit, BorderLayout.CENTER);

        // ---- Panel inferior: mensaje + botones ----
        JPanel bottomPanel = new JPanel(new BorderLayout());

        msgField = new JTextField();
        JButton sendBtn = new JButton("Enviar");
        sendBtn.addActionListener(e -> enviarMensaje());

        JPanel msgPanel = new JPanel(new BorderLayout());
        msgPanel.add(msgField, BorderLayout.CENTER);
        msgPanel.add(sendBtn, BorderLayout.EAST);
        msgPanel.setBorder(new TitledBorder("Mensaje (texto / emojis)"));

        bottomPanel.add(msgPanel, BorderLayout.NORTH);

        // ---- Panel de stickers y audio ----
        JPanel mediaPanel = new JPanel(new BorderLayout());

        JPanel stickersPanel = new JPanel(new GridLayout(3, 5, 5, 5));
        stickersPanel.setBorder(new TitledBorder("Stickers"));

        for (String name : STICKER_NAMES) {
            File f = new File("stickers", name + ".png");
            JButton b;
            if (f.exists()) {
                ImageIcon icon = new ImageIcon(f.getPath());
                Image img = icon.getImage().getScaledInstance(40, 40, Image.SCALE_SMOOTH);
                icon = new ImageIcon(img);
                b = new JButton(icon);
            } else {
                b = new JButton(name);
            }
            b.setToolTipText(name);
            b.addActionListener(e -> enviarSticker(name));
            stickersPanel.add(b);
        }

        JButton audioBtn = new JButton("Enviar audio");
        audioBtn.addActionListener(e -> enviarAudio());

        mediaPanel.add(stickersPanel, BorderLayout.CENTER);
        mediaPanel.add(audioBtn, BorderLayout.SOUTH);

        bottomPanel.add(mediaPanel, BorderLayout.CENTER);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        // ---- Panel superior: salas y control ----
        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel joinPanel = new JPanel(new GridLayout(2, 4, 5, 2));
        joinPanel.setBorder(new TitledBorder("Conexión a sala"));

        salaField = new JTextField("main");
        maddrField = new JTextField("230.0.0.1");
        mportField = new JTextField("5000");

        JButton joinBtn = new JButton("JOIN");
        joinBtn.addActionListener(e -> joinFromFields());

        JButton leaveBtn = new JButton("LEAVE");
        leaveBtn.addActionListener(e -> leaveActiveRoom());

        joinPanel.add(new JLabel("Sala:"));
        joinPanel.add(salaField);
        joinPanel.add(new JLabel("Multicast IP:"));
        joinPanel.add(maddrField);
        joinPanel.add(new JLabel("Puerto multicast:"));
        joinPanel.add(mportField);
        joinPanel.add(joinBtn);
        joinPanel.add(leaveBtn);

        roomComboModel = new DefaultComboBoxModel<>();
        roomCombo = new JComboBox<>(roomComboModel);
        roomCombo.addActionListener(e -> cambiarSalaActiva());

        JPanel comboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        comboPanel.setBorder(new TitledBorder("Sala activa"));
        comboPanel.add(new JLabel("Salas: "));
        comboPanel.add(roomCombo);

        topPanel.add(joinPanel, BorderLayout.CENTER);
        topPanel.add(comboPanel, BorderLayout.EAST);

        frame.add(topPanel, BorderLayout.NORTH);

        frame.setVisible(true);

        appendText("Bienvenido " + nick + ". Primero haz JOIN a una sala.\n");
    }

    // ==== Lógica de red con servidor ====

    private void sendToServer(String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, serverHost, serverPort);
        DatagramSocket tmp = new DatagramSocket();
        tmp.send(p);
        tmp.close();
    }

    private void sendJoin(String sala, String maddr, int mport) throws IOException {
        String msg = "JOIN|" + nick + "|" + sala + "|" + maddr + "|" + mport + "|" + privSocket.getLocalPort();
        sendToServer(msg);
    }

    private void sendLeave(String sala) throws IOException {
        String msg = "LEAVE|" + nick + "|" + sala;
        sendToServer(msg);
    }

    // ==== Listeners de red ====

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
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }, "PrivListener");
        t.setDaemon(true);
        t.start();
    }

    private void handlePrivate(String msg) {
        String[] parts = msg.split("\\|", 4);
        if (parts.length == 4 && "PM".equals(parts[0])) {
            String from = parts[1];
            String to = parts[2];
            String content = parts[3];
            appendText("[PM] " + from + " -> " + to + ": " + content + "\n");
        } else {
            appendText("[Privado] " + msg + "\n");
        }
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
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }, "Room-" + rs.name);
        t.setDaemon(true);
        t.start();
    }

    // ==== Manejo de mensajes de sala ====

    private void handleRoomMessage(RoomSession rs, String msg) {
        if (msg.startsWith("USER_LIST|")) {
            handleUserList(rs.name, msg);
            return;
        }

        if (msg.startsWith("<msg>")) {
            String user = tagValue(msg, "usr");
            String sala = tagValue(msg, "sala");
            String content = tagValue(msg, "content");
            if (sala == null || !sala.equals(rs.name)) return;
            appendText("[" + sala + "] " + user + ": " + content + "\n");
        } else if (msg.startsWith("<sticker>")) {
            String user = tagValue(msg, "usr");
            String sala = tagValue(msg, "sala");
            String name = tagValue(msg, "name");
            String data = tagValue(msg, "data");
            if (sala == null || !sala.equals(rs.name)) return;

            if (data != null && name != null) {
                // guardar copia en downloads
                saveBinaryFromBase64("downloads/" + sala, name + ".png", data);
            }
            appendStickerMessage(sala, user, name);
        } else if (msg.startsWith("<audiofrag>")) {
            handleAudioFragment(msg, rs.name);
        } else {
            appendText("[Sala " + rs.name + "] " + msg + "\n");
        }
    }

    private void handleUserList(String sala, String msg) {
        // USER_LIST|sala|nick@ip:port;...
        String[] parts = msg.split("\\|", 3);
        if (parts.length != 3) return;
        String salaMsg = parts[1];
        if (!salaMsg.equals(sala)) return;

        String payload = parts[2];
        Map<String, InetSocketAddress> newDir = new HashMap<>();
        List<String> users = new ArrayList<>();

        if (!payload.isEmpty()) {
            String[] usersArr = payload.split(";");
            for (String u : usersArr) {
                String[] up = u.split("@");
                if (up.length != 2) continue;
                String name = up[0];
                String[] hp = up[1].split(":");
                if (hp.length != 2) continue;
                try {
                    InetAddress ip = InetAddress.getByName(hp[0]);
                    int port = Integer.parseInt(hp[1]);
                    newDir.put(name, new InetSocketAddress(ip, port));
                    users.add(name);
                } catch (Exception ignored) { }
            }
        }

        if (sala.equals(activeRoom)) {
            directory.clear();
            directory.putAll(newDir);

            SwingUtilities.invokeLater(() -> {
                userListModel.clear();
                for (String u : users) userListModel.addElement(u);
            });
        }

        appendText("[Sala " + sala + "] Usuarios activos: " + users + "\n");
    }

    // ==== Join / Leave ====

    private void joinFromFields() {
        String sala = salaField.getText().trim();
        String maddr = maddrField.getText().trim();
        String mportStr = mportField.getText().trim();
        if (sala.isEmpty() || maddr.isEmpty() || mportStr.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Completa sala, IP y puerto");
            return;
        }
        try {
            int port = Integer.parseInt(mportStr);
            joinRoom(sala, maddr, port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Error JOIN: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void joinRoom(String sala, String maddr, int mport) throws IOException {
        if (rooms.containsKey(sala)) {
            appendText("Ya estás en la sala " + sala + "\n");
            return;
        }

        MulticastSocket ms = new MulticastSocket(mport);
        InetAddress group = InetAddress.getByName(maddr);
        ms.joinGroup(group);

        RoomSession rs = new RoomSession(sala, maddr, mport, ms, group);
        rooms.put(sala, rs);
        startRoomListener(rs);
        sendJoin(sala, maddr, mport);

        appendText("Unido a sala " + sala + " (" + maddr + ":" + mport + ")\n");

        SwingUtilities.invokeLater(() -> {
            roomComboModel.addElement(sala);
            if (activeRoom == null) {
                activeRoom = sala;
                roomCombo.setSelectedItem(sala);
            }
        });
    }

    private void leaveActiveRoom() {
        if (activeRoom == null) {
            appendText("No hay sala activa.\n");
            return;
        }
        String sala = activeRoom;
        try {
            leaveRoom(sala);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error LEAVE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void leaveRoom(String sala) throws IOException {
        RoomSession rs = rooms.remove(sala);
        if (rs == null) {
            appendText("No estabas en sala " + sala + "\n");
            return;
        }
        rs.msock.leaveGroup(rs.group);
        rs.msock.close();
        sendLeave(sala);

        appendText("Saliste de la sala " + sala + "\n");

        SwingUtilities.invokeLater(() -> {
            roomComboModel.removeElement(sala);
            if (roomComboModel.getSize() > 0) {
                activeRoom = roomComboModel.getElementAt(0);
                roomCombo.setSelectedItem(activeRoom);
            } else {
                activeRoom = null;
                userListModel.clear();
            }
        });
    }

    private void cambiarSalaActiva() {
        String sel = (String) roomCombo.getSelectedItem();
        if (sel != null && !sel.equals(activeRoom)) {
            activeRoom = sel;
            directory.clear();
            userListModel.clear();
            appendText("Sala activa ahora: " + activeRoom + "\n");
        }
    }

    // ==== Envío de mensajes ====

    private void enviarMensaje() {
        String text = msgField.getText().trim();
        if (text.isEmpty()) return;
        msgField.setText("");

        if (text.startsWith("@")) {
            int space = text.indexOf(' ');
            if (space > 1) {
                String dest = text.substring(1, space);
                String content = text.substring(space + 1);
                try {
                    sendPrivate(dest, content);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Error PM: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                appendText("Formato PM: @usuario mensaje...\n");
            }
        } else {
            try {
                sendText(text);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Error enviar mensaje: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void sendText(String text) throws IOException {
        String sala = activeRoom;
        if (sala == null) {
            appendText("Primero entra a una sala.\n");
            return;
        }
        RoomSession rs = rooms.get(sala);
        if (rs == null) {
            appendText("No tienes sesión para sala " + sala + "\n");
            return;
        }

        String xml = "<msg><usr>" + nick + "</usr><sala>" + sala +
                "</sala><content>" + text + "</content></msg>";
        byte[] data = xml.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, rs.group, rs.mport);
        rs.msock.send(p);
    }

    private void enviarSticker(String name) {
        String sala = activeRoom;
        if (sala == null) {
            appendText("Primero entra a una sala.\n");
            return;
        }
        try {
            RoomSession rs = rooms.get(sala);
            if (rs == null) {
                appendText("No tienes sesión para sala " + sala + "\n");
                return;
            }

            File f = new File("stickers", name + ".png");
            if (!f.exists()) {
                appendText("Sticker no encontrado: " + f.getAbsolutePath() + "\n");
                return;
            }

            byte[] bytes = readAllBytes(f);
            String b64 = Base64.getEncoder().encodeToString(bytes);

            String xml = "<sticker><usr>" + nick + "</usr><sala>" + sala +
                    "</sala><name>" + name + "</name><data>" + b64 + "</data></sticker>";

            byte[] data = xml.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(data, data.length, rs.group, rs.mport);
            rs.msock.send(p);

            // IMPORTANTE: ya NO mostramos aquí para evitar el doble sticker
            // El sticker se muestra cuando se recibe por multicast en handleRoomMessage()

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error sticker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void enviarAudio() {
        String sala = activeRoom;
        if (sala == null) {
            appendText("Primero entra a una sala.\n");
            return;
        }
        RoomSession rs = rooms.get(sala);
        if (rs == null) {
            appendText("No tienes sesión para sala " + sala + "\n");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        int r = chooser.showOpenDialog(frame);
        if (r != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        try {
            byte[] bytes = readAllBytes(f);
            int total = (int) Math.ceil(bytes.length / (double) AUDIO_CHUNK_SIZE);

            appendText("[" + sala + "] Enviando audio " + f.getName() +
                    " en " + total + " fragmentos...\n");

            for (int i = 0; i < total; i++) {
                int start = i * AUDIO_CHUNK_SIZE;
                int len = Math.min(AUDIO_CHUNK_SIZE, bytes.length - start);
                byte[] chunk = Arrays.copyOfRange(bytes, start, start + len);
                String b64 = Base64.getEncoder().encodeToString(chunk);

                String xml = "<audiofrag><usr>" + nick + "</usr><sala>" + sala +
                        "</sala><name>" + f.getName() +
                        "</name><index>" + i +
                        "</index><total>" + total +
                        "</total><data>" + b64 +
                        "</data></audiofrag>";

                byte[] data = xml.getBytes(StandardCharsets.UTF_8);
                DatagramPacket p = new DatagramPacket(data, data.length, rs.group, rs.mport);
                rs.msock.send(p);
            }

            appendText("[" + sala + "] Audio enviado: " + f.getName() + "\n");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendPrivate(String destinatario, String content) throws IOException {
        InetSocketAddress addr = directory.get(destinatario);
        if (addr == null) {
            appendText("No conozco al usuario " + destinatario + " en la USER_LIST de esta sala.\n");
            return;
        }
        String msg = "PM|" + nick + "|" + destinatario + "|" + content;
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, addr.getAddress(), addr.getPort());
        privSocket.send(p);
        appendText("[PM] (Tú -> " + destinatario + "): " + content + "\n");
    }

    // ==== Manejo de audio fragmentado ====

    private void handleAudioFragment(String xml, String roomName) {
        String user = tagValue(xml, "usr");
        String sala = tagValue(xml, "sala");
        String name = tagValue(xml, "name");
        String idxStr = tagValue(xml, "index");
        String totalStr = tagValue(xml, "total");
        String data = tagValue(xml, "data");

        if (sala == null || !sala.equals(roomName)) return;
        if (user == null || name == null || idxStr == null || totalStr == null || data == null) return;

        int index;
        int total;
        try {
            index = Integer.parseInt(idxStr);
            total = Integer.parseInt(totalStr);
        } catch (NumberFormatException e) {
            return;
        }

        String key = audioKey(sala, user, name);
        AudioAssembly as = audioAssemblies.get(key);
        if (as == null) {
            as = new AudioAssembly(sala, user, name, total);
            audioAssemblies.put(key, as);
        }

        if (index < 0 || index >= as.total) {
            return;
        }

        // Decodificar fragmento
        byte[] chunk;
        try {
            chunk = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return;
        }

        synchronized (as) {
            if (as.parts[index] == null) {
                as.parts[index] = chunk;
                as.received++;
            }
            if (as.received == as.total) {
                // Reensamblar
                int totalBytes = 0;
                for (byte[] part : as.parts) {
                    if (part != null) totalBytes += part.length;
                }
                byte[] full = new byte[totalBytes];
                int pos = 0;
                for (byte[] part : as.parts) {
                    System.arraycopy(part, 0, full, pos, part.length);
                    pos += part.length;
                }

                try {
                    File dir = new File("downloads/" + sala);
                    if (!dir.exists()) dir.mkdirs();
                    File out = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(full);
                    }

                    appendText("[" + sala + "] " + user +
                            " envió audio (reconstruido): " + out.getAbsolutePath() + "\n");

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    audioAssemblies.remove(key);
                }
            }
        }
    }

    // ==== Utils de texto / archivos ====

    private static String tagValue(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int i = xml.indexOf(open);
        if (i < 0) return null;
        int j = xml.indexOf(close, i + open.length());
        if (j < 0) return null;
        return xml.substring(i + open.length(), j);
    }

    private static byte[] readAllBytes(File f) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static String saveBinaryFromBase64(String dirPath, String filename, String b64) {
        try {
            byte[] data = Base64.getDecoder().decode(b64);
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(data);
            }
            return out.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void appendText(String text) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatPane.getStyledDocument();
            try {
                doc.insertString(doc.getLength(), text, null);
                chatPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void appendStickerMessage(String sala, String user, String name) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatPane.getStyledDocument();
            try {
                doc.insertString(doc.getLength(),
                        "[" + sala + "] " + user + ": ", null);

                File f = new File("stickers", name + ".png");
                if (f.exists()) {
                    ImageIcon icon = new ImageIcon(f.getPath());
                    Image img = icon.getImage().getScaledInstance(40, 40, Image.SCALE_SMOOTH);
                    icon = new ImageIcon(img);
                    chatPane.setCaretPosition(doc.getLength());
                    chatPane.insertIcon(icon);
                    doc.insertString(doc.getLength(), "\n", null);
                } else {
                    doc.insertString(doc.getLength(), "[sticker " + name + "]\n", null);
                }

                chatPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    // ==== Cierre ordenado ====

    private void cerrar() {
        running = false;
        try {
            for (String sala : new ArrayList<>(rooms.keySet())) {
                try {
                    leaveRoom(sala);
                } catch (Exception ignored) {}
            }
        } finally {
            privSocket.close();
        }
        frame.dispose();
        System.exit(0);
    }

    // ==== main ====

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Uso: java chat.ChatClientGUI <nick> <hostServidor> <puertoServidor>");
            return;
        }
        String nick = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        new ChatClientGUI(nick, host, port);
    }
}
