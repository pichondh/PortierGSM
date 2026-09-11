# PortierGSM
AutoOpen door with GSM intercom

Use case :
You have a GSM intercom.
When a visitor rings, the intercom calls you.
You must pick up the phone and press *.

The goal of this project is to automate the response to the call and the pressing of the * key.

The hardware environment used is a "Raspberry PI3" with a GSM module.

## Module GSM

Le projet a été initialement développé pour un module **SIM800 (2G)**. La 2G
étant progressivement désactivée par les opérateurs (en France : arrêt entre
le 22 septembre et le 20 octobre 2026 selon les zones), le projet a été migré :

1. Un premier essai avec un module **A7670E (LTE Cat-1 / 4G, série SIMCom
   A76XX)** a été fait, puis abandonné et retourné : ce module ne supporte
   que 2G + 4G (pas de 3G), et sans VoLTE documenté, il n'aurait plus eu
   aucun moyen de recevoir un appel vocal une fois la 2G coupée (repli CSFB
   impossible).
2. Le module retenu est un **SIM7600E-H (LTE Cat-4 / 4G+3G+2G, série SIMCom
   SIM7600)**, HAT Waveshare (bandes Europe B1/B3/B5/B7/B8/B20). Il supporte
   la 3G, ce qui garantit un repli CSFB possible pour les appels vocaux au
   moins jusqu'à l'arrêt de la 3G chez les opérateurs français (2028 chez
   Orange, dont dépend l'itinérance Free). Son firmware documente aussi une
   commande VoLTE (`AT+VOLTESETTING`), une option à creuser plus tard si
   besoin, sans garantie de provisionnement côté opérateur.

Points d'attention liés au module actuellement utilisé (SIM7600E-H) :

- **Vitesse série (BAUD_RATE)** : 115200 bauds par défaut, comme le A7670E
  testé précédemment (contre 9600 bauds pour l'ancien SIM800 2G).
  Configurable via la clé `BAUD_RATE` de `config.properties` (115200 par
  défaut si absente).
- **Port série (PORT_COM)** : à revérifier à chaque changement de module/HAT
  (le mapping des ports USB change). Le SIM7600E-H expose plusieurs ports
  (USB natif `MAIN`/`AUX`, plus le port USB-UART CP2102 selon la position du
  cavalier "UART JMP" sur la HAT) — utiliser la même méthode de découverte
  que pour le A7670E (`screen /dev/ttyUSBx 115200`, `AT+CLIP=1`, appel test)
  pour identifier le bon port avant de renseigner `PORT_COM`.
- **`AT+CVHU=0` requis pour que `ATH` raccroche** : contrairement au A7670E,
  la doc SIMCom du SIM7600 précise qu'il faut envoyer `AT+CVHU=0` avant que
  `ATH` ne raccroche effectivement un appel vocal. Cette commande est
  envoyée une fois au démarrage dans `InterphoneApplication.java`.
- **Commandes AT supprimées** : `AT+DDET` et `AT+CHFA`, spécifiques au SIM800,
  n'existent pas dans le jeu de commandes AT des modules 4G utilisés et ont
  été retirées du code (voir `SerialPortier.java`). Les commandes standard
  utilisées pour répondre à l'appel et envoyer la tonalité DTMF `*` (`ATA`,
  `AT+VTS`, `ATH`, `AT+CLIP`, `AT+CPAS`, `AT+CSQ`, `AT+CPIN`) sont supportées
  par le SIM800, le A7670E et le SIM7600E-H.
- **Synthèse vocale (`SYNTHESE_VOCALE=true`)** : cette option jouait un message
  audio de fermeture en écrivant les échantillons PCM directement sur le port
  série AT après `AT+CHFA=1` (astuce spécifique au SIM800), non reprise pour
  le A7670E ni le SIM7600E-H : en l'état, si cette option est activée, le
  programme se contente de décrocher puis raccrocher sans diffuser le message
  (avertissement dans les logs). Piste pour une vraie implémentation future :
  le SIM7600E-H documente `AT+CSDVC` (bascule du canal audio) et la HAT
  Waveshare a un jack audio 3.5mm câblé au module — probablement exploitable,
  mais pas implémenté à ce jour.
- **Alimentation du module / `POWER_SCRIPT` (`GSM_PWR.py`)** : sur le HAT
  SIM7600E-H, par défaut, un cavalier relie `PWR` à `3V3` et **le module
  s'allume tout seul dès qu'il est alimenté, sans intervention GPIO** — ce
  script n'est donc plus nécessaire (il l'était pour piloter le PWRKEY du HAT
  SIM800 via GPIO4 / pin physique 7). Le code de `tools/GSM_PWR.py` est
  désormais **commenté** (neutralisé) pour cette raison : s'il est encore
  appelé en fallback par `InterphoneApplication` (cas où le module ne répond
  pas à `AT` au démarrage), il ne fait rien. Si un contrôle logiciel du
  démarrage devient un jour nécessaire, il faudra déplacer le cavalier de la
  HAT sur `PWR`-`D6`, puis décommenter le script en remplaçant le pin GPIO4
  par le bon pin BOARD pour **GPIO6**.

Lancement du programme au démarrage du RPI
/etc/rc.local
/home/pi/PortierGSM/bin/PortierGSM

