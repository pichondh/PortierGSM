package com.edaxortho.interphone.serial;

import com.edaxortho.interphone.util.SerialUtil;
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


    public void decrocheEtoileRaccroche(boolean withStar) throws InterruptedException {

        LOGGER.info("Reception d'un appel");

        // Décrocher l'appel
        serialUtil.sendCommand("ATA\r\n");
        Thread.sleep(1000);

        if(withStar) {
            // Envoyer la commande AT pour activer la transmission DTMF
            serialUtil.sendCommand("AT+DDET=1\r\n");
            Thread.sleep(1000);

            // Envoyer la commande AT pour envoyer la tonalité DTMF correspondant à l'étoile '*'
            serialUtil.sendCommand("AT+VTS=*\r\n");
            Thread.sleep(1000);

            // Envoyer la commande AT pour désactiver la transmission DTMF
            serialUtil.sendCommand("AT+DDET=0\r\n");
            Thread.sleep(1000);
        } else {
            LOGGER.info("Reception d'un appel en période de fermeture");
        }

        // Envoyer la commande AT pour raccrocher
        serialUtil.sendCommand("ATH\r\n");
        Thread.sleep(1000);

        // Afficher le numéro de l'appelant
        LOGGER.info("Appel terminé");
    }

    public void sendAudio(double[] buffer) throws InterruptedException {
        LOGGER.info("Reception d'un appel en période de fermeture");

        // Décrocher l'appel
        serialUtil.sendCommand("ATA\r\n");
        Thread.sleep(1000);

        short[] shorts = new short[buffer.length];
        for (int i = 0; i < buffer.length; i++) {
            shorts[i] = (short) (buffer[i] * Short.MAX_VALUE);
        }
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }

        serialUtil.sendCommand("AT+CHFA=1\r\n");
        Thread.sleep(1000);
        port.writeBytes(bytes, bytes.length);
        Thread.sleep(1000);

        serialUtil.sendCommand("ATH\r\n");
        Thread.sleep(1000);

        LOGGER.info("Appel en période de fermeture, terminé");
    }
}
