gcc servidor.c -o servidor.exe -lws2_32
gcc client.c   -o client.exe   -lws2_32

## Ejecutar
servidor.exe 5000
client.exe 127.0.0.1 5000
