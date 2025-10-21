import java.io.File;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class Servidor {
    private MulticastSocket serverSocket;
    private InetAddress multicastGroup;
    private Clip audioClip;
    private boolean isPlaying = false;

    public Servidor() {
        try {
            multicastGroup = InetAddress.getByName("ff3e:40:2001::1");
            serverSocket = new MulticastSocket(7777);
            serverSocket.joinGroup(multicastGroup);
            serverSocket.setReuseAddress(true);
            serverSocket.setTimeToLive(255);

            System.out.println("Servidor de Audio Multicast iniciado...");
            System.out.println("Grupo: " + multicastGroup + ", Puerto: 7777");
            System.out.println("Unido al grupo multicast correctamente");

            loadAudioFile("C:\\Users\\HP\\Documents\\Uni\\Redes 2\\Redes\\Practica2\\Prueba.wav");
            listenForCommands();

        } catch (Exception e) {
            System.err.println("Error iniciando servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void loadAudioFile(String filename) { 
        try {
            File audioFile = new File(filename);
            if (!audioFile.exists()) {
                System.err.println("Archivo no encontrado: " + filename);
                return;
            }
            
            System.out.println("Cargando archivo: " + filename);
            System.out.println("Tamaño: " + audioFile.length() + " bytes");
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            audioClip = AudioSystem.getClip();
            audioClip.open(audioStream);
            System.out.println("Audio cargado: " + filename);
            
        } catch (Exception e) {
            System.err.println("Error cargando audio: " + e.getMessage());
            if (e instanceof javax.sound.sampled.UnsupportedAudioFileException) {
                System.err.println("Formato de audio no soportado. Use archivos WAV compatibles.");
            }
        }
    }
    
    private void listenForCommands() {
        try {
            byte[] buffer = new byte[1024];
            System.out.println("Escuchando comandos multicast...");
            
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);
                
                String command = new String(packet.getData(), 0, packet.getLength()).trim();
                String clientInfo = packet.getAddress() + ":" + packet.getPort();
                
                System.out.println("Comando recibido desde " + clientInfo + ": " + command);
                
                processCommand(command, packet.getAddress(), packet.getPort());
            }
        } catch (Exception e) {
            System.err.println("Error recibiendo comandos: " + e.getMessage());
        }
    }
    
    private void processCommand(String command, InetAddress clientAddress, int clientPort) {
        try {
            String response = "";
            
            if (audioClip == null) {
                response = "Error: Audio no disponible";
                sendResponse(response, clientAddress, clientPort);
                return;
            }
            
            switch (command.toUpperCase()) {
                case "CONNECT":
                    if (!isPlaying) {
                        audioClip.start();
                        isPlaying = true;
                        response = "Conectado - Reproduciendo audio automáticamente";
                    } else {
                        response = "Conectado - Audio ya se está reproduciendo";
                    }
                    break;
                    
                case "PLAY":
                    if (!isPlaying) {
                        audioClip.start();
                        isPlaying = true;
                        response = "Reproduciendo audio";
                    } else {
                        response = "El audio ya se está reproduciendo";
                    }
                    break;
                    
                case "PAUSE":
                    if (isPlaying) {
                        audioClip.stop();
                        isPlaying = false;
                        response = "Audio pausado";
                    } else {
                        response = "El audio ya está pausado";
                    }
                    break;
                    
                case "STOP":
                    audioClip.stop();
                    audioClip.setFramePosition(0);
                    isPlaying = false;
                    response = "Audio detenido y reiniciado";
                    break;
                    
                case "RESTART":
                    audioClip.setFramePosition(0);
                    if (!isPlaying) {
                        audioClip.start();
                        isPlaying = true;
                    }
                    response = "Audio reiniciado desde el inicio";
                    break;
                    
                case "STATUS":
                    response = isPlaying ? "Estado: Reproduciendo" : "Estado: Pausado";
                    break;
                    
                default:
                    response = "Comando no reconocido: " + command;
            }
            
            sendResponse(response, clientAddress, clientPort);
            
        } catch (Exception e) {
            System.err.println("Error procesando comando: " + e.getMessage());
        }
    }
    
    private void sendResponse(String message, InetAddress clientAddress, int clientPort) {
        try {
            byte[] responseData = message.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(
                responseData, responseData.length, clientAddress, clientPort);
            serverSocket.send(responsePacket);
            
            System.out.println("Respuesta enviada: " + message);
        } catch (Exception e) {
            System.err.println("Error enviando respuesta: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        new Servidor();
    }
}