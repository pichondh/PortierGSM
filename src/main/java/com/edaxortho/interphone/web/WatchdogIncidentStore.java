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

/**
 * Historique des déclenchements du watchdog logiciel (voir
 * com.edaxortho.interphone.watchdog.SignalWatchdog), persisté dans un
 * simple fichier CSV à côté de config.properties -- même approche que
 * SignalHistoryStore et CallLogStore.
 *
 * Ajouté suite à l'incident du 12/09/2026, pour garder une trace des
 * redémarrages automatiques et repérer si le module se bloque de plus en
 * plus souvent (signe d'usure à surveiller), plutôt que de devoir éplucher
 * les logs bruts pour retrouver ces événements.
 */
public class WatchdogIncidentStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchdogIncidentStore.class);
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static class Incident {
        public final LocalDateTime timestamp;
        public final int consecutiveFailures;

        public Incident(LocalDateTime timestamp, int consecutiveFailures) {
            this.timestamp = timestamp;
            this.consecutiveFailures = consecutiveFailures;
        }
    }

    private final File file;
    private final int retentionDays;

    private volatile String lastIncidentTimestampIso;

    public WatchdogIncidentStore(String path, int retentionDays) {
        this.file = new File(path);
        this.retentionDays = retentionDays;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        // Initialise lastIncidentTimestampIso depuis le fichier existant,
        // pour que la page affiche correctement le dernier incident connu
        // même juste après un redémarrage de l'application.
        List<Incident> existing = readRecent(1);
        if (!existing.isEmpty()) {
            lastIncidentTimestampIso = TS_FORMAT.format(existing.get(0).timestamp);
        }
    }

    public synchronized void record(int consecutiveFailures) {
        LocalDateTime now = LocalDateTime.now();
        String line = TS_FORMAT.format(now) + "," + consecutiveFailures;

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            LOGGER.error("Impossible d'écrire le journal des incidents watchdog : {}", e.getMessage(), e);
        }

        lastIncidentTimestampIso = TS_FORMAT.format(now);
        prune();
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
                Incident entry = parseLine(line);
                if (entry == null || !entry.timestamp.isBefore(cutoff)) {
                    kept.add(line);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Impossible de lire le journal des incidents watchdog pour purge : {}", e.getMessage(), e);
            return;
        }
        try (FileWriter writer = new FileWriter(file, false)) {
            for (String line : kept) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        } catch (IOException e) {
            LOGGER.error("Impossible d'écrire le journal des incidents watchdog purgé : {}", e.getMessage(), e);
        }
    }

    /**
     * Renvoie les <limit> derniers incidents, du plus récent au plus ancien.
     */
    public synchronized List<Incident> readRecent(int limit) {
        List<Incident> all = new ArrayList<>();
        if (!file.exists()) {
            return all;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Incident entry = parseLine(line);
                if (entry != null) {
                    all.add(entry);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Impossible de lire le journal des incidents watchdog : {}", e.getMessage(), e);
        }
        Collections.reverse(all);
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public String getLastIncidentTimestampIso() {
        return lastIncidentTimestampIso;
    }

    private Incident parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 2) {
            return null;
        }
        try {
            LocalDateTime ts = LocalDateTime.parse(parts[0], TS_FORMAT);
            int failures = Integer.parseInt(parts[1]);
            return new Incident(ts, failures);
        } catch (Exception e) {
            return null;
        }
    }
}
