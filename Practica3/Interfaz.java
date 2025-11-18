import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Interfaz extends JFrame {
    private static final long serialVersionUID = 2L;

    // Constructor principal
    public Interfaz(String host, int puerto, String nombre, String sala) {
        this.host = host;
        this.puerto = puerto;
        this.nombre = nombre;
        this.sala = sala;

        // Configuración básica de la ventana
        setBounds(325, 100, 500, 500);
        setTitle("Chat " + nombre + " @ " + sala);
        setResizable(false);

        // Inicialización de paneles
        panelPrincipal = new JPanel();
        panelCentral = new JPanel();
        panelInferior = new JPanel();
        panelEmojis = new JPanel();
        panelFunciones = new JPanel();
        panelStickers = new JPanel();

        // Editor para mostrar mensajes
        editor = new JEditorPane("text/html", null);
        editor.setEditable(false);

        // Área para escribir mensajes
        areaMensaje = new JTextArea();
        areaMensaje.setLineWrap(true); 

        // Botones de acción
        enviar = new JButton("Enviar");
        botonSalir = new JButton("Salir de la sala");
        botonAudio = new JButton("🎵 Audio");
        botonStickers = new JButton("😊 Stickers");

        // ComboBox para elegir destinatario
        usuarioConectado = new JComboBox<>();
        usuarioConectado.addItem("Todos");

        // Configurar panel de stickers
        configurarPanelStickers();

        // Acción para enviar audio
        botonAudio.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enviarAudio();
            }
        });

        // Acción para mostrar stickers
        botonStickers.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panelStickers.setVisible(!panelStickers.isVisible());
            }
        });

        // Acción al hacer clic en "Enviar"
        enviar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String destino = (String) usuarioConectado.getSelectedItem();
                String texto = areaMensaje.getText().trim();

                if (texto.isEmpty()) return;

                if (destino.equals("Todos")) {
                    miCliente.enviar(new Mensaje(nombre + ": " + texto, nombre, "", Mensaje.TIPO_PUBLICO, sala));
                } else {
                    miCliente.enviar(new Mensaje("[Privado] " + nombre + ": " + texto, nombre, destino, Mensaje.TIPO_PRIVADO, sala));
                }
                areaMensaje.setText("");
            }
        });

        // Acción para salir de la sala
        botonSalir.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
                miCliente.despedida(nombre);
                new SalaSelector(nombre);
            }
        });

        // Layouts
        panelPrincipal.setLayout(new BorderLayout(5, 5));
        panelPrincipal.add(botonSalir, BorderLayout.NORTH);

        panelCentral.setLayout(new BorderLayout(5, 5));
        panelInferior.setLayout(new BoxLayout(panelInferior, BoxLayout.Y_AXIS));
        panelFunciones.setLayout(new BoxLayout(panelFunciones, BoxLayout.X_AXIS));
        panelStickers.setLayout(new GridLayout(2, 4, 5, 5));

        // Agregar componentes
        panelCentral.add(new JScrollPane(editor), BorderLayout.CENTER);
        
        panelFunciones.add(new JScrollPane(areaMensaje));
        panelFunciones.add(enviar);
        panelFunciones.add(botonAudio);
        panelFunciones.add(botonStickers);

        panelInferior.add(panelStickers);
        panelInferior.add(usuarioConectado);
        panelInferior.add(panelFunciones);

        panelPrincipal.add(panelCentral, BorderLayout.CENTER);
        panelPrincipal.add(panelInferior, BorderLayout.SOUTH);
        add(panelPrincipal);

        // Ocultar panel de stickers inicialmente
        panelStickers.setVisible(false);

        addWindowListener(new CorreCliente());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);
    }

    // Configurar panel de stickers como WhatsApp
    private void configurarPanelStickers() {
        String[] stickers = {
            "❤️", "😂", "😊", "😍", 
            "👍", "👋", "🎉", "🔥"
        };
        
        String[] stickerFiles = {
            "corazon", "risa", "feliz", "enamorado",
            "like", "saludo", "celebracion", "fuego"
        };

        for (int i = 0; i < stickers.length; i++) {
            JButton stickerBtn = new JButton(stickers[i]);
            final String stickerFile = stickerFiles[i];
            
            stickerBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    miCliente.enviarSticker(stickerFile, (String) usuarioConectado.getSelectedItem());
                }
            });
            
            stickerBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            stickerBtn.setPreferredSize(new Dimension(50, 50));
            panelStickers.add(stickerBtn);
        }
    }

    // Método para enviar audio
    private void enviarAudio() {
        String destino = (String) usuarioConectado.getSelectedItem();
        
        JFileChooser selector = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            "Archivos de Audio (WAV, MP3)", "wav", "mp3", "WAV", "MP3");
        selector.setFileFilter(filter);
        
        int resultado = selector.showOpenDialog(this);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivoAudio = selector.getSelectedFile();
            
            // Verificar tamaño (máximo 1MB)
            long tamanio = archivoAudio.length();
            if (tamanio > 1024 * 1024) {
                JOptionPane.showMessageDialog(this, 
                    "El archivo de audio es muy grande (máximo 1MB)", 
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (!esArchivoAudioValido(archivoAudio)) {
                JOptionPane.showMessageDialog(this, 
                    "El archivo no es un audio válido", 
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (destino.equals("Todos")) {
                miCliente.enviarAudio(archivoAudio, "");
            } else {
                miCliente.enviarAudio(archivoAudio, destino);
            }
        }
    }
    
    private boolean esArchivoAudioValido(File archivo) {
        String nombre = archivo.getName().toLowerCase();
        return nombre.endsWith(".wav") || nombre.endsWith(".mp3");
    }

    // Clase interna para manejar cliente
    private class CorreCliente extends WindowAdapter {
        public void windowOpened(WindowEvent we) {
            miCliente = new Cliente(nombre, host, puerto, editor, usuarioConectado, sala);
            miCliente.saludo(nombre);
        }

        public void windowClosing(WindowEvent e) {
            if (miCliente != null) {
                miCliente.despedida(nombre);
                miCliente.cerrarConexion();
            }
            System.exit(0);
        }
    }

    // Atributos
    private String host;
    private int puerto;
    private String nombre;
    private String sala;

    // Componentes gráficos
    private JPanel panelPrincipal;
    private JPanel panelCentral;
    private JPanel panelInferior;
    private JPanel panelEmojis;
    private JPanel panelFunciones;
    private JPanel panelStickers;

    private JEditorPane editor;
    private JTextArea areaMensaje;
    private JButton enviar;
    private JButton botonSalir;
    private JButton botonAudio;
    private JButton botonStickers;
    public static JComboBox<String> usuarioConectado;
    private Cliente miCliente;
}