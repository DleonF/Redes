import java.net.*;
import java.io.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.embed.swing.JFXPanel;

public class AudioServer {
    private MediaPlayer mediaPlayer;
    private ServerSocket serverSocket;
    
    public AudioServer() {
        new JFXPanel(); // Inicializar JavaFX
    }
    
    public void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Servidor de audio iniciado en puerto: " + port);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private class ClientHandler extends Thread {
        private Socket clientSocket;
        
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        public void run() {
            try (
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String command;
                while ((command = in.readLine()) != null) {
                    System.out.println("Comando recibido: " + command);
                    
                    switch (command) {
                        case "PLAY":
                            playMP3("audio.mp3");
                            out.println("Reproduciendo audio");
                            break;
                        case "PAUSE":
                            pausePlayback();
                            out.println("Reproducción pausada");
                            break;
                        case "STOP":
                            stopPlayback();
                            out.println("Reproducción detenida");
                            break;
                        case "VOLUME_UP":
                            setVolume(1.0);
                            out.println("Volumen máximo");
                            break;
                        case "VOLUME_DOWN":
                            setVolume(0.3);
                            out.println("Volumen bajo");
                            break;
                        default:
                            out.println("Comando no reconocido");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Métodos de reproducción (iguales a los anteriores)
    public void playMP3(String filePath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }
            
            String mediaPath = new File(filePath).toURI().toString();
            Media media = new Media(mediaPath);
            mediaPlayer = new MediaPlayer(media);
            
            mediaPlayer.setOnEndOfMedia(() -> {
                System.out.println("Reproducción finalizada automáticamente");
            });
            
            mediaPlayer.play();
            System.out.println("Reproduciendo: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    public void pausePlayback() {
        if (mediaPlayer != null) mediaPlayer.pause();
    }
    
    public void stopPlayback() {
        if (mediaPlayer != null) mediaPlayer.stop();
    }
    
    public void setVolume(double volume) {
        if (mediaPlayer != null) mediaPlayer.setVolume(volume);
    }
    
    public static void main(String[] args) {
        AudioServer server = new AudioServer();
        server.startServer(8080);
    }
}