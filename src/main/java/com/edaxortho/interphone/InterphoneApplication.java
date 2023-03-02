package com.edaxortho.interphone;


import com.edaxortho.interphone.configuration.ConfigReader;
import com.edaxortho.interphone.serial.SerialPortReader;
import com.edaxortho.interphone.serial.SerialPortier;
import com.edaxortho.interphone.serial.SerialPower;
import com.edaxortho.interphone.util.OpeningHoursUtil;
import com.edaxortho.interphone.util.SerialUtil;
import com.edaxortho.marytts.PortierSpeech;
import com.fazecast.jSerialComm.SerialPort;
import marytts.exceptions.MaryConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

public class InterphoneApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterphoneApplication.class);

    public static void main(String[] args) throws InterruptedException, MaryConfigurationException {

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
        //port.setBaudRate(9600);
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

            // Attendre un appel entrant
            LOGGER.info("En attente d'un appel entrant...");
            serialUtil.sendCommand("AT+CPAS\r\n");
            Thread.sleep(1000);


            while (true) {
                String msg = serialPortReader.getLastMessage();
                if (msg != null && msg.contains("RING")) {
                    SerialPortier serialPortier = new SerialPortier(port, serialUtil);
                    if (openingHoursUtil.isOpen(configReader.getOpeningHours(), LocalDateTime.now())) {
                        LOGGER.info("ON DECROCHE !!!!");
                        serialPortier.decrocheEtoileRaccroche(true);
                    } else {
                        if (configReader.getSYNTHESE_VOCALE() && portierSpeech != null) {
                            serialPortier.sendAudio(portierSpeech.getTts(configReader.getTEXTE_FERMETURE()));
                        } else {
                            serialPortier.decrocheEtoileRaccroche(false);
                        }
                    }
                }
                Thread.sleep(1000);
            }


            // Fermer le port série
//            port.closePort();
//            System.out.println("Port série fermé");
        } else {
            LOGGER.error("Impossible d'ouvrir le port série");
        }
    }
}