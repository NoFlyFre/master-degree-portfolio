#!/bin/bash

# Trova l'IP di rete di en0
IP_ADDRESS=$(ipconfig getifaddr en0)

# Configura XQuartz per consentire connessioni dal client di rete
xhost + $IP_ADDRESS

# Nome del container esistente
CONTAINER_NAME="ros-mac"

# Avvia il container (se è fermo) e accedi con DISPLAY configurato
docker start $CONTAINER_NAME
docker exec -it -e DISPLAY="$IP_ADDRESS:0" $CONTAINER_NAME bash
