package com.edaxortho.interphone.configuration;

import com.edaxortho.interphone.InterphoneApplication;
import com.edaxortho.interphone.bean.OpeningHours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ConfigReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigReader.class);

    private String PORT_COM;
    private Integer BAUD_RATE;
    private String CODE_PIN;
    private String POWER_SCRIPT;

    private Boolean SYNTHESE_VOCALE;
    private String TEXTE_FERMETURE;

    // Page de supervision web (qualité du signal + historique, horaires
    // d'ouverture, redémarrage). Voir com.edaxortho.interphone.web.
    private Integer WEB_PORT;
    private String WEB_USERNAME;
    private String WEB_PASSWORD;
    private Integer SIGNAL_HISTORY_RETENTION_DAYS;

    // Chemin absolu du config.properties effectivement chargé (résolu dans
    // init()), pour permettre une réécriture ciblée depuis la page de
    // supervision (mise à jour des horaires d'ouverture).
    private String confPath;

    // Rechargé en mémoire par updateOpeningHours() depuis un autre thread
    // (le serveur HTTP de supervision) pendant que la boucle principale le
    // lit ; volatile pour garantir la visibilité entre threads.
    private volatile OpeningHours openingHours;

    public void init() {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            String jarPath = InterphoneApplication.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            File jarFile = new File(jarPath);
            File parentDir = jarFile.getParentFile();

            confPath = parentDir.getAbsolutePath() + "/../conf/config.properties";
            LOGGER.info("Chemin de recherche du fichier config : {}", confPath);
            input = new FileInputStream(confPath);

            // Charge les propriétés depuis le flux d'entrée
            prop.load(input);

            PORT_COM = prop.getProperty("PORT_COM");
            LOGGER.info("PORT_COM = " + PORT_COM);

            // Vitesse de communication série avec le module GSM/4G.
            // Le SIM800 (2G) fonctionnait à 9600 bauds ; le A7670E (4G, série SIMCom
            // A76XX) utilise 115200 bauds par défaut. Valeur configurable ici pour
            // s'adapter au module réellement branché ; 115200 est utilisé si la clé
            // BAUD_RATE est absente ou invalide dans config.properties.
            try {
                BAUD_RATE = Integer.parseInt(prop.getProperty("BAUD_RATE", "115200").trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("BAUD_RATE invalide dans config.properties, utilisation de 115200 par défaut (A7670E).");
                BAUD_RATE = 115200;
            }
            LOGGER.info("BAUD_RATE = " + BAUD_RATE);

            CODE_PIN = prop.getProperty("CODE_PIN");
            LOGGER.info("CODE_PIN = " + CODE_PIN);

            POWER_SCRIPT = prop.getProperty("POWER_SCRIPT");
            LOGGER.info("POWER_SCRIPT = " + POWER_SCRIPT);

            try {
                WEB_PORT = Integer.parseInt(prop.getProperty("WEB_PORT", "8080").trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("WEB_PORT invalide dans config.properties, utilisation de 8080 par défaut.");
                WEB_PORT = 8080;
            }
            WEB_USERNAME = prop.getProperty("WEB_USERNAME", "admin");
            WEB_PASSWORD = prop.getProperty("WEB_PASSWORD", "");
            if (WEB_PASSWORD == null || WEB_PASSWORD.isEmpty()) {
                LOGGER.warn("WEB_PASSWORD non défini dans config.properties : la page de supervision sera inaccessible tant qu'un mot de passe n'est pas configuré.");
            }
            try {
                SIGNAL_HISTORY_RETENTION_DAYS = Integer.parseInt(prop.getProperty("SIGNAL_HISTORY_RETENTION_DAYS", "30").trim());
            } catch (NumberFormatException e) {
                SIGNAL_HISTORY_RETENTION_DAYS = 30;
            }
            LOGGER.info("WEB_PORT = {}, WEB_USERNAME = {}", WEB_PORT, WEB_USERNAME);

            SYNTHESE_VOCALE = Boolean.parseBoolean(prop.getProperty("SYNTHESE_VOCALE"));
            TEXTE_FERMETURE = prop.getProperty("TEXTE_FERMETURE");
            if(SYNTHESE_VOCALE){
                LOGGER.info("Synthèse vocale activée avec le texte : {}", TEXTE_FERMETURE);
            }else {
                LOGGER.warn("Synthèse vocale désactivée");
            }

            initOpeningHours(prop);
            LOGGER.info(openingHours.toString());

        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
    }

    private void initOpeningHours(Properties prop) {
        this.openingHours = new OpeningHours();
        this.openingHours.setMondayOpen(openingHours.parseTime(prop.getProperty("MONDAY_OPEN")));
        this.openingHours.setMondayClose(openingHours.parseTime(prop.getProperty("MONDAY_CLOSE")));

        this.openingHours.setTuesdayOpen(openingHours.parseTime(prop.getProperty("TUESDAY_OPEN")));
        this.openingHours.setTuesdayClose(openingHours.parseTime(prop.getProperty("TUESDAY_CLOSE")));

        this.openingHours.setWednesdayOpen(openingHours.parseTime(prop.getProperty("WEDNESDAY_OPEN")));
        this.openingHours.setWednesdayClose(openingHours.parseTime(prop.getProperty("WEDNESDAY_CLOSE")));

        this.openingHours.setThursdayOpen(openingHours.parseTime(prop.getProperty("THURSDAY_OPEN")));
        this.openingHours.setThursdayClose(openingHours.parseTime(prop.getProperty("THURSDAY_CLOSE")));

        this.openingHours.setFridayOpen(openingHours.parseTime(prop.getProperty("FRIDAY_OPEN")));
        this.openingHours.setFridayClose(openingHours.parseTime(prop.getProperty("FRIDAY_CLOSE")));

        this.openingHours.setSaturdayOpen(openingHours.parseTime(prop.getProperty("SATURDAY_OPEN")));
        this.openingHours.setSaturdayClose(openingHours.parseTime(prop.getProperty("SATURDAY_CLOSE")));

        this.openingHours.setSundayOpen(openingHours.parseTime(prop.getProperty("SUNDAY_OPEN")));
        this.openingHours.setSundayClose(openingHours.parseTime(prop.getProperty("SUNDAY_CLOSE")));
    }

    public String getCODE_PIN() {
        return CODE_PIN;
    }

    public String getPOWER_SCRIPT() {
        return POWER_SCRIPT;
    }

    public String getPORT_COM() {
        return PORT_COM;
    }

    public Integer getBAUD_RATE() {
        return BAUD_RATE;
    }

    public String getTEXTE_FERMETURE() {
        return TEXTE_FERMETURE;
    }

    public OpeningHours getOpeningHours() {
        return openingHours;
    }

    public Boolean getSYNTHESE_VOCALE() {
        return SYNTHESE_VOCALE;
    }

    public Integer getWEB_PORT() {
        return WEB_PORT;
    }

    public String getWEB_USERNAME() {
        return WEB_USERNAME;
    }

    public String getWEB_PASSWORD() {
        return WEB_PASSWORD;
    }

    public Integer getSIGNAL_HISTORY_RETENTION_DAYS() {
        return SIGNAL_HISTORY_RETENTION_DAYS;
    }

    public String getConfPath() {
        return confPath;
    }

    /**
     * Met à jour les horaires d'ouverture dans config.properties (clés
     * <JOUR>_OPEN / <JOUR>_CLOSE, ex: MONDAY_OPEN) et recharge toute la
     * configuration en mémoire. Ne modifie que ces lignes précises, en
     * préservant le reste du fichier (commentaires, ordre, autres clés)
     * plutôt que d'utiliser Properties.store() qui réécrirait tout le
     * fichier sans ses commentaires.
     *
     * @param hoursByDay clé = jour en anglais majuscules (ex: "MONDAY"),
     *                   valeur = tableau [heureOuverture, heureFermeture]
     *                   au format "HH:mm"
     */
    public synchronized void updateOpeningHours(Map<String, String[]> hoursByDay) throws IOException {
        if (confPath == null) {
            throw new IOException("config.properties non initialisé (init() jamais appelé avec succès)");
        }
        List<String> lines = Files.readAllLines(Paths.get(confPath), StandardCharsets.UTF_8);
        List<String> result = new java.util.ArrayList<>(lines.size());
        for (String line : lines) {
            String replaced = line;
            for (Map.Entry<String, String[]> entry : hoursByDay.entrySet()) {
                String openKey = entry.getKey() + "_OPEN";
                String closeKey = entry.getKey() + "_CLOSE";
                if (line.startsWith(openKey + "=")) {
                    replaced = openKey + "=" + entry.getValue()[0];
                    break;
                } else if (line.startsWith(closeKey + "=")) {
                    replaced = closeKey + "=" + entry.getValue()[1];
                    break;
                }
            }
            result.add(replaced);
        }
        Files.write(Paths.get(confPath), result, StandardCharsets.UTF_8);
        LOGGER.info("Horaires d'ouverture réécrits dans {}", confPath);
        init();
    }
}
