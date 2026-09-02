#!/usr/bin/env python3
"""
Faux modem A7670E pour tester PortierGSM sans le vrai module ni le Raspberry Pi.

A utiliser avec un port serie virtuel en paire (ex: com0com, cree une paire
COM8 <-> COM9). Ce script se branche sur l'un des deux ports (ex: COM9) et
repond aux commandes AT envoyees par PortierGSM comme le ferait un A7670E
en fonctionnement nominal (SIM presente, code PIN deja valide, reseau
disponible, module deja sous tension).

PortierGSM se connecte sur l'autre port de la paire (ex: COM8) :
    PORT_COM=COM8
    BAUD_RATE=115200
dans config.properties.

Pre-requis :
    pip install pyserial

Utilisation :
    python fake_a7670e_modem.py COM9

Pendant que le script tourne, commandes disponibles dans sa console :
    ring    -> simule un appel entrant : envoie "RING" toutes les ~3s
               jusqu'a ce que PortierGSM reponde (ATA) ou raccroche (ATH),
               ou abandonne au bout de 10 sonneries (~30s) si personne ne
               decroche (comme un appelant qui raccroche).
    q       -> quitte le script

Limites : ce simulateur ne valide QUE la logique cote PortierGSM (ouverture
du port, enchainement des commandes AT, detection du RING, horaires
d'ouverture, tonalite DTMF envoyee). Il ne prouve pas que le vrai module
A7670E se comporte exactement ainsi, ni que la tonalite '*' ouvre reellement
la porte cote interphone : un test sur le materiel reel reste necessaire.
"""

import sys
import threading

try:
    import serial
except ImportError:
    print("Le module 'pyserial' est requis : pip install pyserial")
    sys.exit(1)


BAUD_RATE = 115200

# Reponses statiques a des commandes connues (cle = commande normalisee,
# sans le \r\n final que PortierGSM ajoute a chaque envoi).
RESPONSES = {
    "AT": "OK",
    "AT+CLIP=1": "OK",
    "AT+CPAS": "+CPAS: 0\r\nOK",
    "AT+VTS=*": "OK",
    "AT+CSQ": "+CSQ: 22,0\r\nOK",
    # AT+CPIN? et ATA/ATH sont geres a part car ils declenchent une action.
}


class FakeModem:
    def __init__(self, port_name):
        self.ser = serial.Serial(
            port_name, BAUD_RATE, bytesize=8, parity="N", stopbits=1, timeout=0.2
        )
        self.buffer = ""
        self.stop_ring = threading.Event()
        self.stop_ring.set()  # pas de sonnerie en cours au demarrage

    def send(self, text):
        payload = ("\r\n" + text + "\r\n").encode()
        self.ser.write(payload)
        print(f"  -> {text!r}")

    def handle_line(self, line):
        line = line.strip()
        if not line:
            return
        print(f"<- {line!r}")

        if line == "AT+CPIN?":
            self.send("+CPIN: READY\r\nOK")
            return

        if line.startswith("AT+CPIN="):
            # Saisie du code PIN (ne devrait pas arriver ici puisqu'on
            # repond READY a AT+CPIN?, mais on repond OK par realisme).
            self.send("OK")
            return

        if line == "ATA":
            # Decroche : on arrete d'envoyer des RING.
            self.stop_ring.set()
            self.send("OK")
            return

        if line == "ATH":
            self.stop_ring.set()
            self.send("OK")
            return

        resp = RESPONSES.get(line)
        if resp is not None:
            self.send(resp)
        else:
            print("  (commande inconnue pour ce simulateur -> ERROR)")
            self.send("ERROR")

    def read_loop(self):
        while True:
            data = self.ser.read(256)
            if not data:
                continue
            self.buffer += data.decode(errors="replace")
            while "\r\n" in self.buffer:
                line, self.buffer = self.buffer.split("\r\n", 1)
                self.handle_line(line)

    def ring(self):
        if not self.stop_ring.is_set():
            print("Un appel est deja en cours de simulation.")
            return
        self.stop_ring.clear()
        print("Simulation d'un appel entrant...")
        count = 0
        while not self.stop_ring.is_set() and count < 10:
            self.ser.write(b"\r\nRING\r\n")
            print("  -> 'RING'")
            self.stop_ring.wait(3)
            count += 1
        if not self.stop_ring.is_set():
            print("Personne n'a decroche apres 10 sonneries, abandon de l'appel simule.")
        self.stop_ring.set()


def main():
    if len(sys.argv) != 2:
        print("Usage : python fake_a7670e_modem.py <PORT>   (ex: COM9)")
        sys.exit(1)

    modem = FakeModem(sys.argv[1])
    reader_thread = threading.Thread(target=modem.read_loop, daemon=True)
    reader_thread.start()

    print(f"Faux modem A7670E en ecoute sur {sys.argv[1]} a {BAUD_RATE} bauds.")
    print("Commandes : 'ring' pour simuler un appel entrant, 'q' pour quitter.\n")

    while True:
        try:
            cmd = input("> ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            break
        if cmd == "ring":
            threading.Thread(target=modem.ring, daemon=True).start()
        elif cmd == "q":
            break
        elif cmd:
            print("Commandes disponibles : ring | q")


if __name__ == "__main__":
    main()
