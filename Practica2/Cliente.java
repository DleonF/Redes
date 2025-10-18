import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class Cliente {
    private DatagramSocket socket;
    private InetAddress multicastGroup;
    
    public Cliente() {
        try {
            socket = new DatagramSocket();
            multicastGroup = InetAddress.getByName("ff3e:40:2001::1");
            
            System.out.println("Cliente de Audio iniciado...");
            
            // Al iniciar el cliente, enviar automáticamente CONNECT
            enviarComando("CONNECT");
            
            startClient();
            
        } catch (Exception e) {
            System.err.println("Error iniciando cliente: " + e.getMessage());
        }
    }
    
    private void startClient() {
        // Hilo para recibir respuestas
        new Thread(this::recibirRespuestas).start();
        
        // Interfaz de usuario
        enviarComandos();
    }
    
    private void recibirRespuestas() {
        try {
            byte[] buffer = new byte[1024];
            
            System.out.println("Escuchando respuestas del servidor...");
            
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                String respuesta = new String(packet.getData(), 0, packet.getLength());
                System.out.println(">>> " + respuesta);
            }
        } catch (Exception e) {
            System.err.println("Error recibiendo respuestas: " + e.getMessage());
        }
    }
    
    private void enviarComandos() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n=== CONTROL DE AUDIO ===");
        System.out.println("Comandos disponibles:");
        System.out.println("PLAY    - Continuar reproducción");
        System.out.println("PAUSE   - Pausar reproducción");
        System.out.println("STOP    - Detener y reiniciar");
        System.out.println("RESTART - Reiniciar desde el inicio");
        System.out.println("STATUS  - Consultar estado actual");
        System.out.println("EXIT    - Salir del cliente");
        System.out.println("========================\n");
        
        while (true) {
            System.out.print("Ingresa comando: ");
            String comando = scanner.nextLine().trim();
            
            if ("EXIT".equalsIgnoreCase(comando)) {
                break;
            }
            
            if (!comando.isEmpty()) {
                enviarComando(comando);
            }
        }
        
        scanner.close();
        System.out.println("Cliente terminado.");
    }
    
    private void enviarComando(String comando) {
        try {
            byte[] datos = comando.getBytes();
            DatagramPacket packet = new DatagramPacket(
                datos, datos.length, multicastGroup, 7777);
            
            socket.send(packet);
            System.out.println("Comando enviado: " + comando);
            
        } catch (Exception e) {
            System.err.println("Error enviando comando: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        new Cliente();
    }
}