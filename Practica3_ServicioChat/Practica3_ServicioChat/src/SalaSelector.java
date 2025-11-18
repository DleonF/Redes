import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

public class SalaSelector extends JFrame {
    private static final long serialVersionUID = 1L;

    // Componentes principales
    private JComboBox<String> listaSalas;
    private JTextField campoNuevaSala;
    private String nombreUsuario;

    // Constructor de la ventana para seleccionar o crear una sala
    public SalaSelector(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;

        // Configura ventana
        setTitle("Seleccionar Sala de Chat");
        setSize(350, 200);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel etiqueta = new JLabel("Selecciona una sala o crea una nueva:");

        // Combo con las salas cargadas desde el archivo
        listaSalas = new JComboBox<>(cargarSalas().toArray(new String[0]));

        // Campo de texto para escribir una nueva sala
        campoNuevaSala = new JTextField();

        // Botón para confirmar la selección
        JButton botonEntrar = new JButton("Entrar");
        botonEntrar.addActionListener(e -> entrarASala());

        // Organiza y agrega los elementos en un panel con GridLayout
        JPanel panel = new JPanel(new GridLayout(5, 1, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(etiqueta);
        panel.add(listaSalas);
        panel.add(new JLabel("O escribe una nueva sala:"));
        panel.add(campoNuevaSala);
        panel.add(botonEntrar);

        add(panel);
        setVisible(true); // Muestra la ventana
    }

    // Método que carga las salas desde el archivo 'salas.txt'
    private List<String> cargarSalas() {
        List<String> salas = new ArrayList<>();
        File archivo = new File("salas.txt");

        if (archivo.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    if (!linea.trim().isEmpty()) salas.add(linea.trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return salas;
    }

    // Método para guardar una nueva sala en el archivo
    private void guardarSala(String sala) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("salas.txt", true))) {
            bw.write(sala); // Escribe la sala
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método para entrar a la sala
    private void entrarASala() {
        // Determina si se usó el campo de texto o el combo
        String sala = campoNuevaSala.getText().trim().isEmpty()
            ? (String) listaSalas.getSelectedItem()
            : campoNuevaSala.getText().trim();

        // Verifica si el nombre de sala es válido
        if (sala == null || sala.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe seleccionar o escribir una sala.");
            return;
        }

        // Si es una sala nueva, la guarda
        if (!new File("salas.txt").exists() || !cargarSalas().contains(sala)) {
            guardarSala(sala);
        }

        // Cierra esta ventana y abre la interfaz del chat
        dispose();
        new Interfaz("230.1.1.1", 9000, nombreUsuario, sala);
    }
}