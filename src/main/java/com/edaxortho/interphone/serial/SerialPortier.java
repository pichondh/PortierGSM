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
            // Historique migration SIM800 -> A7670E -> SIM7600E-H (module final retenu) :
            // AT+VTS est une commande standard supportée nativement par les deux modules
            // 4G SIMCom testés (A76XX et SIM7600) et fonctionne seule, sans commande
            // préalable. AT+DDET (activation/désactivation du détecteur DTMF), utilisée
            // sur l'ancien SIM800, a été retirée : absente du jeu de commandes AT des
            // modules 4G, elle provoquerait juste une erreur AT sans rien bloquer.
            serialUtil.sendCommand("AT+VTS=*\r\n");
            Thread.sleep(1000);
        } else {
            LOGGER.info("Reception d'un appel en période de fermeture");
        }

        // Envoyer la commande AT pour raccrocher.
        // Sur le SIM7600E-H, ATH ne raccroche effectivement l'appel que si
        // AT+CVHU=0 a été envoyé au préalable (fait une fois au démarrage dans
        // InterphoneApplication) - sans quoi ATH peut ne rien faire.
        serialUtil.sendCommand("ATH\r\n");
        Thread.sleep(1000);

        // Afficher le numéro de l'appelant
        LOGGER.info("Appel terminé");
    }

    /**
     * Décroche un appel reçu en période de fermeture et tente de diffuser un message
     * vocal de synthèse (option SYNTHESE_VOCALE=true dans config.properties).
     * <p>
     * Historique : cette fonctionnalité reposait sur AT+CHFA, une commande spécifique
     * au SIM800 ("Switch Handsfree/Audio Channel") qui bascule le port série AT en un
     * mode où les octets écrits ensuite sont transmis directement comme échantillons
     * audio PCM. Absente du A7670E (module testé puis retourné), remplacé depuis par
     * le SIM7600E-H.
     * <p>
     * Piste non explorée pour le SIM7600E-H : contrairement au A7670E, ce module
     * documente AT+CSDVC (bascule du canal audio casque/haut-parleur) et la HAT
     * Waveshare expose un vrai jack audio 3.5mm câblé au module - une vraie diffusion
     * audio est donc probablement possible ici, mais nécessiterait une implémentation
     * différente (routage audio physique/AT+CSDVC, pas d'écriture de PCM brut sur le
     * port AT) qui n'a pas été faite. Tant que ce n'est pas implémenté, on se contente
     * ici de décrocher puis raccrocher sans diffuser le message, plutôt que d'envoyer
     * des données susceptibles de perturber le module.
     */
    public void sendAudio(double[] buffer) throws InterruptedException {
        LOGGER.info("Reception d'un appel en période de fermeture");

        // Décrocher l'appel
        serialUtil.sendCommand("ATA\r\n");
        Thread.sleep(1000);

        LOGGER.warn("Synthèse vocale (SYNTHESE_VOCALE=true) demandée mais non implémentée pour le " +
                "module SIM7600E-H (voir le commentaire de SerialPortier#sendAudio) : l'appel est " +
                "raccroché sans diffusion du message.");

        serialUtil.sendCommand("ATH\r\n");
        Thread.sleep(1000);

        LOGGER.info("Appel en période de fermeture, terminé");
    }
}
