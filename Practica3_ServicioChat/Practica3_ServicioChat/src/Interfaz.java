import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class Interfaz extends JFrame {
    private static final long serialVersionUID = 2L;

    // Constructor principal
    public Interfaz(String host, int puerto, String nombre, String sala) {
        // Guarda los datos de conexión
        this.host = host;
        this.puerto = puerto;
        this.nombre = nombre;
        this.sala = sala;

        // Configuración básica de la ventana
        setBounds(325, 100, 400, 400);
        setTitle("Chat " + nombre + " @ " + sala); // Título dinámico con el nombre y sala
        setResizable(false);

        // Inicialización de paneles para organizar la GUI
        panelPrincipal = new JPanel();
        panelCentral = new JPanel();
        panelInferior = new JPanel();
        panelEmojis = new JPanel();
        panelFunciones = new JPanel();
        panelUsuarios = new JPanel();
        panelCombo = new JPanel();

        // Editor para mostrar los mensajes en formato HTML (permite imágenes)
        editor = new JEditorPane("text/html", null);
        editor.setEditable(false);

        // Área para escribir mensajes
        areaMensaje = new JTextArea();
        areaMensaje.setLineWrap(true); 

        // Botones de acción
        enviar = new JButton("Enviar");
        botonSalir = new JButton("Salir de la sala");

        // ComboBox para elegir el destinatario (Todos o usuarios individuales)
        usuarioConectado = new JComboBox<>();
        usuarioConectado.addItem("Todos");

        // Manejador de emojis
        escuchaEmojis = new ManejoEmojis();
        
        // Botón para enviar archivos
        botonArchivo = new JButton("Enviar Archivo");
        botonArchivo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String destino = (String) usuarioConectado.getSelectedItem();
                
                JFileChooser selector = new JFileChooser(); // Selector de archivos
                int resultado = selector.showOpenDialog(null);
                if (resultado == JFileChooser.APPROVE_OPTION) {
                    File archivo = selector.getSelectedFile();
                    //Archivo publico
                    if (destino.equals("Todos")) {
                        miCliente.enviarArchivo(archivo,"");
                    } else {
                        //Archivo privado
                        miCliente.enviarArchivo(archivo,destino);
                    }
                }
            }
        });        

        // Acción al hacer clic en "Enviar"
        enviar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String destino = (String) usuarioConectado.getSelectedItem();
                String texto = areaMensaje.getText().trim();

                if (texto.isEmpty()) return; // No envía si el mensaje está vacío

                if (destino.equals("Todos")) {
                    // Mensaje público
                    miCliente.enviar(new Mensaje(nombre + ": " + texto, nombre, "", 1, sala));
                } else {
                    // Mensaje privado
                    miCliente.enviar(new Mensaje("[Privado] " + nombre + ": " + texto, nombre, destino, 4, sala));
                }
                areaMensaje.setText(""); // Limpia el área de texto
            }
        });

        // Acción para salir de la sala
        botonSalir.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose(); // Cierra esta ventana
                miCliente.despedida(nombre); // Envía despedida
                new SalaSelector(nombre); // Abre el selector de salas otra vez
            }
        });

        // Layouts de los paneles
        panelPrincipal.setLayout(new BorderLayout(5, 5));
        panelPrincipal.add(botonSalir, BorderLayout.NORTH);

        panelCentral.setLayout(new BorderLayout(5, 5));
        panelInferior.setLayout(new BoxLayout(panelInferior, BoxLayout.Y_AXIS));
        panelFunciones.setLayout(new BoxLayout(panelFunciones, BoxLayout.X_AXIS));

        // Cuando la ventana se abre o cierra
        addWindowListener(new CorreCliente());

        // Agrega componentes a cada panel
        panelCentral.add(new JScrollPane(editor), BorderLayout.CENTER); 
        panelFunciones.add(new JScrollPane(areaMensaje));
        panelFunciones.add(enviar);
        panelFunciones.add(botonArchivo);

        panelInferior.add(panelEmojis);
        panelInferior.add(usuarioConectado);
        panelInferior.add(panelFunciones);

        panelPrincipal.add(panelCentral, BorderLayout.CENTER);
        panelPrincipal.add(panelInferior, BorderLayout.SOUTH);
        add(panelPrincipal);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true); // Muestra la ventana
    }

    // Clase interna para manejar selección de emojis
    private class ManejoEmojis implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            areaMensaje.append(" " + e.getActionCommand() + " ");
        }
    }

    // Clase interna que maneja el cliente al abrir/cerrar ventana
    private class CorreCliente extends WindowAdapter {
        public void windowOpened(WindowEvent we) {
            // Crea cliente y lo conecta al servidor
            miCliente = new Cliente(nombre, host, puerto, editor, usuarioConectado, sala);
            miCliente.saludo(nombre); // Envía saludo al ingresar
        }

        public void windowClosing(WindowEvent e) {
            // Al cerrar la ventana: despedida y cierre de conexión
            if (miCliente != null) {
                miCliente.despedida(nombre);
                miCliente.cerrarConexion();
            }
            System.exit(0);
        }
    }

    // Atributos necesarios
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
    private JPanel panelUsuarios;
    private JPanel panelCombo;

    private JEditorPane editor;
    private JTextArea areaMensaje;
    private JButton enviar;
    private JButton botonSalir;
    private JButton botonArchivo;
    public static JComboBox<String> usuarioConectado;
    private ActionListener escuchaEmojis;
    private Cliente miCliente;
}
