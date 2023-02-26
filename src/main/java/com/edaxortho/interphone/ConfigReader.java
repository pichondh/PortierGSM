package com.edaxortho.interphone;

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

    void init() {
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

    public String getCODE_PIN() {
        return CODE_PIN;
    }

    public String getPOWER_SCRIPT() {
        return POWER_SCRIPT;
    }

    public String getPORT_COM() {
        return PORT_COM;
    }

}
