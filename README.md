# PortierGSM
AutoOpen door with GSM intercom

Use case :
You have a GSM intercom.
When a visitor rings, the intercom calls you.
You must pick up the phone and press *.

The goal of this project is to automate the response to the call and the pressing of the * key.

The hardware environment used is a "Raspberry PI3" with a GSM module.

## Module GSM

Le projet a été initialement développé pour un module **SIM800 (2G)**.
La 2G étant progressivement désactivée par les opérateurs, le projet a été migré
vers un module **A7670E (LTE Cat-1 / 4G, série SIMCom A76XX)**, compatible
2G en secours selon les zones.

Points d'attention lors du remplacement du module :

- **Vitesse série (BAUD_RATE)** : le SIM800 fonctionnait à 9600 bauds, le A7670E
  fonctionne à 115200 bauds par défaut. C'est désormais configurable via la clé
  `BAUD_RATE` de `config.properties` (115200 par défaut si absente).
- **Port série (PORT_COM)** : selon le module/HAT utilisé, le chemin du port AT
  sur le Raspberry Pi peut changer (ex. `/dev/ttyS0`, `/dev/ttyAMA0`,
  `/dev/ttyUSB2`...). À vérifier/adapter dans `config.properties` pour le
  A7670E.
- **Commandes AT supprimées** : `AT+DDET` et `AT+CHFA`, spécifiques au SIM800,
  n'existent pas dans le jeu de commandes AT du A7670E et ont été retirées du
  code (voir `SerialPortier.java`). Les commandes standard utilisées pour
  répondre à l'appel et envoyer la tonalité DTMF `*` (`ATA`, `AT+VTS`, `ATH`,
  `AT+CLIP`, `AT+CPAS`, `AT+CSQ`, `AT+CPIN`) restent, elles, supportées par les
  deux modules.
- **Synthèse vocale (`SYNTHESE_VOCALE=true`)** : cette option jouait un message
  audio de fermeture en écrivant les échantillons PCM directement sur le port
  série AT après `AT+CHFA=1` (astuce spécifique au SIM800). Le A7670E ne
  propose pas de canal audio numérique équivalent sur son port AT ; en l'état,
  si cette option est activée, le programme se contente de décrocher puis
  raccrocher sans diffuser le message (avec un avertissement dans les logs).
  Une implémentation alternative (interface audio USB du A7670E) resterait à
  faire si cette fonctionnalité est nécessaire.
- **Script d'alimentation (`POWER_SCRIPT`, `GSM_PWR.py`)** : ce script (hors de
  ce dépôt, déployé séparément sur le Raspberry Pi) pilote probablement la
  broche PWRKEY du module en GPIO. Le câblage/GPIO du HAT A7670E pouvant
  différer de celui du HAT SIM800, pensez à vérifier/adapter ce script sur le
  Raspberry Pi.

Lancement du programme au démarrage du RPI
/etc/rc.local
/home/pi/PortierGSM/bin/PortierGSM

