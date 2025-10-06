#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0600

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <ws2tcpip.h>         // <-- inet_pton
#include "modelos.h"

#pragma comment(lib, "ws2_32.lib")

static void error(const char *msg){
    fprintf(stderr, "Error: %s (WSA:%d)\n", msg, WSAGetLastError());
    exit(1);
}

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

void buscar(SOCKET s, const char *texto){
    MensajeTienda msg = (MensajeTienda){0};
    msg.tipo = CMD_BUSCAR;
    strncpy(msg.datos, texto, sizeof(msg.datos) - 1);
    sendAll(s, (const char*)&msg, sizeof msg);

    MensajeTienda resp;
    while (1){
        if(recvAll(s, (char*)&resp, sizeof resp) <0) break;
        if(resp.tipo == sendEnd) break;
        if(resp.tipo == sendItem)
            printf("Producto: %s (%s) $%.2f Stock:%d\n",
                   resp.articulo.nombre, resp.articulo.marca,
                   resp.articulo.precio_cent / 100.0, resp.articulo.stock);
    }
}

void listar(SOCKET s, const char *tipo){
    MensajeTienda msg = (MensajeTienda){0};
    msg.tipo = CMD_LISTAR;
    strncpy(msg.datos, tipo, sizeof(msg.datos) - 1);
    sendAll(s, (const char*)&msg, sizeof msg);

    MensajeTienda resp;
    while (1){
        if(recvAll(s, (char*)&resp, sizeof resp) <0) break;
        if(resp.tipo == sendEnd) break;
        if(resp.tipo == sendItem)
            printf("Producto: %s (%s) $%.2f Stock:%d\n",
                   resp.articulo.nombre, resp.articulo.marca,
                   resp.articulo.precio_cent / 100.0, resp.articulo.stock);
    }
}

void agregar(SOCKET s, int id, int cantidad){
    MensajeTienda msg = (MensajeTienda){0};
    msg.tipo = CMD_ADD;
    msg.id_articulo = id;
    msg.cantidad = cantidad;
    sendAll(s, (const char*)&msg, sizeof msg);

    MensajeTienda resp;
    if(recvAll(s, (char*)&resp, sizeof resp) <0) return;
    printf("%s\n", resp.datos);
}

void finalizar(SOCKET s){
    MensajeTienda msg = (MensajeTienda){0};
    msg.tipo = CMD_CHECK;
    sendAll(s, (const char*)&msg, sizeof msg);

    MensajeTienda resp;
    while (1){
        if(recvAll(s, (char*)&resp, sizeof resp) <0) break;
        if(resp.tipo == sendOK)
            printf("%s\n", resp.datos);
        else if(resp.tipo == sendTicket)
            printf("Folio: %u | Total: $%.2f\n", resp.folio, resp.total_cent / 100.0); // %u mejor para uint32_t
        else break;
    }
}

int main(int argc, char *argv[]){
    if(argc < 3){
        printf("Uso: %s <IP> <PUERTO>\n", argv[0]);
        return 1;
    }

    WSADATA wsa; if(WSAStartup(MAKEWORD(2,2), &wsa) != 0) error("WSAStartup");

    SOCKET s = socket(AF_INET, SOCK_STREAM, 0);
    if(s == INVALID_SOCKET) error("socket");

    struct sockaddr_in srv; memset(&srv, 0, sizeof srv); // <-- limpia la estructura
    srv.sin_family = AF_INET;
    srv.sin_port = htons((u_short)atoi(argv[2]));
    if(inet_pton(AF_INET, argv[1], &srv.sin_addr) != 1) error("inet_pton");

    if(connect(s, (struct sockaddr*)&srv, sizeof(srv)) == SOCKET_ERROR) error("connect");

    printf("Conectado al servidor.\n");

    buscar(s, "Laptop");
    listar(s, "Electronica");
    agregar(s, 1, 2);
    finalizar(s);

    closesocket(s);
    WSACleanup();
    return 0;
}
