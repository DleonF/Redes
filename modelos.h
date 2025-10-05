// modelos.h
#ifndef MODELOS_H
#define MODELOS_H
#include <stdint.h>

#define TXT_MAX 64

// Códigos de mensaje
enum TipoMsg {
    CMD_BUSCAR = 1,
    CMD_LISTAR = 2,
    CMD_ADD    = 3, // agregar/editar carrito
    CMD_CHECK  = 5, // checkout
    sendItem   = 100, // respuesta con un artículo
    sendEnd    = 101, // fin de lista
    sendOK     = 102, // ok con texto
    sendError    = 103, // error con texto
    sendTicket = 104  // folio + total
};

// Artículo con campos de tamaño fijo (precio en centavos)
typedef struct {
    uint32_t id;                  // host order (si quisieras NBO, usa htonl/ntohl al enviar/recibir)
    char     nombre[40];
    char     marca[24];
    char     tipo[16];            // "Electronica", "Libros", etc.
    uint32_t precio_cent;         // 15000.00 -> 1500000
    uint16_t stock;
} Articulo;

// Mensaje “tamaño fijo” para simplificar la práctica
typedef struct {
    int32_t  tipo;                // enum TipoMsg
    int32_t  id_articulo;         // para ADD/CHECK
    int32_t  cantidad;            // para ADD (0 elimina)
    char     datos[TXT_MAX];      // texto de búsqueda o tipo
    Articulo articulo;            // en respuestas sendItem
    uint32_t folio;               // en sendTicket
    uint32_t total_cent;          // en sendTicket
} MensajeTienda;

#endif
