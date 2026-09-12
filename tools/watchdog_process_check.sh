#!/bin/bash
#
# Watchdog "niveau process" pour PortierGSM.
#
# Complement du watchdog logiciel interne (SignalWatchdog, qui redemarre la
# Pi apres plusieurs echecs consecutifs du test AT+CSQ) : celui-ci ne peut
# rien detecter si la boucle principale de l'application se bloque
# completement (deadlock, freeze de la JVM...), puisque le code qui
# declenche le redemarrage ne s'execute alors plus du tout.
#
# Principe : InterphoneApplication.java ecrit l'horodatage courant dans
# conf/heartbeat.txt a chaque tour de la boucle principale (~1 fois par
# seconde). Ce script, lance regulierement par cron, verifie que ce fichier
# a bien ete mis a jour recemment. S'il est trop vieux (ou absent), on
# considere le process fige et on redemarre le service systemd.
#
# Installation (voir README, section "Watchdog niveau process") :
#   1. Rendre ce script executable : chmod +x watchdog_process_check.sh
#   2. Ajouter a la crontab de root (sudo crontab -e) :
#        * * * * * /home/pi/PortierGSM-0.2.2/tools/watchdog_process_check.sh >> /home/pi/portier_log/watchdog_process_check.log 2>&1
#
# Necessite que le service tourne via systemd (voir tools/portiergsm.service)
# et que root puisse executer `systemctl restart portiergsm` sans mot de
# passe (c'est deja le cas pour root, contrairement au watchdog logiciel qui
# tourne en tant qu'utilisateur "pi" et necessite une regle sudoers dediee).

set -u

HEARTBEAT_FILE="/home/pi/PortierGSM-0.2.2/conf/heartbeat.txt"
MAX_AGE_SECONDS=300
SERVICE_NAME="portiergsm"

now_epoch=$(date +%s)

if [ ! -f "$HEARTBEAT_FILE" ]; then
    echo "$(date -Iseconds) : fichier heartbeat absent (${HEARTBEAT_FILE}) - le service a-t-il deja demarre au moins une fois ? Aucune action (evite de redemarrer en boucle si le fichier n'existe simplement pas encore)."
    exit 0
fi

heartbeat_epoch=$(stat -c %Y "$HEARTBEAT_FILE" 2>/dev/null)
if [ -z "$heartbeat_epoch" ]; then
    echo "$(date -Iseconds) : impossible de lire la date de modification de ${HEARTBEAT_FILE}, aucune action."
    exit 0
fi

age=$((now_epoch - heartbeat_epoch))

if [ "$age" -gt "$MAX_AGE_SECONDS" ]; then
    echo "$(date -Iseconds) : heartbeat vieux de ${age}s (seuil ${MAX_AGE_SECONDS}s) - la boucle principale semble bloquee. Redemarrage du service ${SERVICE_NAME}."
    systemctl restart "$SERVICE_NAME"
else
    : # heartbeat recent, rien a faire (pas de log pour ne pas polluer inutilement, cron tourne toutes les minutes)
fi
