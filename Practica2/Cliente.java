import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class Cliente {
    private DatagramSocket socket;
    private InetAddress multicastGroup;
    private Clip audioClip;
    private boolean isPlaying = false;

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
        public void updateTimestamp() { this.timestamp = System.currentTimeMillis(); }
        
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
    
    // Ventana deslizante
    private int nextSeqNumber = 0;
    private int windowSize = 6;
    private Map<Integer, Mensaje> sentMessages = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int lastAckReceived = -1;
    private int reenvios = 0;
    
    // Buffer para respuestas del servidor
    private final Object responseLock = new Object();
    private String ultimaRespuesta = "";
    
    public Cliente() {
        try {
            socket = new DatagramSocket();
            multicastGroup = InetAddress.getByName("ff3e:40:2001::1");
            
            System.out.println("Cliente de Audio con Ventana Deslizante iniciado...");
            System.out.println("Tamaño de ventana: " + windowSize);
            
            cargarAudioLocal();
            startClient();
            
        } catch (Exception e) {
            System.err.println("Error iniciando cliente: " + e.getMessage());
        }
    }
    
    private void cargarAudioLocal() {
        try {
            File audioFile = new File("Prueba.wav");
            if (!audioFile.exists()) {
                System.err.println("Archivo de audio no encontrado localmente");
                System.err.println("Coloca 'Prueba.wav' en la misma carpeta que el cliente");
                return;
            }
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            audioClip = AudioSystem.getClip();
            audioClip.open(audioStream);
            System.out.println("Audio cargado localmente");
            
        } catch (Exception e) {
            System.err.println("Error cargando audio local: " + e.getMessage());
        }
    }
    
    private void startClient() {
        new Thread(this::recibirRespuestas).start();
        new Thread(this::verificarTimeouts).start();
        enviarComandos();
    }

    private void demostrarVentanaDeslizante() {
    System.out.println("\n=== DEMOSTRACION VENTANA DESLIZANTE ===");
    System.out.println("Enviando 6 comandos SIN esperar ACKs...");
    System.out.println("Tamaño de ventana: " + windowSize);
    
    String[] comandos = {"PLAY", "PAUSE", "STOP", "RESTART", "STATUS", "PAUSE"};
    
    for (int i = 0; i < comandos.length; i++) {
        String comando = comandos[i];
        
        // Ejecutar localmente
        procesarComandoLocal(comando);
        
        // Pequeña pausa para ver los mensajes (100ms)
        try { 
            Thread.sleep(100); 
        } catch (InterruptedException e) {}
    }
}

    private void enviarComandoRapido(String comando) {
        // Versión simplificada que solo envía, no ejecuta localmente
        if (sentMessages.size() >= windowSize) {
            System.out.println("VENTANA LLENA - No se puede enviar: " + comando);
            return;
        }
        
        int currentSeq = nextSeqNumber++;
        Mensaje mensaje = new Mensaje(currentSeq, lastAckReceived, comando);
        
        try {
            byte[] datos = mensaje.toBytes();
            DatagramPacket packet = new DatagramPacket(datos, datos.length, multicastGroup, 7777);
            socket.send(packet);
            
            sentMessages.put(currentSeq, mensaje);
            System.out.println("Enviado: " + comando + " [Seq:" + currentSeq + ", Ventana:" + sentMessages.size() + "/" + windowSize + "]");
            
        } catch (Exception e) {
            System.err.println("Error enviando comando: " + e.getMessage());
        }
    }
    
    private void recibirRespuestas() {
        try {
            byte[] buffer = new byte[1024];
            System.out.println("Escuchando ACKs del servidor...");
            
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                Mensaje respuesta = Mensaje.fromBytes(Arrays.copyOf(packet.getData(), packet.getLength()));
                if (respuesta != null) {
                    procesarACK(respuesta);
                }
            }
        } catch (Exception e) {
            System.err.println("Error recibiendo respuestas: " + e.getMessage());
        }
    }
    
    private void procesarACK(Mensaje ack) {
        int ackNumber = ack.getAckNumber();
        
        synchronized (responseLock) {
            System.out.println("\n=== RESPUESTA DEL SERVIDOR ===");
            System.out.println("ACK recibido para secuencia: " + ackNumber);
            
            if (ackNumber > lastAckReceived) {
                lastAckReceived = ackNumber;
            }
            
            int antes = sentMessages.size();
            sentMessages.entrySet().removeIf(entry -> entry.getKey() <= ackNumber);
            int despues = sentMessages.size();
            
            if (antes != despues) {
                System.out.println("Ventana liberada: " + despues + "/" + windowSize + " mensajes en vuelo");
            }
            
            if (!ack.getComando().startsWith("ACK:")) {
                String mensajeServidor = ack.getComando().replace("ACK:", "");
                System.out.println("Servidor: " + mensajeServidor);
                ultimaRespuesta = mensajeServidor;
            }
            System.out.println("==============================\n");
        }
        
        // Mostrar prompt después de procesar la respuesta
        mostrarPrompt();
    }
    
    private void verificarTimeouts() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            sentMessages.forEach((seq, msg) -> {
                if (currentTime - msg.getTimestamp() > 2000) {
                    synchronized (responseLock) {
                        System.out.println("\n=== TIMEOUT ===");
                        System.out.println("Timeout para secuencia " + seq + ", reenviando...");
                        System.out.println("================\n");
                    }
                    reenviarMensaje(msg);
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    private void reenviarMensaje(Mensaje mensaje) {
        try {
            mensaje.updateTimestamp();
            
            byte[] datos = mensaje.toBytes();
            DatagramPacket packet = new DatagramPacket(datos, datos.length, multicastGroup, 7777);
            socket.send(packet);
            
            reenvios++;
            synchronized (responseLock) {
                System.out.println("\n=== REENVIO ===");
                System.out.println("Reenviado: " + mensaje.getComando() + " [Seq:" + mensaje.getSequenceNumber() + "] (Reenvio #" + reenvios + ")");
                System.out.println("================\n");
            }
            
        } catch (Exception e) {
            System.err.println("Error reenviando mensaje: " + e.getMessage());
        }
    }
    
    private void enviarComandoConVentana(String comando) {
        if (sentMessages.size() >= windowSize) {
            System.out.println("Ventana llena (" + sentMessages.size() + "/" + windowSize + "), esperando ACKs...");
            return;
        }
        
        int currentSeq = nextSeqNumber++;
        Mensaje mensaje = new Mensaje(currentSeq, lastAckReceived, comando);
        
        try {
            byte[] datos = mensaje.toBytes();
            DatagramPacket packet = new DatagramPacket(datos, datos.length, multicastGroup, 7777);
            socket.send(packet);
            
            sentMessages.put(currentSeq, mensaje);
            System.out.println("Enviado: " + comando + " [Seq:" + currentSeq + ", Ventana:" + sentMessages.size() + "/" + windowSize + "]");
            
        } catch (Exception e) {
            System.err.println("Error enviando comando: " + e.getMessage());
        }
    }
    
    private void mostrarPrompt() {
        synchronized (responseLock) {
            System.out.print("Ingresa comando: ");
        }
    }
    
    private void enviarComandos() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n=== CONTROL DE AUDIO (VENTANA DESLIZANTE) ===");
        System.out.println("Comandos disponibles:");
        System.out.println("PLAY    - Reproducir audio LOCAL");
        System.out.println("PAUSE   - Pausar audio LOCAL"); 
        System.out.println("STOP    - Detener audio LOCAL");
        System.out.println("RESTART - Reiniciar audio LOCAL");
        System.out.println("STATUS  - Estado del audio LOCAL");
        System.out.println("TEST    - Probar ventana deslizante (6 comandos rapidos)");
        System.out.println("EXIT    - Salir del cliente");
        System.out.println("============================================\n");
        
        // Mostrar primer prompt
        System.out.print("Ingresa comando: ");
        
        while (true) {
            String comando = scanner.nextLine().trim();
            
            if ("EXIT".equalsIgnoreCase(comando)) {
                break;
            }
            
            if (!comando.isEmpty()) {
                procesarComandoLocal(comando);
            }
            
            // No mostrar prompt aquí - se mostrará después de cada respuesta
        }
        
        scanner.close();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        if (audioClip != null) audioClip.close();
        socket.close();
        System.out.println("Cliente terminado. Total reenvios: " + reenvios);
    }
    
        private void procesarComandoLocal(String comando) {
        String comandoUpper = comando.toUpperCase();
        
        // Caso especial para TEST
        if ("TEST".equals(comandoUpper)) {
            demostrarVentanaDeslizante();
            return;
        }
        
        // Ejecutar localmente inmediatamente
        switch (comandoUpper) {
            case "PLAY":
                reproducirAudioLocal();
                break;
            case "PAUSE":
                pausarAudioLocal();
                break;
            case "STOP":
                detenerAudioLocal();
                break;
            case "RESTART":
                reiniciarAudioLocal();
                break;
            case "STATUS":
                mostrarEstadoLocal();
                break;
            case "MUTE":
                System.out.println("Comando MUTE ejecutado localmente");
                break;
            default:
                System.out.println("Comando no reconocido: " + comando);
                System.out.print("Ingresa comando: ");
                return;
        }
        
        // Enviar comando al servidor con ventana deslizante
        enviarComandoConVentana(comandoUpper);
    }
    
    private void reproducirAudioLocal() {
        if (audioClip == null) {
            System.out.println("Error: Audio no disponible localmente");
            return;
        }
        if (!isPlaying) {
            audioClip.start();
            isPlaying = true;
            System.out.println("Reproduciendo audio LOCAL");
        } else {
            System.out.println("Audio ya se esta reproduciendo LOCALMENTE");
        }
    }
    
    private void pausarAudioLocal() {
        if (audioClip == null) {
            System.out.println("Error: Audio no disponible localmente");
            return;
        }
        if (isPlaying) {
            audioClip.stop();
            isPlaying = false;
            System.out.println("Audio LOCAL pausado");
        } else {
            System.out.println("Audio LOCAL ya esta pausado");
        }
    }
    
    private void detenerAudioLocal() {
        if (audioClip == null) {
            System.out.println("Error: Audio no disponible localmente");
            return;
        }
        audioClip.stop();
        audioClip.setFramePosition(0);
        isPlaying = false;
        System.out.println("Audio LOCAL detenido y reiniciado");
    }
    
    private void reiniciarAudioLocal() {
        if (audioClip == null) {
            System.out.println("Error: Audio no disponible localmente");
            return;
        }
        audioClip.setFramePosition(0);
        if (!isPlaying) {
            audioClip.start();
            isPlaying = true;
            System.out.println("Audio LOCAL reiniciado y reproduciendo");
        } else {
            System.out.println("Audio LOCAL reiniciado (continuando reproduccion)");
        }
    }
    
    private void mostrarEstadoLocal() {
        if (audioClip == null) {
            System.out.println("Estado: Audio no cargado localmente");
        } else {
            String estado = isPlaying ? "Reproduciendo LOCALMENTE" : "Pausado LOCALMENTE";
            long posicion = audioClip.getMicrosecondPosition() / 1000000;
            long duracion = audioClip.getMicrosecondLength() / 1000000;
            System.out.println(estado + " | Tiempo: " + posicion + "/" + duracion + "s");
        }
    }
    
    public static void main(String[] args) {
        new Cliente();
    }
}