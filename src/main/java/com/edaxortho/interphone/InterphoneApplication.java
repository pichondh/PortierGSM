package com.edaxortho.interphone;


import com.edaxortho.interphone.configuration.ConfigReader;
import com.edaxortho.interphone.serial.SerialPortReader;
import com.edaxortho.interphone.serial.SerialPortier;
import com.edaxortho.interphone.serial.SerialPower;
import com.edaxortho.interphone.util.NetworkStatusUtil;
import com.edaxortho.interphone.util.OpeningHoursUtil;
import com.edaxortho.interphone.util.SerialUtil;
import com.edaxortho.interphone.watchdog.SignalWatchdog;
import com.edaxortho.interphone.web.CallLogStore;
import com.edaxortho.interphone.web.SignalHistoryStore;
import com.edaxortho.interphone.web.SupervisionServer;
import com.edaxortho.interphone.web.WatchdogIncidentStore;
import com.edaxortho.marytts.PortierSpeech;
import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

public class InterphoneApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterphoneApplication.class);

    public static void main(String[] args) throws InterruptedException {

        OpeningHoursUtil openingHoursUtil = new OpeningHoursUtil();
        ConfigReader configReader = new ConfigReader();
        configReader.init();
        PortierSpeech portierSpeech = null;
        if (configReader.getSYNTHESE_VOCALE()) {
            portierSpeech = new PortierSpeech();
        }

        // Sélectionnez le port série à utiliser
        SerialPort port = SerialPort.getCommPort(configReader.getPORT_COM());

        // Configurez les paramètres du port série
        // Module SIM7600E-H (4G Cat-4, série SIMCom SIM7600) : 115200 bauds par
        // défaut (identique au A7670E testé initialement, et différent des 9600
        // bauds de l'ancien SIM800 2G). Valeur lue depuis config.properties (clé
        // BAUD_RATE) pour rester adaptable si le module venait encore à changer.
        port.setBaudRate(configReader.getBAUD_RATE());
        port.setNumDataBits(8);
        port.setParity(SerialPort.NO_PARITY);
        port.setNumStopBits(1);

        // Définir un écouteur pour les événements de réception de données du port série
        SerialPortReader serialPortReader = new SerialPortReader(port);

        // Ouvrez le port série
        if (port.openPort()) {
            System.out.println("Port série ouvert avec succès");
            port.addDataListener(serialPortReader);
            SerialUtil serialUtil = new SerialUtil(port);

            // Historique de signal + page de supervision web (qualité du signal,
            // horaires d'ouverture, redémarrage de la Pi). Fichiers d'historique
            // stockés à côté de config.properties. Un échec de démarrage du
            // serveur web n'empêche pas le portier de fonctionner : on logue
            // l'erreur et on continue sans page de supervision.
            File confFile = new File(configReader.getConfPath());
            String historyPath = new File(confFile.getParentFile(), "signal_history.csv").getAbsolutePath();
            SignalHistoryStore signalHistoryStore = new SignalHistoryStore(historyPath, configReader.getSIGNAL_HISTORY_RETENTION_DAYS());
            String callLogPath = new File(confFile.getParentFile(), "call_log.csv").getAbsolutePath();
            CallLogStore callLogStore = new CallLogStore(callLogPath, configReader.getCALL_LOG_RETENTION_DAYS());
            String watchdogIncidentsPath = new File(confFile.getParentFile(), "watchdog_incidents.csv").getAbsolutePath();
            WatchdogIncidentStore watchdogIncidentStore = new WatchdogIncidentStore(watchdogIncidentsPath, configReader.getWATCHDOG_INCIDENT_RETENTION_DAYS());
            File heartbeatFile = new File(confFile.getParentFile(), "heartbeat.txt");

            SupervisionServer supervisionServer = null;
            try {
                supervisionServer = new SupervisionServer(configReader, signalHistoryStore, callLogStore, watchdogIncidentStore,
                        configReader.getWEB_PORT(), configReader.getWEB_USERNAME(), configReader.getWEB_PASSWORD());
                supervisionServer.start();
                LOGGER.info("Page de supervision démarrée sur le port {}", configReader.getWEB_PORT());
            } catch (IOException e) {
                LOGGER.error("Impossible de démarrer la page de supervision web : {}", e.getMessage(), e);
            }

            serialUtil.sendCommand("AT\r\n");
            Thread.sleep(1000);

            String atResponse = serialPortReader.getLastMessage();
            if (atResponse == null || atResponse.isEmpty()) {
                try {
                    LOGGER.warn("Init du module nécessaire...");
                    SerialPower.callPowerScript(configReader.getPOWER_SCRIPT());
                    LOGGER.warn("Init du module faite.");
                    Thread.sleep(5000);
                    serialUtil.sendCommand("AT\r\n");
                    Thread.sleep(1000);
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }

            serialUtil.sendCommand("AT+CPIN?\r\n");
            Thread.sleep(1000);
            String simResponse = serialPortReader.getLastMessage();
            if (simResponse == null || !simResponse.contains("+CPIN: READY")) {
                LOGGER.warn("Saisie du code PIN nécessaire...");
                serialUtil.sendCommand("AT+CPIN=" + configReader.getCODE_PIN() + "\r\n");
                LOGGER.warn("Saisie du code PIN.");
                Thread.sleep(1000);
            }

            serialUtil.sendCommand("AT+CLIP=1\r\n");
            Thread.sleep(1000);

            // Autoriser ATH à raccrocher un appel vocal. Nécessaire sur le SIM7600E-H
            // (série SIMCom SIM7600) : contrairement au A7670E où ATH raccrochait
            // directement, la doc SIMCom précise "Before using ATH command to hang up
            // a voice call, it must set AT+CVHU=0" - sans ça, SerialPortier#ATH risque
            // de ne pas raccrocher l'appel. Sans effet néfaste si le module ne
            // reconnaît pas la commande (répond simplement ERROR).
            serialUtil.sendCommand("AT+CVHU=0\r\n");
            Thread.sleep(1000);

            // Attendre un appel entrant
            LOGGER.info("En attente d'un appel entrant...");
            serialUtil.sendCommand("AT+CPAS\r\n");
            Thread.sleep(1000);

            int signalTest = 60;

            // Watchdog (ajouté suite à l'incident du 12/09/2026) : le module
            // SIM7600E-H a cessé de répondre à toute commande AT pendant plus
            // de 5h, sans que le process Java ne plante ni ne le remarque -
            // la boucle continuait de tourner normalement (donc "l'appli
            // semblait fonctionner") pendant que le module, lui, était muet
            // et ne signalait plus aucun appel entrant. Logique extraite dans
            // SignalWatchdog (testable indépendamment). Chaque déclenchement
            // est aussi journalisé dans watchdogIncidentStore pour garder une
            // trace consultable sur la page de supervision.
            SignalWatchdog signalWatchdog = new SignalWatchdog(configReader.getWATCHDOG_MAX_FAILURES());

            // Purge préventive périodique de la mémoire SMS du module
            // (AT+CMGD=1,4). Une notification non sollicitée "+SMS FULL" a
            // été observée à plusieurs reprises dans les logs, y compris peu
            // avant l'incident du 12/09/2026 ; une mémoire SMS pleine est un
            // suspect plausible de désynchronisation du dialogue AT avec le
            // module. L'application n'utilise pas les SMS : purger sans
            // condition ne perd aucune donnée utile.
            int smsPurgeCountdownSeconds = configReader.getSMS_PURGE_INTERVAL_MINUTES() * 60;

            while (true) {
                String msg = serialPortReader.getLastMessage();
                if (msg != null) {
                    if (msg.contains("RING")) {
                        SerialPortier serialPortier = new SerialPortier(port, serialUtil);
                        String callerNumber = CallLogStore.parsePhoneNumber(msg);
                        boolean isOpen = openingHoursUtil.isOpen(configReader.getOpeningHours(), LocalDateTime.now());
                        if (isOpen) {
                            LOGGER.info("ON DECROCHE !!!!");
                            serialPortier.decrocheEtoileRaccroche(true);
                        } else {
                            if (configReader.getSYNTHESE_VOCALE() && portierSpeech != null) {
                                serialPortier.sendAudio(portierSpeech.getTts(configReader.getTEXTE_FERMETURE()));
                            } else {
                                serialPortier.decrocheEtoileRaccroche(false);
                            }
                        }
                        callLogStore.record(callerNumber, isOpen);
                        LOGGER.info("Appel de {} {} (journalisé)", callerNumber, isOpen ? "accepté" : "refusé (fermé)");
                    } else {
                        LOGGER.info(">>> {}", msg);
                    }
                }
                signalTest--;
                if (signalTest <= 0) {
                    signalTest = 60;
                    LOGGER.info("Test du signal...");
                    serialUtil.sendCommand("AT+CSQ\r\n");
                    Thread.sleep(1000);
                    String signalTestResponse = serialPortReader.getLastMessage();
                    LOGGER.info("Signal : {}", signalTestResponse);
                    Integer csqValue = SignalHistoryStore.parseCsq(signalTestResponse);
                    if (csqValue != null) {
                        signalHistoryStore.record(csqValue);
                        if (signalWatchdog.getConsecutiveFailures() > 0) {
                            LOGGER.info("Le module répond de nouveau normalement après {} échec(s) consécutif(s).", signalWatchdog.getConsecutiveFailures());
                        }
                        signalWatchdog.onSignalTestResult(true);
                    } else {
                        boolean shouldReboot = signalWatchdog.onSignalTestResult(false);
                        LOGGER.warn("Réponse AT+CSQ inattendue, mesure non enregistrée dans l'historique ({}/{} échecs consécutifs) : {}",
                                signalWatchdog.getConsecutiveFailures(), configReader.getWATCHDOG_MAX_FAILURES(), signalTestResponse);
                        if (shouldReboot) {
                            LOGGER.error("WATCHDOG : le module GSM ne répond plus depuis {} tentatives consécutives (~{} min). Redémarrage automatique de la Raspberry Pi.",
                                    signalWatchdog.getConsecutiveFailures(), signalWatchdog.getConsecutiveFailures());
                            watchdogIncidentStore.record(signalWatchdog.getConsecutiveFailures());
                            try {
                                new ProcessBuilder("sudo", "reboot").start();
                            } catch (IOException e) {
                                LOGGER.error("WATCHDOG : impossible de déclencher le redémarrage automatique (sudoers configuré ? cf README) : {}", e.getMessage(), e);
                            }
                        }
                    }

                    // Vérification de l'enregistrement réseau (AT+CREG?), en
                    // complément du CSQ : un signal correct ne garantit pas
                    // que le module est réellement enregistré sur le réseau
                    // de l'opérateur (voir NetworkStatusUtil).
                    serialUtil.sendCommand("AT+CREG?\r\n");
                    Thread.sleep(1000);
                    String cregResponse = serialPortReader.getLastMessage();
                    Boolean registered = NetworkStatusUtil.parseRegistered(cregResponse);
                    if (supervisionServer != null) {
                        supervisionServer.updateNetworkStatus(registered);
                    }
                    if (registered != null && !registered) {
                        LOGGER.warn("Module non enregistré sur le réseau mobile (AT+CREG? : {})", cregResponse);
                    }
                } else {
                    Thread.sleep(1000);
                }

                smsPurgeCountdownSeconds--;
                if (smsPurgeCountdownSeconds <= 0) {
                    smsPurgeCountdownSeconds = configReader.getSMS_PURGE_INTERVAL_MINUTES() * 60;
                    LOGGER.info("Purge périodique de la mémoire SMS du module (AT+CMGD=1,4)...");
                    serialUtil.sendCommand("AT+CMGD=1,4\r\n");
                    Thread.sleep(1000);
                }

                // Fichier "battement de coeur" (watchdog niveau process, voir
                // tools/watchdog_process_check.sh et README) : un script
                // externe (cron) vérifie que ce fichier est régulièrement
                // mis à jour, et redémarre le service si la boucle
                // principale elle-même se bloque (deadlock, freeze JVM...),
                // ce que le watchdog signal seul ne peut pas détecter.
                try {
                    Files.write(heartbeatFile.toPath(), String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    LOGGER.warn("Impossible d'écrire le fichier heartbeat ({}) : {}", heartbeatFile, e.getMessage());
                }
            }


            // Fermer le port série
//            port.closePort();
//            System.out.println("Port série fermé");
        } else {
            LOGGER.error("Impossible d'ouvrir le port série");
        }
    }
}
