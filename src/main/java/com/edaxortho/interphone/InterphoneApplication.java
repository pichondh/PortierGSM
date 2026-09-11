package com.edaxortho.interphone;


import com.edaxortho.interphone.configuration.ConfigReader;
import com.edaxortho.interphone.serial.SerialPortReader;
import com.edaxortho.interphone.serial.SerialPortier;
import com.edaxortho.interphone.serial.SerialPower;
import com.edaxortho.interphone.util.OpeningHoursUtil;
import com.edaxortho.interphone.util.SerialUtil;
import com.edaxortho.interphone.web.CallLogStore;
import com.edaxortho.interphone.web.SignalHistoryStore;
import com.edaxortho.interphone.web.SupervisionServer;
import com.edaxortho.marytts.PortierSpeech;
import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
            // horaires d'ouverture, redémarrage de la Pi). Fichier d'historique
            // stocké à côté de config.properties. Un échec de démarrage du
            // serveur web n'empêche pas le portier de fonctionner : on logue
            // l'erreur et on continue sans page de supervision.
            File confFile = new File(configReader.getConfPath());
            String historyPath = new File(confFile.getParentFile(), "signal_history.csv").getAbsolutePath();
            SignalHistoryStore signalHistoryStore = new SignalHistoryStore(historyPath, configReader.getSIGNAL_HISTORY_RETENTION_DAYS());
            String callLogPath = new File(confFile.getParentFile(), "call_log.csv").getAbsolutePath();
            CallLogStore callLogStore = new CallLogStore(callLogPath, configReader.getCALL_LOG_RETENTION_DAYS());
            try {
                SupervisionServer supervisionServer = new SupervisionServer(configReader, signalHistoryStore, callLogStore,
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
                    } else {
                        LOGGER.warn("Réponse AT+CSQ inattendue, mesure non enregistrée dans l'historique : {}", signalTestResponse);
                    }
                } else {
                    Thread.sleep(1000);
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