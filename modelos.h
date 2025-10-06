#ifndef MODELOS_H
#define MODELOS_H
#include <stdint.h>

#define TXT_MAX 64

enum TipoMsg {
    CMD_BUSCAR = 1,
    CMD_LISTAR = 2,
    CMD_ADD    = 3, // agregar/editar carrito
    CMD_CHECK  = 5, // checkout
    sendItem   = 100, // respuesta con un artículo
    sendEnd    = 101, // fin de lista
    sendOK     = 102, // ok con texto
    sendError  = 103, // error con texto
    sendTicket = 104  // folio + total
};

typedef struct {
    uint32_t id;
    char     nombre[40];
    char     marca[24];
    char     tipo[16];
    uint32_t precio_cent;
    uint16_t stock;
} Articulo;

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
