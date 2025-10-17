import java.net.*;
import java.util.Scanner;

public class Cliente {
    
    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress multicastGroup = InetAddress.getByName("ff3e:40:2001::1");
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("Cliente de Audio - Escribe comandos:");
            System.out.println("PLAY, PAUSE, STOP, RESTART, STATUS, EXIT\n");
            
            while (true) {
                System.out.print("Comando: ");
                String comando = scanner.nextLine().trim();
                
                if ("EXIT".equalsIgnoreCase(comando)) {
                    break;
                }
                
                // Enviar comando al servidor
                byte[] datos = comando.getBytes();
                DatagramPacket packet = new DatagramPacket(
                    datos, datos.length, multicastGroup, 7777);
                socket.send(packet);
                
                System.out.println("Comando enviado: " + comando);
                
                // Esperar respuesta con timeout
                byte[] buffer = new byte[1024];
                DatagramPacket respuesta = new DatagramPacket(buffer, buffer.length);
                socket.setSoTimeout(3000); // 3 segundos de timeout
                
                try {
                    socket.receive(respuesta);
                    String mensaje = new String(respuesta.getData(), 0, respuesta.getLength());
                    System.out.println("Servidor: " + mensaje);
                } catch (SocketTimeoutException e) {
                    System.out.println("No se recibió respuesta del servidor (timeout)");
                }
                
                System.out.println(); // línea en blanco
            }
            
            scanner.close();
            socket.close();
            System.out.println("Cliente terminado.");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}