import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    private MulticastSocket serverSocket;
    private InetAddress multicastGroup;
    private int nextExpectedSeq = 0;
    private Map<Integer, String> receivedCommands = new ConcurrentHashMap<>();
    
    public Servidor() {
        try {
            multicastGroup = InetAddress.getByName("ff3e:40:2001::1");
            serverSocket = new MulticastSocket(7777);
            serverSocket.joinGroup(multicastGroup);
            serverSocket.setReuseAddress(true);
            serverSocket.setTimeToLive(255);

            System.out.println("Servidor de Audio con Ventana Deslizante iniciado...");
            System.out.println("Grupo: " + multicastGroup + ", Puerto: 7777");
            System.out.println("Esperando comandos con ventana deslizante...");

            listenForCommands();

        } catch (Exception e) {
            System.err.println("Error iniciando servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class Mensaje {
        private int sequenceNumber;
        private int ackNumber;
        private String comando;
        private long timestamp;
        
        public Mensaje(int seq, int ack, String cmd) {
            this.sequenceNumber = seq;
            this.ackNumber = ack;
            this.comando = cmd;
            this.timestamp = System.currentTimeMillis();
        }
        
        public int getSequenceNumber() { return sequenceNumber; }
        public int getAckNumber() { return ackNumber; }
        public String getComando() { return comando; }
        public long getTimestamp() { return timestamp; }
        
        public byte[] toBytes() {
            String data = sequenceNumber + ":" + ackNumber + ":" + comando;
            return data.getBytes();
        }
        
        public static Mensaje fromBytes(byte[] data) {
            try {
                String str = new String(data).trim();
                String[] parts = str.split(":", 3);
                if (parts.length == 3) {
                    return new Mensaje(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        parts[2]
                    );
                }
            } catch (Exception e) {
                System.err.println("Error parseando mensaje: " + new String(data));
            }
            return null;
        }
    }
    
    private void listenForCommands() {
        try {
            byte[] buffer = new byte[1024];
            
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);
                
                Mensaje mensaje = Mensaje.fromBytes(Arrays.copyOf(packet.getData(), packet.getLength()));
                if (mensaje != null) {
                    procesarMensaje(mensaje, packet.getAddress(), packet.getPort());
                } else {
                    System.err.println("Mensaje no valido recibido");
                }
            }
        } catch (Exception e) {
            System.err.println("Error recibiendo comandos: " + e.getMessage());
        }
    }
    
    private void procesarMensaje(Mensaje mensaje, InetAddress clientAddress, int clientPort) {
        int seqNumber = mensaje.getSequenceNumber();
        String comando = mensaje.getComando();
        
        System.out.println("Mensaje recibido: " + comando + " [Seq:" + seqNumber + "]");
        
        if (seqNumber == nextExpectedSeq) {
            System.out.println("Secuencia esperada, procesando inmediatamente...");
            procesarYResponderComando(comando, clientAddress, clientPort, seqNumber);
            nextExpectedSeq++;
            
            procesarBufferComandos(clientAddress, clientPort);
            
        } else if (seqNumber > nextExpectedSeq) {
            receivedCommands.put(seqNumber, comando);
            System.out.println("Comando almacenado en buffer [Seq:" + seqNumber + "]");
            System.out.println("Buffer size: " + receivedCommands.size());
            
            enviarACK(nextExpectedSeq - 1, "BUFFERED", clientAddress, clientPort);
            
        } else {
            System.out.println("Comando duplicado [Seq:" + seqNumber + "], reenviando ACK");
            enviarACK(seqNumber, "DUPLICADO", clientAddress, clientPort);
        }
    }
    
    private void procesarBufferComandos(InetAddress clientAddress, int clientPort) {
        while (receivedCommands.containsKey(nextExpectedSeq)) {
            String comando = receivedCommands.remove(nextExpectedSeq);
            System.out.println("Procesando comando del buffer [Seq:" + nextExpectedSeq + "]");
            procesarYResponderComando(comando, clientAddress, clientPort, nextExpectedSeq);
            nextExpectedSeq++;
        }
    }
    
    private void procesarYResponderComando(String comando, InetAddress clientAddress, int clientPort, int seqNumber) {
        String respuesta = "";
        
        switch (comando.toUpperCase()) {
            case "CONNECT":
                respuesta = "Conectado - Servidor listo";
                break;
            case "PLAY":
                respuesta = "Comando PLAY recibido y procesado";
                break;
            case "PAUSE":
                respuesta = "Comando PAUSE recibido y procesado";
                break;
            case "STOP":
                respuesta = "Comando STOP recibido y procesado";
                break;
            case "RESTART":
                respuesta = "Comando RESTART recibido y procesado";
                break;
            case "STATUS":
                respuesta = "Servidor funcionando - Esperando comandos";
                break;
            default:
                respuesta = "Comando no reconocido: " + comando;
        }
        
        enviarACK(seqNumber, respuesta, clientAddress, clientPort);
        System.out.println("Procesado: " + comando + " [Seq:" + seqNumber + "] -> " + respuesta);
    }
    
    private void enviarACK(int ackNumber, String mensaje, InetAddress clientAddress, int clientPort) {
        try {
            Mensaje ack = new Mensaje(0, ackNumber, "ACK:" + mensaje);
            byte[] ackData = ack.toBytes();
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
            serverSocket.send(ackPacket);
            System.out.println("ACK enviado: " + ackNumber + " -> " + mensaje);
        } catch (Exception e) {
            System.err.println("Error enviando ACK: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        new Servidor();
    }
}