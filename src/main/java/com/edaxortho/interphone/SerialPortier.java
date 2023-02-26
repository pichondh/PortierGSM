package com.edaxortho.interphone;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialPortier {

    private static final Logger LOGGER = LoggerFactory.getLogger(SerialPortier.class);
    public final SerialPort port;
    public final SerialUtil serialUtil;

    public SerialPortier(SerialPort port, SerialUtil serialUtil) {
        this.port = port;
        this.serialUtil = serialUtil;
    }


    public void decrocheEtoileRaccroche() throws InterruptedException {

        LOGGER.info("Reception d'un appel");

        // Décrocher l'appel
        serialUtil.sendCommand("ATA\r\n");
        Thread.sleep(1000);

        // Envoyer la commande AT pour activer la transmission DTMF
        serialUtil.sendCommand("AT+DDET=1\r\n");
        Thread.sleep(1000);

        // Envoyer la commande AT pour envoyer la tonalité DTMF correspondant à l'étoile '*'
        serialUtil.sendCommand("AT+VTS=*\r\n");
        Thread.sleep(1000);

        // Envoyer la commande AT pour désactiver la transmission DTMF
        serialUtil.sendCommand("AT+DDET=0\r\n");
        Thread.sleep(1000);

//        SerialAudio serialAudio = new SerialAudio(port, serialUtil);
//        serialAudio.playFile("E:\\Musiques\\La_carioca.mp3");

        // Envoyer la commande AT pour raccrocher
        serialUtil.sendCommand("ATH\r\n");
        Thread.sleep(1000);

        // Afficher le numéro de l'appelant
        LOGGER.info("Appel terminé");
    }
}
