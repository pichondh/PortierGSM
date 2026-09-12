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
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Historique des appels reçus (numéro, horodatage, accepté ou refusé faute
 * d'être en horaire d'ouverture), persisté dans un simple fichier CSV à côté
 * de config.properties -- même approche que SignalHistoryStore, pour la
 * même raison (volume faible, facile à inspecter à la main).
 *
 * Utilisé par la page de supervision web pour afficher les derniers appels
 * et si le portier a ouvert ou non (utile pour diagnostiquer les jours où
 * "le déclenchement fonctionne mal").
 */
public class CallLogStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(CallLogStore.class);
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern CLIP_PATTERN = Pattern.compile("\\+CLIP:\\s*\"([^\"]*)\"");

    // Purge moins fréquente que pour le signal : un appel est un événement
    // rare (quelques par jour au plus) comparé à une mesure de signal par
    // minute, pas besoin de réécrire le fichier aussi souvent.
    private static final int PRUNE_EVERY_N_APPENDS = 50;

    public static class CallEntry {
        public final LocalDateTime timestamp;
        public final String phoneNumber;
        public final boolean accepted;

        public CallEntry(LocalDateTime timestamp, String phoneNumber, boolean accepted) {
            this.timestamp = timestamp;
            this.phoneNumber = phoneNumber;
            this.accepted = accepted;
        }
    }

    /**
     * Compte agrégé des appels acceptés/refusés sur une période donnée,
     * pour un badge de fiabilité simple sur la page de supervision.
     */
    public static class Stats {
        public final int accepted;
        public final int refused;

        public Stats(int accepted, int refused) {
            this.accepted = accepted;
            this.refused = refused;
        }

        public int total() {
            return accepted + refused;
        }
    }

    private final File file;
    private final int retentionDays;
    private int appendsSincePrune = 0;

    public CallLogStore(String path, int retentionDays) {
        this.file = new File(path);
        this.retentionDays = retentionDays;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    /**
     * Extrait le numéro appelant d'une réponse brute contenant une ligne
     * +CLIP (ex: +CLIP: "+33612345678",145,,,,0). Retourne "inconnu" si
     * absent (numéro masqué, ou +CLIP pas encore arrivé dans le buffer lu).
     */
    public static String parsePhoneNumber(String rawResponse) {
        if (rawResponse == null) {
            return "inconnu";
        }
        Matcher matcher = CLIP_PATTERN.matcher(rawResponse);
        if (matcher.find()) {
            String number = matcher.group(1);
            return (number == null || number.isEmpty()) ? "inconnu" : number;
        }
        return "inconnu";
    }

    public synchronized void record(String phoneNumber, boolean accepted) {
        LocalDateTime now = LocalDateTime.now();
        String safeNumber = (phoneNumber == null || phoneNumber.isEmpty()) ? "inconnu" : phoneNumber.replace(",", "");
        String line = TS_FORMAT.format(now) + "," + safeNumber + "," + accepted;

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            LOGGER.error("Impossible d'écrire le journal d'appels : {}", e.getMessage(), e);
        }

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
                CallEntry entry = parseLine(line);
                if (entry == null || !entry.timestamp.isBefore(cutoff)) {
                    kept.add(line);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Impossible de lire le journal d'appels pour purge : {}", e.getMessage(), e);
            return;
        }
        try (FileWriter writer = new FileWriter(file, false)) {
            for (String line : kept) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        } catch (IOException e) {
            LOGGER.error("Impossible d'écrire le journal d'appels purgé : {}", e.getMessage(), e);
        }
    }

    /**
     * Renvoie les <limit> derniers appels, du plus récent au plus ancien.
     */
    public synchronized List<CallEntry> readRecent(int limit) {
        List<CallEntry> all = new ArrayList<>();
        if (!file.exists()) {
            return all;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                CallEntry entry = parseLine(line);
                if (entry != null) {
                    all.add(entry);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Impossible de lire le journal d'appels : {}", e.getMessage(), e);
        }
        Collections.reverse(all);
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    /**
     * Compte les appels acceptés/refusés sur les <days> derniers jours,
     * pour un badge de fiabilité simple ("X ouverts / Y refusés").
     */
    public synchronized Stats getStats(int days) {
        int accepted = 0;
        int refused = 0;
        if (file.exists()) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    CallEntry entry = parseLine(line);
                    if (entry != null && !entry.timestamp.isBefore(cutoff)) {
                        if (entry.accepted) {
                            accepted++;
                        } else {
                            refused++;
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Impossible de lire le journal d'appels pour les statistiques : {}", e.getMessage(), e);
            }
        }
        return new Stats(accepted, refused);
    }

    /**
     * Chemin du fichier CSV brut, pour l'export téléchargeable depuis la
     * page de supervision (voir SupervisionServer#handleCallsExport).
     */
    public File getFile() {
        return file;
    }

    private CallEntry parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 3) {
            return null;
        }
        try {
            LocalDateTime ts = LocalDateTime.parse(parts[0], TS_FORMAT);
            String number = parts[1];
            boolean accepted = Boolean.parseBoolean(parts[2]);
            return new CallEntry(ts, number, accepted);
        } catch (Exception e) {
            return null;
        }
    }
}
