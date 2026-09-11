package com.edaxortho.interphone.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Numéro de build (horodatage) et version du projet, générés par Gradle à
 * chaque build (voir build.gradle, tâche generateBuildInfo) dans le fichier
 * build-info.properties. Affiché sur la page de supervision pour vérifier
 * facilement quel code tourne réellement sur la Pi, et éviter les
 * confusions du type "j'ai redéployé mais c'est toujours l'ancien code".
 */
public final class BuildInfo {

    private static final String UNKNOWN = "dev";

    private static final String BUILD_NUMBER;
    private static final String PROJECT_VERSION;

    static {
        Properties props = new Properties();
        String buildNumber = UNKNOWN;
        String version = UNKNOWN;
        try (InputStream in = BuildInfo.class.getResourceAsStream("/build-info.properties")) {
            if (in != null) {
                props.load(in);
                buildNumber = props.getProperty("BUILD_NUMBER", UNKNOWN);
                version = props.getProperty("PROJECT_VERSION", UNKNOWN);
            }
        } catch (IOException ignored) {
            // build-info.properties absent (ex: exécution hors build Gradle) -> valeurs par défaut
        }
        BUILD_NUMBER = buildNumber;
        PROJECT_VERSION = version;
    }

    private BuildInfo() {
    }

    public static String getBuildNumber() {
        return BUILD_NUMBER;
    }

    public static String getProjectVersion() {
        return PROJECT_VERSION;
    }
}
