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
            // Envoyer la commande AT pour envoyer la tonalité DTMF correspondant à l'étoile '*'
            // Note migration SIM800 -> A7670E : AT+VTS est supporté nativement par le
            // A7670E (série SIMCom A76XX) et fonctionne seul, sans commande préalable.
            // La commande AT+DDET (activation/désactivation du détecteur DTMF), utilisée
            // ici sur le SIM800, a été retirée car elle est absente du jeu de commandes
            // AT du A7670E (cf. A76XX Series AT Command Manual) : l'envoyer provoquerait
            // une erreur AT sans bloquer le fonctionnement, mais elle est désormais inutile.
            serialUtil.sendCommand("AT+VTS=*\r\n");
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

    /**
     * Décroche un appel reçu en période de fermeture et tente de diffuser un message
     * vocal de synthèse (option SYNTHESE_VOCALE=true dans config.properties).
     * <p>
     * Note migration SIM800 -> A7670E : cette fonctionnalité reposait sur une commande
     * spécifique au SIM800, AT+CHFA ("Switch Handsfree/Audio Channel"), qui bascule le
     * port série AT en un mode où les octets écrits ensuite sont transmis directement
     * comme échantillons audio PCM vers le correspondant. AT+CHFA n'existe pas dans le
     * jeu de commandes AT du A7670E (série SIMCom A76XX) : le module ne propose pas ce
     * mode audio numérique sur son port AT. Écrire les échantillons PCM sur ce port
     * serait donc interprété comme des commandes AT invalides et risquerait de
     * désynchroniser le module (voire de nécessiter un redémarrage).
     * <p>
     * Tant qu'une solution équivalente n'a pas été implémentée pour le A7670E (le
     * module expose une interface audio numérique/USB séparée qu'il faudrait piloter
     * différemment, cf. doc SIMCom "USB AUDIO Application Note"), on se contente ici de
     * décrocher puis raccrocher sans diffuser le message, plutôt que d'envoyer des
     * données susceptibles de perturber le module.
     */
    public void sendAudio(double[] buffer) throws InterruptedException {
        LOGGER.info("Reception d'un appel en période de fermeture");

        // Décrocher l'appel
        serialUtil.sendCommand("ATA\r\n");
        Thread.sleep(1000);

        LOGGER.warn("Synthèse vocale (SYNTHESE_VOCALE=true) demandée mais non supportée en l'état " +
                "par le module A7670E (pas d'équivalent à AT+CHFA du SIM800) : l'appel est raccroché " +
                "sans diffusion du message. Voir SerialPortier#sendAudio.");

        serialUtil.sendCommand("ATH\r\n");
        Thread.sleep(1000);

        LOGGER.info("Appel en période de fermeture, terminé");
    }
}
