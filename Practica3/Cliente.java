import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLEditorKit;

public class Cliente {
    // Variables para comunicación con servidor
    private DatagramSocket socketServidor;
    private InetAddress direccionServidor;
    private final int PUERTO_SERVIDOR = 4446;

    // Constructor
    public Cliente(String nombre, String host, int puerto, JEditorPane editor, JComboBox<String> usuarioConectado, String sala) {
        this.nombre = nombre;
        this.host = host;
        this.puerto = puerto;
        this.editor = editor;
        this.usuarioConectado = usuarioConectado;
        this.sala = sala;

        try {
            // Socket para comunicación con servidor
            socketServidor = new DatagramSocket();
            direccionServidor = InetAddress.getByName("localhost");
            
            // Unirse al grupo multicast
            cliente = new MulticastSocket(puerto);
            grupo = InetAddress.getByName(host);
            cliente.joinGroup(grupo);
            
            // Notificar al servidor
            notificarServidor("JOIN");
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        hiloEscucha = new EscuchaMensajes();
        escucha = new Thread(hiloEscucha);
        escucha.start();

        carpeta();
    }

    // Notificar servidor
    private void notificarServidor(String accion) {
        try {
            String mensaje = accion + ":" + nombre + ":" + sala;
            byte[] datos = mensaje.getBytes();
            DatagramPacket paquete = new DatagramPacket(datos, datos.length, direccionServidor, PUERTO_SERVIDOR);
            socketServidor.send(paquete);
            System.out.println("Notificado servidor: " + mensaje);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Clase de escucha de mensajes
    private class EscuchaMensajes implements Runnable {
        public void run() {
            System.out.println("Escuchando Mensajes");

            try {
                DatagramPacket recibido = new DatagramPacket(new byte[65000], 65000);

                while (true) {
                    cliente.receive(recibido);
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(recibido.getData()));
                    Mensaje msj = (Mensaje) ois.readObject();

                    if (!sala.equals(msj.getSala())) {
                        continue;
                    }

                    switch (msj.getTipo()) {
                        case 0: // Saludo
                            if (!msj.getUsuarioOrigen().equals(nombre)) {
                                mostrar(msj.getMensaje());
                                usuarioConectado.addItem(msj.getUsuarioOrigen());
                                Mensaje respuesta = new Mensaje("", nombre, msj.getUsuarioOrigen(), 3, sala);
                                enviar(respuesta);
                            }
                            break;
                        case 1: // Mensaje público
                            mostrar(msj.getMensaje());
                            break;
                        case 2: // Audio
                            if (!msj.getUsuarioOrigen().equals(nombre)) {
                                recibirAudio(msj);
                            }
                            break;
                        case 6: // Sticker
                            if (!msj.getUsuarioOrigen().equals(nombre)) {
                                recibirSticker(msj);
                            }
                            break;
                        case 3: // Confirmación
                            if (!msj.getUsuarioOrigen().equals(nombre) && msj.getUsuarioDestino().equals(nombre)) {
                                usuarioConectado.addItem(msj.getUsuarioOrigen());
                            }
                            break;
                        case 4: // Mensaje privado
                            if (msj.getUsuarioDestino().equals(nombre)) {
                                mostrar(msj.getMensaje());
                            }
                            break;
                        case 5: // Despedida
                            mostrar(msj.getMensaje());
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    usuarioConectado.removeItem(msj.getUsuarioOrigen());
                                }
                            });
                            break;
                        case 7: // Lista usuarios (del servidor)
                            actualizarListaUsuarios(msj.getMensaje());
                            break;
                    }

                    ois.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Actualizar lista de usuarios desde servidor
    private void actualizarListaUsuarios(String lista) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    // Formato: "USER_LIST:sala:usuario1,usuario2,usuario3"
                    String[] partes = lista.split(":");
                    if (partes.length >= 3 && partes[0].equals("USER_LIST")) {
                        String salaLista = partes[1];
                        String usuarios = partes[2];
                        
                        if (salaLista.equals(sala)) {
                            usuarioConectado.removeAllItems();
                            usuarioConectado.addItem("Todos");
                            
                            String[] usuariosArray = usuarios.split(",");
                            for (String usuario : usuariosArray) {
                                if (!usuario.isEmpty() && !usuario.equals(nombre)) {
                                    usuarioConectado.addItem(usuario);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Método para mostrar mensajes
    private void mostrar(String contenido) {
        try {
            HTMLEditorKit kit = (HTMLEditorKit) editor.getEditorKit();
            StringReader reader = new StringReader(contenido);
            kit.read(reader, editor.getDocument(), editor.getDocument().getLength());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Enviar mensaje
    public void enviar(Mensaje msj) {
        reemplazarEmojis(msj);
        new Thread(new EnviaMensajes(msj)).start();
    }

    // Enviar audio
    public void enviarAudio(File file, String destinatario) {
        new Thread(new EnvioAudio(file, destinatario)).start();

        String aviso = "<b>" + nombre + " ha enviado un audio: " + file.getName() + "</b>";
        int tipo = destinatario.equals("") ? 1 : 4;
        Mensaje mensajeAviso = new Mensaje(aviso, nombre, destinatario, tipo, sala);
        enviar(mensajeAviso);
    }

    // Enviar sticker
    public void enviarSticker(String nombreSticker, String destinatario) {
        new Thread(new EnvioSticker(nombreSticker, destinatario)).start();
    }

    // Clase para enviar audio
    private class EnvioAudio implements Runnable {
        private File file;
        private String destinatario;

        public EnvioAudio(File file, String destinatario) {
            this.file = file;
            this.destinatario = destinatario;
        }

        public void run() {
            try {
                byte[] audioData = leerArchivoAudio(file);
                if (audioData == null) {
                    mostrar("<b>Error: No se pudo leer el archivo de audio</b>");
                    return;
                }

                // Enviar en un solo paquete (simplificado)
                Mensaje datosAudio = new Mensaje(
                    file.getName(), nombre, destinatario, Mensaje.TIPO_AUDIO, 
                    file.length(), 1, sala, audioData
                );

                enviarMensajeObjeto(datosAudio);
                mostrar("<b>Audio enviado: " + file.getName() + "</b>");

            } catch (Exception e) {
                e.printStackTrace();
                mostrar("<b>Error al enviar audio</b>");
            }
        }

        private byte[] leerArchivoAudio(File file) {
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return baos.toByteArray();
                
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    // Clase para enviar stickers
    private class EnvioSticker implements Runnable {
        private String nombreSticker;
        private String destinatario;

        public EnvioSticker(String nombreSticker, String destinatario) {
            this.nombreSticker = nombreSticker;
            this.destinatario = destinatario;
        }

        public void run() {
            try {
                // Cargar sticker desde recursos
                byte[] stickerData = cargarSticker(nombreSticker);
                if (stickerData != null) {
                    Mensaje mensajeSticker = new Mensaje(
                        nombreSticker, nombre, destinatario, Mensaje.TIPO_STICKER, 
                        sala, stickerData
                    );
                    enviarMensajeObjeto(mensajeSticker);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private byte[] cargarSticker(String nombreSticker) {
            try {
                // Simular sticker con emoji (en una implementación real cargarías imágenes)
                String emoji = convertirNombreAEmoji(nombreSticker);
                String mensajeSticker = "<div style='font-size: 48px; text-align: center;'>" + 
                                       emoji + "<br><small>Sticker de " + nombre + "</small></div>";
                
                Mensaje avisoSticker = new Mensaje(mensajeSticker, nombre, destinatario, 
                                                 destinatario.equals("") ? 1 : 4, sala);
                enviar(avisoSticker);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new byte[0]; // Placeholder
        }

        private String convertirNombreAEmoji(String nombreSticker) {
            Map<String, String> stickerEmojis = new HashMap<>();
            stickerEmojis.put("corazon", "❤️");
            stickerEmojis.put("risa", "😂");
            stickerEmojis.put("feliz", "😊");
            stickerEmojis.put("enamorado", "😍");
            stickerEmojis.put("like", "👍");
            stickerEmojis.put("saludo", "👋");
            stickerEmojis.put("celebracion", "🎉");
            stickerEmojis.put("fuego", "🔥");
            
            return stickerEmojis.getOrDefault(nombreSticker, "😊");
        }
    }

    // Recibir audio
    private void recibirAudio(Mensaje datos) {
        try {
            if (!datos.getUsuarioDestino().isEmpty() && !datos.getUsuarioDestino().equals(nombre)) {
                return;
            }

            byte[] audioData = datos.getDatosAudio();
            if (audioData != null && audioData.length > 0) {
                guardarAudio(datos.getNombre(), audioData);
                mostrar("<b>Audio recibido: " + datos.getNombre() + "</b>");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Recibir sticker
    private void recibirSticker(Mensaje datos) {
        // Los stickers se manejan como mensajes normales en esta implementación
    }

    // Guardar audio
    private void guardarAudio(String nombreArchivo, byte[] audioData) {
        try {
            File carpetaDestino = new File("Audios Recibidos/" + nombre + "/" + sala);
            if (!carpetaDestino.exists()) {
                carpetaDestino.mkdirs();
            }

            File archivoFinal = new File(carpetaDestino, nombreArchivo);
            try (FileOutputStream fos = new FileOutputStream(archivoFinal)) {
                fos.write(audioData);
            }

            // Preguntar reproducción solo para WAV
            if (nombreArchivo.toLowerCase().endsWith(".wav")) {
                int opcion = JOptionPane.showConfirmDialog(null,
                    "¿Quieres reproducir el audio: " + nombreArchivo + "?",
                    "Audio Recibido", JOptionPane.YES_NO_OPTION);

                if (opcion == JOptionPane.YES_OPTION) {
                    reproducirAudio(archivoFinal);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Reproducir audio
    private void reproducirAudio(File archivoAudio) {
        new Thread(() -> {
            try {
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(archivoAudio);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                clip.start();
                
                while (!clip.isRunning()) Thread.sleep(10);
                while (clip.isRunning()) Thread.sleep(10);
                
                clip.close();
                audioInputStream.close();
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                    "No se pudo reproducir el audio WAV",
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    // Enviar mensaje como objeto
    private void enviarMensajeObjeto(Mensaje msj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(msj);
            oos.flush();
            byte[] msjBytes = baos.toByteArray();

            DatagramPacket p = new DatagramPacket(msjBytes, msjBytes.length, grupo, puerto);
            cliente.send(p);

            oos.close();
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Clase para enviar mensajes
    private class EnviaMensajes implements Runnable {
        private Mensaje msj;

        private EnviaMensajes(Mensaje msj) {
            this.msj = msj;
        }

        public void run() {
            enviarMensajeObjeto(msj);
        }
    }

    // Saludo
    public void saludo(String nombre) {
        String a = "<b>[" + nombre + "] se ha conectado</b>";
        Mensaje m = new Mensaje(a, nombre, "", 0, sala);
        enviar(m);
    }

    // Despedida
    public void despedida(String nombre) {
        String msg = "<b>[" + nombre + "] ha salido del chat</b>";
        Mensaje m = new Mensaje(msg, nombre, "", 5, sala);
        enviar(m);
        notificarServidor("LEAVE");
    }

    // Crear carpetas
    public void carpeta() {
        File carpetaBase = new File("Audios Recibidos");
        if (!carpetaBase.exists()) carpetaBase.mkdir();

        File carpetaUsuario = new File(carpetaBase, nombre);
        if (!carpetaUsuario.exists()) carpetaUsuario.mkdir();
        
        File carpetaSala = new File(carpetaUsuario, sala);
        if (!carpetaSala.exists()) {
            carpetaSala.mkdir();
        }
    }

    // Reemplazar emojis (tu código existente)
    private void reemplazarEmojis(Mensaje msj) {
        String mensaje = msj.getMensaje();
        // ... (tu código existente de reemplazo de emojis)
        msj.setMensaje("<html>" + mensaje + "</html>");
    }

    // Cerrar conexión
    public void cerrarConexion() {
        try {
            if (cliente != null && !cliente.isClosed()) {
                cliente.leaveGroup(grupo);
                cliente.close();
            }
            if (socketServidor != null && !socketServidor.isClosed()) {
                socketServidor.close();
            }
            if (escucha != null) {
                escucha.interrupt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Variables
    private String nombre;
    private String host;
    private int puerto;
    private String sala;
    private JEditorPane editor;
    private Thread escucha;
    private Runnable hiloEscucha;
    private MulticastSocket cliente;
    private InetAddress grupo;
    private JComboBox<String> usuarioConectado;
    private Map<String, List<byte[]>> archivosEnProgreso = new HashMap<>();
}