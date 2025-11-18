import javax.swing.*;
import java.awt.BorderLayout;

public class Main extends JFrame {
    private static final long serialVersionUID = 1L;

    public Main() {
        // Configura la ventana principal
        setBounds(200, 200, 300, 110);
        setResizable(false);
        setTitle("Chat");

        // Paneles principales
        panelPrincipal = new JPanel();
        panelCentral = new JPanel();
        panelPrincipal.setLayout(new BorderLayout(5, 5));
        panelCentral.setLayout(new BoxLayout(this.panelCentral, BoxLayout.Y_AXIS));

        // Etiqueta y campo de texto para el nombre de usuario
        nombreUsuario = new JLabel("Introduce tu nombre de usuario:");
        campoUsuario = new JTextField(30);

        // Botón para conectar al chat
        botonConectar = new JButton("Conectar");

        // Evento al hacer clic en "Conectar"
        botonConectar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // Verifica que el campo no esté vacío
                if (!campoUsuario.getText().equals("")) {
                    setVisible(false);  // Oculta la ventana actual
                    new SalaSelector(campoUsuario.getText().trim());  // Abre selector de sala
                } else {
                    // Muestra mensaje de error si el campo está vacío
                    JOptionPane.showMessageDialog(Main.this, "Nombre de usuario vacío", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Agrega los componentes al panel central
        panelCentral.add(nombreUsuario);
        panelCentral.add(campoUsuario);

        // Agrega paneles al panel principal y luego a la ventana
        panelPrincipal.add(panelCentral, BorderLayout.CENTER);
        panelPrincipal.add(botonConectar, BorderLayout.SOUTH);
        add(panelPrincipal);

        // Configura el cierre de la ventana
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);  // Muestra la ventana
    }

    public static void main(String[] args) {
        new Main();  // Inicia la aplicación
    }

    // Constantes para IP y puerto multicast (pueden usarse más adelante)
    private final String HOST = "230.1.1.1";
    private final int PUERTO = 9000;

    // Componentes de la interfaz
    private JPanel panelPrincipal;
    private JPanel panelCentral;
    private JLabel nombreUsuario;
    private JTextField campoUsuario;
    private JButton botonConectar;
}
