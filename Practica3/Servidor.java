import java.net.*;
import java.io.*;
import java.util.*;

public class Servidor {
    private static final int PUERTO_SERVIDOR = 4446;
    private Map<String, Set<String>> salasUsuarios = new HashMap<>();
    private Map<String, DatagramPacket> usuariosConexiones = new HashMap<>();
    
    public Servidor() {
        iniciarServidor();
    }
    
    private void iniciarServidor() {
        try (DatagramSocket socket = new DatagramSocket(PUERTO_SERVIDOR)) {
            System.out.println("🟢 Servidor iniciado en puerto " + PUERTO_SERVIDOR);
            System.out.println("📍 Esperando conexiones de clientes...");
            
            byte[] buffer = new byte[1024];
            
            while (true) {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
                
                String mensaje = new String(paquete.getData(), 0, paquete.getLength());
                System.out.println("📨 Mensaje recibido: " + mensaje);
                
                String[] partes = mensaje.split(":");
                if (partes.length >= 3) {
                    String accion = partes[0];
                    String usuario = partes[1];
                    String sala = partes[2];
                    
                    usuariosConexiones.put(usuario, paquete);
                    
                    switch (accion) {
                        case "JOIN":
                            unirUsuarioASala(usuario, sala);
                            break;
                        case "LEAVE":
                            sacarUsuarioDeSala(usuario, sala);
                            break;
                    }
                    
                    enviarListaUsuarios(sala, socket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void unirUsuarioASala(String usuario, String sala) {
        salasUsuarios.putIfAbsent(sala, new HashSet<>());
        salasUsuarios.get(sala).add(usuario);
        System.out.println("✅ " + usuario + " se unió a la sala: " + sala);
    }
    
    private void sacarUsuarioDeSala(String usuario, String sala) {
        if (salasUsuarios.containsKey(sala)) {
            salasUsuarios.get(sala).remove(usuario);
            System.out.println("❌ " + usuario + " salió de la sala: " + sala);
        }
    }
    
    private void enviarListaUsuarios(String sala, DatagramSocket socket) {
        if (!salasUsuarios.containsKey(sala)) return;
        
        Set<String> usuarios = salasUsuarios.get(sala);
        String lista = "USER_LIST:" + sala + ":" + String.join(",", usuarios);
        
        System.out.println("📋 Lista actualizada " + sala + ": " + usuarios);
        
        for (String usuario : usuarios) {
            DatagramPacket conexionUsuario = usuariosConexiones.get(usuario);
            if (conexionUsuario != null) {
                try {
                    byte[] datos = lista.getBytes();
                    DatagramPacket paqueteLista = new DatagramPacket(
                        datos, datos.length, 
                        conexionUsuario.getAddress(), 
                        conexionUsuario.getPort()
                    );
                    socket.send(paqueteLista);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public static void main(String[] args) {
        new Servidor();
    }
}