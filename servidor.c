#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0600 

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include "modelos.h"

#pragma comment(lib, "ws2_32.lib")

#define MAX_ARTICULOS 100

static Articulo articulos[MAX_ARTICULOS];
static int cant_articulos = 0;

static void error(const char *msg){ fprintf(stderr, "Error: %s (WSA:%d)\n", msg, WSAGetLastError()); exit(1);}

static int sendAll(SOCKET s, const char* buf, int len){
    int sent = 0;
    while (sent < len){
        int n = send(s, buf + sent, len - sent, 0);
        if(n <= 0) return -1;
        sent += n;
    }
    return 0;
}

static int recvAll(SOCKET s, char* buf, int len){
    int recvd = 0;
    while (recvd < len){
        int n = recv(s, buf + recvd, len - recvd, 0);
        if(n <= 0) return -1;
        recvd += n;
    }
    return 0;
}

/* ---------------- Datos de ejemplo ---------------- */
static void inicializar_datos(void){
    Articulo a1 = (Articulo){1, "Laptop Gamer", "Dell", "Electronica", 1500000, 5};   // $15000.00
    Articulo a2 = (Articulo){2, "Mouse Inalambrico", "Logitech", "Electronica", 30000, 10};
    Articulo a3 = (Articulo){3, "El Principito", "Penguin", "Libros", 25000, 8};
    articulos[0] = a1; articulos[1] = a2; articulos[2] = a3;
    cant_articulos = 3;
}

/* ---------------- Respuestas (renombradas) ---------------- */
static void sendItemMsg(SOCKET s, const Articulo* a){
    MensajeTienda r = (MensajeTienda){0};
    r.tipo = sendItem;
    r.articulo = *a;
    sendAll(s, (const char*)&r, sizeof r);
}

static void sendEndMsg(SOCKET s){
    MensajeTienda r = (MensajeTienda){0};
    r.tipo = sendEnd;
    sendAll(s, (const char*)&r, sizeof r);
}

static void sendOKMsg(SOCKET s, const char* texto){
    MensajeTienda r = (MensajeTienda){0};
    r.tipo = sendOK;
    strncpy(r.datos, texto, sizeof r.datos - 1);
    sendAll(s, (const char*)&r, sizeof r);
}

static void sendErrorMsg(SOCKET s, const char* texto){
    MensajeTienda r = (MensajeTienda){0};
    r.tipo = sendError;
    strncpy(r.datos, texto, sizeof r.datos - 1);
    sendAll(s, (const char*)&r, sizeof r);
}

static void sendTicketMsg(SOCKET s, uint32_t folio, uint32_t total_cent){
    MensajeTienda r = (MensajeTienda){0};
    r.tipo = sendTicket;
    r.folio = folio; r.total_cent = total_cent;
    sendAll(s, (const char*)&r, sizeof r);
}

/* ---------------- Lógica ---------------- */
static void buscar_articulos(SOCKET cli, const char *texto){
    printf("Buscando: %s\n", texto);
    for(int i=0; i < cant_articulos; i++){
        if(strstr(articulos[i].nombre, texto) || strstr(articulos[i].marca, texto)){
            sendItemMsg(cli, &articulos[i]);
        }
    }
    sendEndMsg(cli);
}

static void listarTipo(SOCKET cli, const char *tipo){
    printf("Listando tipo: %s\n", tipo);
    for(int i=0; i < cant_articulos; i++){
        if(strcmp(articulos[i].tipo, tipo) == 0){
            sendItemMsg(cli, &articulos[i]);
        }
    }
    sendEndMsg(cli);
}

// Carrito simple en servidor por conexión
typedef struct{uint32_t id; uint16_t cantidad;} Item;
typedef struct{Item it[64]; int n;} Carrito;

static Articulo* buscarArt(uint32_t id){
    for(int i=0;i<cant_articulos;i++) if(articulos[i].id == id) return &articulos[i];
    return NULL;
}

static int carritoEdit(Carrito* c, uint32_t id, uint16_t cantidad){
    Articulo* a = buscarArt(id);
    if(!a) return 1;
    if(cantidad > a->stock) return 2;

    for(int i=0;i<c->n;i++){
        if(c->it[i].id==id){
            if(cantidad==0){
                for(int j=i;j<c->n-1;j++) c->it[j]=c->it[j+1];
                c->n--;
            } else c->it[i].cantidad = cantidad;
            return 0;
        }
    }
    if(cantidad==0) return 0;
    c->it[c->n++] = (Item){id, cantidad};
    return 0;
}

static int checkout(Carrito* c, uint32_t* total_cent){
    uint32_t total = 0;
    // Revalidar stock
    for(int i=0;i<c->n;i++){
        Articulo* a = buscarArt(c->it[i].id);
        if(!a) return 1;
        if(c->it[i].cantidad > a->stock) return 2;
    }
    // Cobrar y descontar
    for(int i=0;i<c->n;i++){
        Articulo* a = buscarArt(c->it[i].id);
        total += a->precio_cent * c->it[i].cantidad;
        a->stock -= c->it[i].cantidad;
    }
    c->n = 0;
    *total_cent = total;
    return 0;
}

/* ---------------- Dispatcher ---------------- */
static void mensajeAtender(SOCKET cli, MensajeTienda *msg, Carrito* cart){
    switch(msg->tipo){
        case CMD_BUSCAR:
            buscar_articulos(cli, msg->datos);
            break;
        case CMD_LISTAR:
            listarTipo(cli, msg->datos);
            break;
        case CMD_ADD: {
            printf("ADD/EDIT ID:%d Q:%d\n", msg->id_articulo, msg->cantidad);
            int rc = carritoEdit(cart, (uint32_t)msg->id_articulo, (uint16_t)msg->cantidad);
            if(rc==0) sendOKMsg(cli,"Carrito actualizado");
            else if(rc==1) sendErrorMsg(cli,"Producto no existe");
            else sendErrorMsg(cli,"Stock insuficiente");
        } break;
        case CMD_CHECK: {
            uint32_t total=0; int rc = checkout(cart, &total);
            if(rc==0){ sendOKMsg(cli,"Compra exitosa"); sendTicketMsg(cli, 12345, total);}
            else if(rc==1) sendErrorMsg(cli,"Producto no existe");
            else sendErrorMsg(cli,"Stock insuficiente");
        } break;
        default:
            sendErrorMsg(cli, "Tipo de mensaje desconocido");
    }
}

/* ---------------- Main ---------------- */
int main(int argc, char *argv[]){
    if(argc < 2) error("Falta puerto");

    WSADATA wsa; if(WSAStartup(MAKEWORD(2,2), &wsa) != 0) error("WSAStartup");

    inicializar_datos();

    SOCKET s = socket(AF_INET, SOCK_STREAM, 0);
    if(s == INVALID_SOCKET) error("socket");

    BOOL yes=1; setsockopt(s, SOL_SOCKET, SO_REUSEADDR, (const char*)&yes, sizeof(yes));

    struct sockaddr_in srv; memset(&srv,0,sizeof srv);
    srv.sin_family = AF_INET;
    srv.sin_addr.s_addr = INADDR_ANY;
    srv.sin_port = htons((u_short)atoi(argv[1]));

    if(bind(s, (struct sockaddr *)&srv, sizeof(srv)) == SOCKET_ERROR) error("bind");
    if(listen(s, 5) == SOCKET_ERROR) error("listen");

    printf("Servidor Tienda en %s:%s\n", "0.0.0.0", argv[1]);

    for(;;){
        struct sockaddr_in cli; int clen = sizeof(cli);
        SOCKET c = accept(s, (struct sockaddr *)&cli, &clen);
        if(c == INVALID_SOCKET){ fprintf(stderr,"accept falló\n"); continue;}

        printf("Cliente conectado\n");
        Carrito cart = (Carrito){0};

        MensajeTienda msg;
        while (1){
            if(recvAll(c, (char*)&msg, sizeof msg) <0) break;
            mensajeAtender(c, &msg, &cart);
        }
        closesocket(c);
        printf("Cliente desconectado\n");
    }

    closesocket(s);
    WSACleanup();
    return 0;
}
