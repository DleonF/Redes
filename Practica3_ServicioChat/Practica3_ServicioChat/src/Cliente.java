import java.net.*;
import java.io.*;
import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.io.StringReader;
import java.util.*;
import java.util.regex.*;

public class Cliente {

    // Constructor del cliente que inicializa variables, une al grupo multicast y lanza el hilo de escucha
    public Cliente(String nombre, String host, int puerto, JEditorPane editor, JComboBox<String> usuarioConectado, String sala) {
        this.nombre = nombre;
        this.host = host;
        this.puerto = puerto;
        this.editor = editor;
        this.usuarioConectado = usuarioConectado;
        this.sala = sala;

        try {
            cliente = new MulticastSocket(puerto);      // Socket multicast
            grupo = InetAddress.getByName(host);            // Grupo multicast
            cliente.joinGroup(grupo);               // Se une al grupo
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Inicia el hilo que escucha mensajes entrantes
        hiloEscucha = new EscuchaMensajes();
        escucha = new Thread(hiloEscucha);
        escucha.start();

        carpeta(); // Crea carpetas necesarias para archivos
    }

    // Clase interna que representa el hilo de escucha
    private class EscuchaMensajes implements Runnable {
        public void run() {
            System.out.println("Escuchando Mensajes");

            try {
                DatagramPacket recibido = new DatagramPacket(new byte[6500], 6500);

                while (true) {
                    cliente.receive(recibido);
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(recibido.getData()));
                    Mensaje msj = (Mensaje) ois.readObject();

                    // ✳️ Filtra mensajes de otras salas
                    if (!sala.equals(msj.getSala())) {
                        continue;
                    }

                    switch (msj.getTipo()) {
                        case 0: // Saludo de nuevo usuario
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
                        case 2: // Archivo
                            if (!msj.getUsuarioOrigen().equals(nombre)) {
                                recibirArchivo(msj);
                            }
                            break;
                        case 3: // Confirmación de usuario
                            if (!msj.getUsuarioOrigen().equals(nombre) && msj.getUsuarioDestino().equals(nombre)) {
                                usuarioConectado.addItem(msj.getUsuarioOrigen());
                            }
                            break;
                        case 4: // Mensaje privado
                            if (msj.getUsuarioDestino().equals(nombre)) {
                                mostrar(msj.getMensaje());
                            }
                            break;
                        case 5: // Mensaje de salida
                            mostrar(msj.getMensaje());
                            
                            // Elimina el usuario de la lista de usuarios conectados
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    usuarioConectado.removeItem(msj.getUsuarioOrigen());
                                }
                            });
                            break;
                    }

                    ois.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Muestra contenido HTML en el editor
    private void mostrar(String contenido) {
        try {
            HTMLEditorKit kit = (HTMLEditorKit) editor.getEditorKit();
            StringReader reader = new StringReader(contenido);
            kit.read(reader, editor.getDocument(), editor.getDocument().getLength());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Reemplazando emojis antes de enviar
    public void enviar(Mensaje msj) {
        reemplazarEmojis(msj);
        new Thread(new EnviaMensajes(msj)).start();
    }

    // Clase interna para enviar mensajes
    private class EnviaMensajes implements Runnable {
        private Mensaje msj;

        private EnviaMensajes(Mensaje msj) {
            this.msj = msj;
        }

        public void run() {
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
    }

    // Envía mensaje de bienvenida al entrar al chat
    public void saludo(String nombre) {
        String a = "<b>[" + nombre + "] se ha conectado</b>";
        Mensaje m = new Mensaje(a, nombre, "", 0, sala);
        enviar(m);
    }

    // Envía mensaje de despedida al salir
    public void despedida(String nombre) {
        String msg = "<b>[" + nombre + "] ha salido del chat</b>";
        Mensaje m = new Mensaje(msg, nombre, "", 5, sala);
        enviar(m);
    }

    // Crea carpetas de usuario para archivos recibidos
    public void carpeta() {
        File carpetaBase = new File("Archivos Adjuntos");
        if (!carpetaBase.exists()) carpetaBase.mkdir();

        File carpetaUsuario = new File(carpetaBase, nombre);
        if (!carpetaUsuario.exists()) carpetaUsuario.mkdir();
        
        File carpetaSala = new File(carpetaUsuario, sala);
        if (!carpetaSala.exists()) {
            carpetaSala.mkdir();
        }
    }

    // Reemplaza los códigos de emoji por sus imágenes correspondientes
    private void reemplazarEmojis(Mensaje msj) {
        String mensaje = msj.getMensaje();

        // Reemplazo de cada emoji
        mensaje = mensaje.replaceAll(Pattern.quote(":astute:"), getImgTag("astuto.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":happy:"), getImgTag("feliz.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":angry:"), getImgTag("enojado.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":sad:"), getImgTag("triste.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":smile:"), getImgTag("sonrisa.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":tired:"), getImgTag("cansado.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":playful:"), getImgTag("lengua.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":laugh:"), getImgTag("risa.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":cat:"), getImgTag("gato.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":heart:"), getImgTag("corazon.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":kiss:"), getImgTag("beso.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":tear:"), getImgTag("lagrima.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":love:"), getImgTag("enamorado.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":ok:"), getImgTag("ok.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":rock:"), getImgTag("rock.png", 40, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":gay:"), getImgTag("gay.gif", 60, 40));
        mensaje = mensaje.replaceAll(Pattern.quote(":sorprendido:"), getImgTag("momo.jpg", 60, 60));
        mensaje = mensaje.replaceAll(Pattern.quote(":like:"), getImgTag("like.jpg", 60, 60));
        mensaje = mensaje.replaceAll(Pattern.quote(":orgullo:"), getImgTag("orgullo.gif", 60, 60));
        mensaje = mensaje.replaceAll(Pattern.quote(":gey2:"), getImgTag("gey2.png", 60, 40));

        msj.setMensaje("<html>" + mensaje + "</html>");
    }

    // Genera el tag <img> para un emoji
    private String getImgTag(String nombreArchivo, int ancho, int alto) {
        URL url = Cliente.class.getClassLoader().getResource("Emojis/" + nombreArchivo);
        return "<img src='" + url + "' width='" + ancho + "' height='" + alto + "'/>";
    }

    // Envía archivo como múltiples paquetes de tipo 2
    public void enviarArchivo(File file, String destinatario) {
        new Thread(new EnvioArchivos(file, destinatario)).start();

        String aviso;
        if (destinatario.equals("")) {
            aviso = "<b>" + nombre + " ha enviado un archivo público: " + file.getName() + "</b>";
        } else {
            aviso = "<b>" + nombre + " ha enviado un archivo privado a [" + destinatario + "]: " + file.getName() + "</b>";
        }

        // Usa tipo 1 (mensaje público) o 4 (privado), según destinatario
        int tipo = destinatario.equals("") ? 1 : 4;
        Mensaje mensajeAviso = new Mensaje(aviso, nombre, destinatario, tipo, sala);
        enviar(mensajeAviso);
    }


    // Clase que gestiona el envío de archivos fragmentados
    private class EnvioArchivos implements Runnable {
        private File file;
        private String destinatario;

        public EnvioArchivos(File file, String destinatario) {
            this.file = file;
            this.destinatario = destinatario;
        }

        public void run() {
            try {
                DataInputStream dis = new DataInputStream(new FileInputStream(file));
                long tamanio = dis.available();
                long enviado = 0;
                int n = 0;
                int i = 0;

                while (enviado < tamanio) {
                    Mensaje datos = new Mensaje(
                        file.getName(), nombre, destinatario, 2, file.length(), "", ++i, sala
                    );
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(6400);
                    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(baos));
                    oos.flush();

                    byte[] b = new byte[4000];
                    n = dis.read(b);
                    byte[] b2 = new byte[n];
                    System.arraycopy(b, 0, b2, 0, n);
                    datos.setDatos(b2);
                    datos.setBytesEnviados(n);

                    oos.writeObject(datos);
                    oos.flush();

                    byte[] d = baos.toByteArray();
                    DatagramPacket paqueteEnvio = new DatagramPacket(d, d.length, grupo, puerto);
                    cliente.send(paqueteEnvio);

                    Thread.sleep(500);
                    enviado += n;
                    oos.close();
                    baos.close();
                }

                // Paquete final
                byte[] bFinal = {0x02};
                Mensaje paqueteFinal = new Mensaje(file.getName(), nombre, destinatario, 2, file.length(), "", 0, sala);
                paqueteFinal.setDatos(bFinal);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(paqueteFinal);
                oos.flush();

                byte[] mnsj = baos.toByteArray();
                DatagramPacket dp = new DatagramPacket(mnsj, mnsj.length, grupo, puerto);
                cliente.send(dp);

                oos.close();
                baos.close();
                dis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    // Recibe fragmentos de archivo y los reconstruye
    private void recibirArchivo(Mensaje datos) {
        try {
            // Validar destinatario: público o dirigido a mí
            if (!datos.getUsuarioDestino().isEmpty() && !datos.getUsuarioDestino().equals(nombre)) {
                return; // Ignora si es privado y no es para mí
            }

            System.out.println("Paquete recibido: " + datos.getNp());

            //Ruta: Archivos Adjuntos/Usuario/Sala/
            File carpetaDestino = new File("Archivos Adjuntos/" + nombre + "/" + sala);
            if (!carpetaDestino.exists()) {
                carpetaDestino.mkdirs(); // Crea carpetas si no existen
            }

            String nombreArchivo = datos.getNombre(); // nombre único por archivo

            if (datos.getNp() == 1) {
                //Primer paquete: inicia lista de fragmentos
                List<byte[]> nuevaLista = new ArrayList<>();
                nuevaLista.add(datos.getDatos());
                archivosEnProgreso.put(nombreArchivo, nuevaLista);

            } else if (datos.getNp() > 1) {
                //Paquete intermedio: agrega al mapa
                if (archivosEnProgreso.containsKey(nombreArchivo)) {
                    archivosEnProgreso.get(nombreArchivo).add(datos.getDatos());
                }

            } else if (datos.getNp() == 0) {
                //Último paquete: reconstruir archivo
                List<byte[]> partes = archivosEnProgreso.get(nombreArchivo);
                if (partes != null) {
                    File archivoFinal = new File(carpetaDestino, nombreArchivo);
                    try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(archivoFinal))) {
                        for (byte[] fragmento : partes) {
                            dos.write(fragmento);
                        }
                    }

                    archivosEnProgreso.remove(nombreArchivo); // Limpia después de reconstruir
                    mostrar("<b>Archivo recibido: " + nombreArchivo + "</b>");
                }
            }

            Thread.sleep(500); // Evita saturación

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Finaliza la conexión y cierra el socket
    public void cerrarConexion() {
        try {
            if (cliente != null && !cliente.isClosed()) {
                cliente.leaveGroup(grupo);
                cliente.close();
            }
            if (escucha != null) {
                escucha.interrupt();
            }
            System.out.println("Cliente desconectado correctamente");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Variables de instancia
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
    private Pattern p;
    private Matcher m;
}