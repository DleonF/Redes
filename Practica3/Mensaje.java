import java.io.*;

public class Mensaje implements Serializable {
    private static final long serialVersionUID = 3L;

    // Tipos de mensaje
    public static final int TIPO_SALUDO = 0;
    public static final int TIPO_PUBLICO = 1;
    public static final int TIPO_AUDIO = 2;
    public static final int TIPO_STICKER = 6;
    public static final int TIPO_CONFIRMACION = 3;
    public static final int TIPO_PRIVADO = 4;
    public static final int TIPO_DESPEDIDA = 5;
    public static final int TIPO_LISTA_USUARIOS = 7;

    // Constructor para mensajes de texto
    public Mensaje(String mensaje, String usuarioOrigen, String usuarioDestino, int tipo, String sala) {
        this.mensaje = mensaje;
        this.usuarioOrigen = usuarioOrigen;
        this.usuarioDestino = usuarioDestino;
        this.tipo = tipo;
        this.sala = sala;
    }

    // Constructor para audio
    public Mensaje(String nombreArchivo, String usuarioOrigen, String usuarioDestino, int tipo, 
                  long tamanio, int np, String sala, byte[] datosAudio) {
        this.nombreArchivo = nombreArchivo;
        this.usuarioOrigen = usuarioOrigen;
        this.usuarioDestino = usuarioDestino;
        this.tipo = tipo;
        this.tamanio = tamanio;
        this.np = np;
        this.sala = sala;
        this.datosAudio = datosAudio;
    }

    // Constructor para sticker
    public Mensaje(String nombreSticker, String usuarioOrigen, String usuarioDestino, int tipo, 
                  String sala, byte[] datosSticker) {
        this.nombreArchivo = nombreSticker;
        this.usuarioOrigen = usuarioOrigen;
        this.usuarioDestino = usuarioDestino;
        this.tipo = tipo;
        this.sala = sala;
        this.datosSticker = datosSticker;
    }

    // Getters
    public String getMensaje() { return mensaje; }
    public String getUsuarioOrigen() { return usuarioOrigen; }
    public String getUsuarioDestino() { return usuarioDestino; }
    public int getTipo() { return tipo; }
    public String getSala() { return sala; }
    public String getNombre() { return nombreArchivo; }
    public long getTamanio() { return tamanio; }
    public int getNp() { return np; }
    public byte[] getDatosAudio() { return datosAudio; }
    public byte[] getDatosSticker() { return datosSticker; }

    // Setters
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public void setUsuarioOrigen(String usuarioOrigen) { this.usuarioOrigen = usuarioOrigen; }
    public void setUsuarioDestino(String usuarioDestino) { this.usuarioDestino = usuarioDestino; }
    public void setTipo(int tipo) { this.tipo = tipo; }
    public void setSala(String sala) { this.sala = sala; }
    public void setDatosAudio(byte[] datosAudio) { this.datosAudio = datosAudio; }
    public void setDatosSticker(byte[] datosSticker) { this.datosSticker = datosSticker; }

    // Atributos
    private String mensaje;
    private String usuarioOrigen;
    private String usuarioDestino;
    private int tipo;
    private String sala;
    
    // Audio/Sticker
    private String nombreArchivo;
    private long tamanio;
    private int np;
    private byte[] datosAudio;
    private byte[] datosSticker;
}