// client.c - Cliente de prueba para la Tienda (Winsock)
// Habla el mismo MensajeTienda de modelos.h
#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0600   // o 0x0501 como mínimo

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include "modelos.h"

#pragma comment(lib, "ws2_32.lib")

static int sendAll(SOCKET s, const char* buf, int len){
    int sent = 0;
    while(sent < len){
        int n = send(s, buf + sent, len - sent, 0);
        if(n <= 0) return -1;
        sent += n;
   }
    return 0;
}
static int recvAll(SOCKET s, char* buf, int len){
    int recvd = 0;
    while(recvd < len){
        int n = recv(s, buf + recvd, len - recvd, 0);
        if(n <= 0) return -1;
        recvd += n;
   }
    return 0;
}

static void print_art(const Articulo* a){
    printf("#%u  %-20s %-12s %-12s $%.2f  stock:%u\n",
        a->id, a->nombre, a->marca, a->tipo,
        a->precio_cent/100.0, a->stock);
}

static void send_msg(SOCKET s, const MensajeTienda* m){
    if(sendAll(s, (const char*)m, sizeof *m) < 0){
        puts("send failed"); exit(1);
   }
}

// Recibe sendItem ... hasta sendEnd. Muestra lista.
static void recv_list(SOCKET s){
    MensajeTienda r;
    for(;;){
        if(recvAll(s, (char*)&r, sizeof r) < 0){ puts("recv failed"); exit(1);}
        if(r.tipo == sendItem){ print_art(&r.articulo);}
        else if(r.tipo == sendEnd){ puts("-- fin de lista --"); break;}
        else if(r.tipo == sendError){ printf("ERR: %s\n", r.datos); break;}
        else if(r.tipo == sendOK) {printf("OK: %s\n", r.datos);} // por si el server manda status
        else{printf("(ignorado tipo=%d)\n", r.tipo);}
   }
}

// Recibe OK/ERR y, si aplica, un TICKET
static void recv_status_or_ticket(SOCKET s){
    MensajeTienda r;
    if(recvAll(s,(char*)&r,sizeof r) < 0){ puts("recv failed"); exit(1);}
    if(r.tipo == sendOK)  printf("OK: %s\n", r.datos);
    else if(r.tipo == sendError){ printf("ERR: %s\n", r.datos); return;}
    else{printf("(tipo inesperado: %d)\n", r.tipo); return;}

    // Si servidor envía ticket después del OK
    if(recvAll(s,(char*)&r,sizeof r) == 0 && r.tipo == sendTicket){
        printf("== TICKET ==\nFolio: %u\nTotal: $%.2f\n", r.folio, r.total_cent/100.0);
   }
}

int main(int argc, char** argv){
    if(argc < 3){ printf("uso: %s host puerto\n", argv[0]); return 1;}

    WSADATA wsa; if(WSAStartup(MAKEWORD(2,2), &wsa)!=0){ puts("WSAStartup fail"); return 1;}

    struct addrinfo hints={0}, *res;
    hints.ai_family = AF_INET; hints.ai_socktype = SOCK_STREAM;
    if(getaddrinfo(argv[1], argv[2], &hints, &res)!=0){ puts("getaddrinfo fail"); return 1;}

    SOCKET s = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if(s == INVALID_SOCKET){ puts("socket fail"); return 1;}
    if(connect(s, res->ai_addr, (int)res->ai_addrlen) != 0){ puts("connect fail"); return 1;}
    freeaddrinfo(res);

    MensajeTienda m; memset(&m,0,sizeof m);

    puts("1) BUSCAR texto='Lap'");
    m.tipo = CMD_BUSCAR; strcpy(m.datos, "Lap");
    send_msg(s,&m); recv_list(s);

    puts("\n2) LISTAR tipo='Electronica'");
    memset(&m,0,sizeof m);
    m.tipo = CMD_LISTAR; strcpy(m.datos,"Electronica");
    send_msg(s,&m); recv_list(s);

    puts("\n3) ADD id=1 cantidad=2");
    memset(&m,0,sizeof m);
    m.tipo = CMD_ADD; m.id_articulo = 1; m.cantidad = 2;
    send_msg(s,&m); recv_status_or_ticket(s);

    puts("\n4) CHECKOUT");
    memset(&m,0,sizeof m);
    m.tipo = CMD_CHECK;
    send_msg(s,&m); recv_status_or_ticket(s);

    closesocket(s);
    WSACleanup();
    return 0;
}
