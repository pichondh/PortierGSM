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

## Page de supervision web

Une petite page web embarquée (servie directement par l'application Java,
sans dépendance externe : `com.sun.net.httpserver` du JDK) permet de
surveiller et piloter le portier depuis un navigateur, en réseau local :

- Qualité du signal GSM actuelle (CSQ + dBm) et historique (24h/7j/30j),
  mesuré à chaque test de signal déjà effectué par l'application (~1x/min).
- Consultation et modification des horaires d'ouverture (écrit directement
  dans `config.properties`, pris en compte immédiatement sans redémarrage).
- Bouton de redémarrage complet de la Raspberry Pi.

**Accès** : `http://<ip-de-la-pi>:8080` (port configurable via `WEB_PORT`
dans `config.properties`), protégé par une authentification HTTP Basic
(`WEB_USERNAME` / `WEB_PASSWORD`). ⚠️ Pensée pour un usage réseau local
uniquement : pas de HTTPS, à ne surtout pas exposer directement sur Internet
(pas de redirection de port sur la box). **Changez `WEB_PASSWORD`** dans
`config.properties` avant la mise en prod — la valeur par défaut du dépôt
(`changeme`) n'est qu'un exemple et déclenche un avertissement dans les logs
tant qu'elle n'a pas été changée.

**Configuration requise (`config.properties`)** :
```
WEB_PORT=8080
WEB_USERNAME=admin
WEB_PASSWORD=<un mot de passe à vous, pas "changeme">
SIGNAL_HISTORY_RETENTION_DAYS=30
```

**Historique du signal** : stocké dans un simple fichier CSV
(`conf/signal_history.csv`, à côté de `config.properties`), purgé
automatiquement au-delà de `SIGNAL_HISTORY_RETENTION_DAYS` jours. Pas de
base de données : le volume est faible (une ligne par minute environ) et un
fichier texte reste facile à inspecter à la main en cas de souci.

**Bouton "Redémarrer la Raspberry Pi"** : exécute `sudo reboot` depuis
l'application. Comme celle-ci tourne sous l'utilisateur `pi` (pas root), il
faut autoriser ce *seul* utilisateur à exécuter *seulement* cette commande
sans mot de passe. Sur la Pi, en root ou via `sudo visudo` :
```
# /etc/sudoers.d/portiergsm-reboot
pi ALL=(root) NOPASSWD: /sbin/reboot
```
(vérifier le chemin exact avec `which reboot` sur votre Pi — c'est en
général `/sbin/reboot` ou `/usr/sbin/reboot` sur Raspberry Pi OS ; ajuster
la ligne ci-dessus en conséquence). Sans cette règle, un clic sur le bouton
échoue silencieusement côté serveur (erreur loguée) sans planter le reste de
l'application.

**Testé en conditions réelles le 11/09/2026** : accès depuis un téléphone,
consultation/modification des horaires, tout fonctionne. Un bug bloquant
initial (erreur d'échappement dans le JS embarqué de `dashboard.html`, qui
cassait toute la page) a été identifié et corrigé après ce premier test.

**Fonctionnalités additionnelles (11/09/2026)** :

- **Numéro de build affiché sur la page** (sous le titre, ex: "Version :
  0.2.0 · build 20260911220532") : généré automatiquement par Gradle à
  chaque build (`generateBuildInfo` dans `build.gradle`, résultat dans
  `build-info.properties`, lu par `BuildInfo.java`). Permet de vérifier en
  un coup d'œil quel code tourne réellement sur la Pi, et d'éviter les
  confusions type "j'ai redéployé mais c'est toujours l'ancien code" (ce qui
  est justement arrivé lors du premier test).
- **Journal des appels reçus** : encart listant les derniers appels (heure,
  numéro appelant si disponible via `+CLIP`, "Ouvert" ou "Refusé (fermé)"
  selon que l'horaire d'ouverture était respecté). Persisté dans
  `conf/call_log.csv` (même principe que l'historique du signal), purgé au
  bout de `CALL_LOG_RETENTION_DAYS` jours (30 par défaut). Utile pour
  diagnostiquer les jours où "le déclenchement fonctionne mal" : permet de
  voir si un appel a bien été reçu et pourquoi il n'a pas ouvert (hors
  horaires) sans avoir à éplucher les logs de l'appli.
- **Période d'historique du signal par défaut : 24h** (au lieu de 7 jours).

## Watchdog et purge SMS (12/09/2026)

**Incident réel** : le 12/09/2026 à 16h20, le module SIM7600E-H a cessé de
répondre à **toute** commande AT (plus seulement AT+CSQ) pendant plus de 5
heures, sans qu'aucune exception ne soit levée côté Java. La boucle
principale a continué de tourner normalement (un test de signal par
minute, en échec silencieux), ce qui donnait l'impression que
l'application fonctionnait alors qu'elle n'entendait plus rien du tout,
y compris un appel entrant réel (RING) qui a sonné dans le vide. Seul un
redémarrage manuel de la Raspberry Pi via le bouton de la page de
supervision a résolu le problème, immédiatement.

Diagnostic tiré des logs : à `16:20:59`, le dernier test de signal réussit
normalement ; dès `16:21:00`, chaque tentative suivante (une par minute)
reçoit une réponse `null` (aucune donnée du port série), et ce sans
interruption jusqu'au redémarrage manuel à `21:30`. Environ 10 minutes
avant le blocage, une notification non sollicitée `+SMS FULL` (mémoire SMS
de la carte pleine) est arrivée en plein milieu d'un échange AT — suspect
plausible d'une désynchronisation du dialogue avec le module, sans
certitude absolue sur la cause exacte du blocage.

Deux mesures ont été ajoutées suite à cet incident, pour ne plus dépendre
d'un humain qui doit remarquer le problème et cliquer sur "redémarrer" :

- **Watchdog automatique** : la boucle principale compte les échecs
  consécutifs du test de signal (AT+CSQ sans réponse exploitable). Au bout
  de `WATCHDOG_MAX_FAILURES` échecs consécutifs (3 par défaut, donc ~3
  minutes au lieu de 5h), elle déclenche elle-même `sudo reboot` — la même
  action que le bouton de la page de supervision, avec les mêmes
  prérequis `sudoers` (voir plus haut). Un redémarrage automatique n'est
  déclenché qu'une seule fois par blocage (pas de boucle de redémarrages).
  Le module utilisé (SIM7600E-H) est prévu pour redémarrer automatiquement
  à la mise sous tension ; on ne dépend donc pas d'un script d'alimentation
  GPIO propre à l'ancien module pour la relance.
- **Purge préventive de la mémoire SMS** : toutes les
  `SMS_PURGE_INTERVAL_MINUTES` minutes (60 par défaut), l'application
  envoie `AT+CMGD=1,4` (suppression de tous les SMS). L'application
  n'utilisant pas les SMS, cette purge ne perd aucune donnée utile et vise
  à éviter la notification `+SMS FULL` observée avant l'incident.

**Configuration (`config.properties`)** :
```
WATCHDOG_MAX_FAILURES=3
SMS_PURGE_INTERVAL_MINUTES=60
```

Lancement du programme au démarrage du RPI
/etc/rc.local
/home/pi/PortierGSM/bin/PortierGSM

