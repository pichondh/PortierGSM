package com.edaxortho.interphone.configuration;

import com.edaxortho.interphone.InterphoneApplication;
import com.edaxortho.interphone.bean.OpeningHours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigReader.class);

    private String PORT_COM;
    private String CODE_PIN;
    private String POWER_SCRIPT;

    private Boolean SYNTHESE_VOCALE;
    private String TEXTE_FERMETURE;

    private OpeningHours openingHours;

    public void init() {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            String jarPath = InterphoneApplication.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            File jarFile = new File(jarPath);
            File parentDir = jarFile.getParentFile();

            String confPath = parentDir.getAbsolutePath() + "/../conf/config.properties";
            LOGGER.info("Chemin de recherche du fichier config : {}", confPath);
            input = new FileInputStream(confPath);

            // Charge les propriétés depuis le flux d'entrée
            prop.load(input);

            PORT_COM = prop.getProperty("PORT_COM");
            LOGGER.info("PORT_COM = " + PORT_COM);

            CODE_PIN = prop.getProperty("CODE_PIN");
            LOGGER.info("CODE_PIN = " + CODE_PIN);

            POWER_SCRIPT = prop.getProperty("POWER_SCRIPT");
            LOGGER.info("POWER_SCRIPT = " + POWER_SCRIPT);

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

    public String getTEXTE_FERMETURE() {
        return TEXTE_FERMETURE;
    }

    public OpeningHours getOpeningHours() {
        return openingHours;
    }

    public Boolean getSYNTHESE_VOCALE() {
        return SYNTHESE_VOCALE;
    }
}
