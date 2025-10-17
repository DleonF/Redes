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

void agregar(SOCKET s, int id, int cantidad) {
    MensajeTienda msg = (MensajeTienda){0};
    msg.tipo = CMD_ADD;
    msg.id_articulo = id;
    msg.cantidad = cantidad;
    sendAll(s, (const char*)&msg, sizeof msg);

    MensajeTienda resp;
    if(recvAll(s, (char*)&resp, sizeof resp) <0) return;
    
    // Mensajes más descriptivos
    if (cantidad == 0) {
        if (resp.tipo == sendOK) {
            printf("✅ Artículo eliminado del carrito\n");
        } else {
            printf("Error: %s\n", resp.datos);
        }
    } else {
        printf("%s\n", resp.datos);
    }
}

void verCarrito(SOCKET s) {
    MensajeTienda msg = (MensajeTienda){0};
    msg.tipo = CMD_CART;
    sendAll(s, (const char*)&msg, sizeof msg);

    MensajeTienda resp;
    uint32_t total_general = 0;
    int tiene_items = 0;
    int items[64] = {0}; // Para almacenar IDs mostrados
    int item_count = 0;
    
    printf("\n=== MI CARRITO ===\n");
    
    // Primera pasada: mostrar items
    while (1) {
        if(recvAll(s, (char*)&resp, sizeof resp) < 0) break;
        if(resp.tipo == sendEnd) break;
        
        if(resp.tipo == sendCartItem) {
            tiene_items = 1;
            items[item_count] = resp.articulo.id;
            printf("%d. ID:%d | %s (%s) | $%.2f x %d = $%.2f\n",
                   item_count + 1,  // Número para eliminar
                   resp.articulo.id, resp.articulo.nombre, resp.articulo.marca,
                   resp.articulo.precio_cent / 100.0, resp.cantidad,
                   resp.total_articulo / 100.0);
            item_count++;
        }
        else if(resp.tipo == sendTicket) {
            total_general = resp.total_cent;
            printf("-----------------------------------\n");
            printf("Total: $%.2f\n", total_general / 100.0);
        }
    }
    
    if (!tiene_items) {
        printf("Carrito vacío\n");
        printf("==================\n\n");
        return;
    }
    
    printf("==================\n\n");
    
    // Menú de opciones para el carrito
    int opcion_carrito;
    do {
        printf("Opciones del carrito:\n");
        printf("1. Eliminar articulo\n");
        printf("2. Modificar cantidad\n");
        printf("3. Volver al menu principal\n");
        printf("Seleccione una opcion: ");
        scanf("%d", &opcion_carrito);
        scanf("%*c"); // Limpiar buffer
        
        switch(opcion_carrito) {
            case 1: {
                // Eliminar artículo
                if (item_count == 0) {
                    printf("Carrito vacio\n");
                    break;
                }
                
                int num_eliminar;
                printf("Ingrese el numero del articulo a eliminar (1-%d): ", item_count);
                scanf("%d", &num_eliminar);
                scanf("%*c");
                
                if (num_eliminar >= 1 && num_eliminar <= item_count) {
                    int id_eliminar = items[num_eliminar - 1];
                    // Enviar comando para eliminar (cantidad = 0)
                    agregar(s, id_eliminar, 0);
                    printf("Articulo eliminado del carrito.\n");
                    return; // Volver a cargar el carrito
                } else {
                    printf("Numero invalido.\n");
                }
                break;
            }
            case 2: {
                // Modificar cantidad
                if (item_count == 0) {
                    printf("Carrito vacio\n");
                    break;
                }
                
                int num_modificar, nueva_cantidad;
                printf("Ingrese el numero del articulo a modificar (1-%d): ", item_count);
                scanf("%d", &num_modificar);
                printf("Ingrese la nueva cantidad (0 para eliminar): ");
                scanf("%d", &nueva_cantidad);
                scanf("%*c");
                
                if (num_modificar >= 1 && num_modificar <= item_count) {
                    int id_modificar = items[num_modificar - 1];
                    if (nueva_cantidad >= 0) {
                        agregar(s, id_modificar, nueva_cantidad);
                        if (nueva_cantidad == 0) {
                            printf("Articulo eliminado del carrito.\n");
                        } else {
                            printf("Cantidad actualizada.\n");
                        }
                        return; // Volver a cargar el carrito
                    } else {
                        printf("Cantidad invalida.\n");
                    }
                } else {
                    printf("Numero invalido.\n");
                }
                break;
            }
            case 3:
                printf("Volviendo al menu principal...\n");
                break;
            default:
                printf("Opcion no valida.\n");
                break;
        }
    } while (opcion_carrito != 3);
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
    int Opcion;
    char texto[64];
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
    while (1) {
        printf("Bienvenido a la tienda en linea.\n");
        printf("Ingrese lo que desea hacer:\n");
        printf("1. Buscar productos (ejemplo: Laptop)\n");
        printf("2. Listar productos por tipo (ejemplo: Electronica)\n");
        printf("3. Agregar producto al carrito (ejemplo: 1 2)\n");
        printf("4. Ver carrito\n");
        printf("5. Finalizar compra\n");
        scanf("%d", &Opcion);
        switch (Opcion){
        case 1:
            scanf("%*c"); // Lee el \n al ingresar una opcion y lo desecha
            printf("Ingrese el texto a buscar:\n");
            fgets(texto, sizeof(texto), stdin);
            texto[strcspn(texto, "\n")] = 0; // Eliminar el salto de línea
            buscar(s, texto);
            break;
        case 2:
            scanf("%*c"); // Lee el \n al ingresar una opcion y lo desecha
            printf("Ingrese el tipo de producto a listar:\n");
            fgets(texto, sizeof(texto), stdin);
            texto[strcspn(texto, "\n")] = 0; // Eliminar el salto de línea
            listar(s, texto);
            break;
        case 3:
            scanf("%*c"); // Lee el \n al ingresar una opcion y lo desecha
            int id, cantidad;
            printf("Ingrese el ID del producto y la cantidad a agregar:\n");
            scanf("%d %d", &id, &cantidad);
            agregar(s, id, cantidad);
            break;
        case 4:
            scanf("%*c");
            verCarrito(s);
            break;
        case 5:
            scanf("%*c"); // Lee el \n al ingresar una opcion y lo desecha
            finalizar(s);
            break;
        default:
            break;
        }
    }
    closesocket(s);
    WSACleanup();
    return 0;
}
