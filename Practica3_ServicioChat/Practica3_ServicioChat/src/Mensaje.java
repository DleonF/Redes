import java.io.*;

public class Mensaje implements Serializable {
    private static final long serialVersionUID = 3L;

    // Constructor para mensajes simples (texto)
    public Mensaje(String mensaje, String usuarioOrigen, String usuarioDestino, int tipo, String sala) {
        this.mensaje = mensaje;                 // Contenido del mensaje
        this.usuarioOrigen = usuarioOrigen;     // Usuario que envía el mensaje
        this.usuarioDestino = usuarioDestino;   // Usuario que recibe (vacío si es público)
        this.tipo = tipo;                       // Tipo de mensaje (0: saludo, 1: público, 4: privado, etc.)
        this.sala = sala;                       // Sala a la que pertenece el mensaje
    }

    // Constructor para envío de archivos
    public Mensaje(String nombreArchivo, String usuarioOrigen, String usuarioDestino, int tipo, long tamanio, String ruta, int np, String sala) {
        this.nombreArchivo = nombreArchivo;     // Nombre del archivo
        this.usuarioOrigen = usuarioOrigen;
        this.usuarioDestino = usuarioDestino;
        this.tamanio = tamanio;                 // Tamaño del archivo en bytes
        this.ruta = ruta;                       // Ruta del archivo original (opcional)
        this.np = np;                           // Número de paquete (para fragmentar el archivo)
        this.tipo = tipo;
        this.sala = sala;
    }

    // Getters para acceder a los atributos
    public String getMensaje() { return mensaje; }
    public String getUsuarioOrigen() { return usuarioOrigen; }
    public String getUsuarioDestino() { return usuarioDestino; }
    public int getTipo() { return tipo; }
    public String getSala() { return sala; }

    // Setters para modificar los atributos
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public void setUsuarioOrigen(String usuarioOrigen) { this.usuarioOrigen = usuarioOrigen; }
    public void setUsuarioDestino(String usuarioDestino) { this.usuarioDestino = usuarioDestino; }
    public void setTipo(int tipo) { this.tipo = tipo; }
    public void setSala(String sala) { this.sala = sala; }

    // Métodos relacionados con envío/recepción de archivos
    public String getNombre() { return nombreArchivo; }
    public long getTamanio() { return tamanio; }
    public String ruta() { return ruta; }
    public int getNp() { return np; }                       // Número de paquete
    public byte[] getDatos() { return datos; }              // Fragmento de datos
    public int getBytesEnviados() { return bytesEnviados; } // Tamaño del fragmento enviado

    public void setDatos(byte[] datos) { this.datos = datos; }
    public void setBytesEnviados(int bytesEnviados) { this.bytesEnviados = bytesEnviados; }

    // Atributos privados
    private String mensaje;
    private String usuarioOrigen;
    private String usuarioDestino;
    private int tipo;
    private String sala;

    // Archivos
    private String nombreArchivo;
    private long tamanio;
    private String ruta;
    private int np;
    private byte[] datos;
    private int bytesEnviados;
}