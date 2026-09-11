package com.edaxortho.interphone.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Historique des mesures de qualité de signal (AT+CSQ), persisté dans un
 * simple fichier CSV à côté de config.properties. Pas de base de données :
 * le volume est faible (une mesure par minute environ) et un fichier texte
 * suffit largement sur une Raspberry Pi, tout en restant facile à inspecter
 * à la main (cat / less) en cas de souci.
 *
 * Utilisé par la page de supervision web (SupervisionServer) pour afficher
 * l'historique du signal et sa tendance.
 */
public class SignalHistoryStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalHistoryStore.class);
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Purge du fichier tous les ~1440 enregistrements (~1x/jour à raison
    // d'une mesure par minute), pour éviter de réécrire tout le fichier à
    // chaque mesure alors qu'une purge quotidienne suffit largement.
    private static final int PRUNE_EVERY_N_APPENDS = 1440;

    public static class Measurement {
        public final LocalDateTime timestamp;
        public final int csq;
        public final Integer dbm;

        public Measurement(LocalDateTime timestamp, int csq, Integer dbm) {
            this.timestamp = timestamp;
            this.csq = csq;
            this.dbm = dbm;
        }
    }

    private final File file;
    private final int retentionDays;
    private int appendsSincePrune = 0;

    private volatile Integer lastCsq;
    private volatile String lastTimestampIso;

    public SignalHistoryStore(String path, int retentionDays) {
        this.file = new File(path);
        this.retentionDays = retentionDays;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    /**
     * Conversion CSQ (0-31, 99=inconnu) -> dBm, formule standard GSM/3GPP :
     * dBm = -113 + 2*CSQ. Retourne null si CSQ hors plage exploitable (99
     * ou valeur invalide).
     */
    public static Integer csqToDbm(int csq) {
        if (csq < 0 || csq > 31) {
            return null;
        }
        return -113 + (2 * csq);
    }

    /**
     * Étiquette lisible pour un CSQ donné. Seuils indicatifs usuels pour un
     * module GSM/LTE (pas une norme stricte).
     */
    public static String qualityLabel(Integer csq) {
        if (csq == null || csq < 0 || csq > 31) {
            return "Inconnu";
        }
        if (csq >= 20) return "Excellent";
        if (csq >= 15) return "Bon";
        if (csq >= 10) return "Correct";
        if (csq >= 5) return "Faible";
        return "Très faible";
    }

    /**
     * Extrait la valeur CSQ d'une réponse brute contenant une ligne du type
     * "+CSQ: 15,99". Retourne null si aucune correspondance (réponse
     * inattendue, ERROR, timeout...).
     */
    public static Integer parseCsq(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\+CSQ:\\s*(\\d+)\\s*,\\s*(\\d+)")
                .matcher(rawResponse);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public synchronized void record(int csq) {
        LocalDateTime now = LocalDateTime.now();
        Integer dbm = csqToDbm(csq);
        String line = TS_FORMAT.format(now) + "," + csq + "," + (dbm == null ? "" : dbm);

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            LOGGER.error("Impossible d'écrire l'historique signal : {}", e.getMessage(), e);
        }

        lastCsq = csq;
        lastTimestampIso = TS_FORMAT.format(now);

        appendsSincePrune++;
        if (appendsSincePrune >= PRUNE_EVERY_N_APPENDS) {
            prune();
            appendsSincePrune = 0;
        }
    }

    public synchronized void prune() {
        if (!file.exists()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<String> kept = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Measurement m = parseLine(line);
                if (m == null || !m.timestamp.isBefore(cutoff)) {
                    kept.add(line);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Impossible de lire l'historique signal pour purge : {}", e.getMessage(), e);
            return;
        }
        try (FileWriter writer = new FileWriter(file, false)) {
            for (String line : kept) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        } catch (IOException e) {
            LOGGER.error("Impossible d'écrire l'historique signal purgé : {}", e.getMessage(), e);
        }
        LOGGER.info("Historique signal purgé (rétention {} jours, {} lignes conservées).", retentionDays, kept.size());
    }

    public synchronized List<Measurement> readHistory(int days) {
        List<Measurement> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Measurement m = parseLine(line);
                if (m != null && !m.timestamp.isBefore(cutoff)) {
                    result.add(m);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Impossible de lire l'historique signal : {}", e.getMessage(), e);
        }
        return result;
    }

    private Measurement parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 2) {
            return null;
        }
        try {
            LocalDateTime ts = LocalDateTime.parse(parts[0], TS_FORMAT);
            int csq = Integer.parseInt(parts[1]);
            Integer dbm = (parts.length > 2 && !parts[2].isEmpty()) ? Integer.parseInt(parts[2]) : null;
            return new Measurement(ts, csq, dbm);
        } catch (Exception e) {
            return null;
        }
    }

    public Integer getLastCsq() {
        return lastCsq;
    }

    public String getLastTimestampIso() {
        return lastTimestampIso;
    }
}
