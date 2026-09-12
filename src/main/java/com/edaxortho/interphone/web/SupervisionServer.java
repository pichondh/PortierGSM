package com.edaxortho.interphone.web;

import com.edaxortho.interphone.bean.OpeningHours;
import com.edaxortho.interphone.configuration.ConfigReader;
import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Petit serveur HTTP embarqué (com.sun.net.httpserver du JDK, sans
 * dépendance externe) exposant une page de supervision en réseau local :
 * qualité du signal GSM avec historique, enregistrement réseau, journal des
 * appels et des incidents watchdog, consultation/modification des horaires
 * d'ouverture, et redémarrage de la Raspberry Pi.
 *
 * Protégé par authentification HTTP Basic (WEB_USERNAME / WEB_PASSWORD dans
 * config.properties), avec un verrouillage temporaire par adresse IP après
 * plusieurs échecs consécutifs (voir RateLimitedAuthenticator). Pensé pour
 * un accès réseau local uniquement : pas de HTTPS (choix assumé pour rester
 * simple d'accès pour un usage non-technique), à ne pas exposer directement
 * sur Internet (pas de port forwarding).
 */
public class SupervisionServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisionServer.class);
    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // Fenêtre glissante utilisée pour le badge de fiabilité (appels
    // acceptés/refusés) affiché sur la page simple.
    private static final int RELIABILITY_WINDOW_DAYS = 30;

    private final ConfigReader configReader;
    private final SignalHistoryStore signalHistoryStore;
    private final CallLogStore callLogStore;
    private final WatchdogIncidentStore watchdogIncidentStore;
    private final int port;
    private final String username;
    private final String password;
    private final String dashboardHtml;

    // Renseigné par InterphoneApplication après chaque test AT+CREG?
    // (voir updateNetworkStatus). null tant qu'aucun test n'a encore eu
    // lieu ou si la dernière réponse n'était pas exploitable.
    private volatile Boolean networkRegistered;

    private HttpServer server;

    public SupervisionServer(ConfigReader configReader, SignalHistoryStore signalHistoryStore,
                              CallLogStore callLogStore, WatchdogIncidentStore watchdogIncidentStore,
                              int port, String username, String password) throws IOException {
        this.configReader = configReader;
        this.signalHistoryStore = signalHistoryStore;
        this.callLogStore = callLogStore;
        this.watchdogIncidentStore = watchdogIncidentStore;
        this.port = port;
        this.username = username;
        this.password = password;
        this.dashboardHtml = loadDashboardHtml();
    }

    /**
     * Appelé par InterphoneApplication après chaque test AT+CREG?, pour
     * refléter l'état d'enregistrement réseau sur la page (indépendamment
     * de la qualité du signal CSQ : un module peut capter du signal sans
     * être enregistré sur le réseau de l'opérateur).
     */
    public void updateNetworkStatus(Boolean registered) {
        this.networkRegistered = registered;
    }

    private String loadDashboardHtml() throws IOException {
        try (InputStream in = SupervisionServer.class.getResourceAsStream("/web/dashboard.html")) {
            if (in == null) {
                throw new IOException("Ressource /web/dashboard.html introuvable dans le jar");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int read;
            while ((read = in.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        Authenticator authenticator = new RateLimitedAuthenticator("PortierGSM", username, password);

        addContext("/", this::handleDashboard, authenticator);
        addContext("/api/status", this::handleStatus, authenticator);
        addContext("/api/history", this::handleHistory, authenticator);
        addContext("/api/calls", this::handleCalls, authenticator);
        addContext("/api/calls/export", this::handleCallsExport, authenticator);
        addContext("/api/watchdog-incidents", this::handleWatchdogIncidents, authenticator);
        addContext("/api/hours", this::handleHours, authenticator);
        addContext("/api/reboot", this::handleReboot, authenticator);

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void addContext(String path, HttpHandler handler, Authenticator authenticator) {
        HttpContext context = server.createContext(path, handler);
        context.setAuthenticator(authenticator);
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        byte[] body = dashboardHtml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        Integer lastCsq = signalHistoryStore.getLastCsq();
        String lastTs = signalHistoryStore.getLastTimestampIso();
        Integer dbm = lastCsq == null ? null : SignalHistoryStore.csqToDbm(lastCsq);
        CallLogStore.Stats callStats = callLogStore.getStats(RELIABILITY_WINDOW_DAYS);
        String lastWatchdogRebootTs = watchdogIncidentStore.getLastIncidentTimestampIso();

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"csq\":").append(lastCsq == null ? "null" : lastCsq).append(",");
        json.append("\"dbm\":").append(dbm == null ? "null" : dbm).append(",");
        json.append("\"quality\":\"").append(escapeJson(SignalHistoryStore.qualityLabel(lastCsq))).append("\",");
        json.append("\"lastUpdate\":").append(lastTs == null ? "null" : "\"" + lastTs + "\"").append(",");
        json.append("\"networkRegistered\":").append(networkRegistered == null ? "null" : networkRegistered).append(",");
        json.append("\"lastWatchdogRebootTs\":").append(lastWatchdogRebootTs == null ? "null" : "\"" + lastWatchdogRebootTs + "\"").append(",");
        json.append("\"callsAccepted30d\":").append(callStats.accepted).append(",");
        json.append("\"callsRefused30d\":").append(callStats.refused).append(",");
        json.append("\"buildNumber\":\"").append(escapeJson(BuildInfo.getBuildNumber())).append("\",");
        json.append("\"projectVersion\":\"").append(escapeJson(BuildInfo.getProjectVersion())).append("\",");

        json.append("\"openingHours\":{");
        OpeningHours openingHours = configReader.getOpeningHours();
        for (int i = 0; i < DAYS.length; i++) {
            String day = DAYS[i];
            LocalTime open = openingHours.getOpeningTime(DayOfWeek.valueOf(day));
            LocalTime close = openingHours.getClosingTime(DayOfWeek.valueOf(day));
            json.append("\"").append(day).append("\":{");
            json.append("\"open\":\"").append(open == null ? "" : open.toString()).append("\",");
            json.append("\"close\":\"").append(close == null ? "" : close.toString()).append("\"");
            json.append("}");
            if (i < DAYS.length - 1) json.append(",");
        }
        json.append("}");
        json.append("}");

        sendJson(exchange, 200, json.toString());
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        int days = 7;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals("days")) {
                    try {
                        days = Math.max(1, Math.min(90, Integer.parseInt(kv[1])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        List<SignalHistoryStore.Measurement> measurements = signalHistoryStore.readHistory(days);
        StringBuilder json = new StringBuilder();
        json.append("{\"measurements\":[");
        for (int i = 0; i < measurements.size(); i++) {
            SignalHistoryStore.Measurement m = measurements.get(i);
            json.append("{\"ts\":\"").append(m.timestamp).append("\",\"csq\":").append(m.csq)
                    .append(",\"dbm\":").append(m.dbm == null ? "null" : m.dbm).append("}");
            if (i < measurements.size() - 1) json.append(",");
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void handleCalls(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        int limit = 20;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals("limit")) {
                    try {
                        limit = Math.max(1, Math.min(200, Integer.parseInt(kv[1])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        List<CallLogStore.CallEntry> calls = callLogStore.readRecent(limit);
        StringBuilder json = new StringBuilder();
        json.append("{\"calls\":[");
        for (int i = 0; i < calls.size(); i++) {
            CallLogStore.CallEntry c = calls.get(i);
            json.append("{\"ts\":\"").append(c.timestamp).append("\",\"number\":\"")
                    .append(escapeJson(c.phoneNumber)).append("\",\"accepted\":").append(c.accepted).append("}");
            if (i < calls.size() - 1) json.append(",");
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    /**
     * Export brut du journal des appels au format CSV téléchargeable
     * (bouton "Télécharger" en réglages avancés). Sert directement le
     * fichier tel que persisté par CallLogStore (timestamp,numéro,accepté).
     */
    private void handleCallsExport(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        java.io.File file = callLogStore.getFile();
        byte[] body;
        if (file.exists()) {
            body = Files.readAllBytes(file.toPath());
        } else {
            body = "timestamp,numero,accepte\n".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().add("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"appels_portiergsm.csv\"");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleWatchdogIncidents(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        int limit = 10;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals("limit")) {
                    try {
                        limit = Math.max(1, Math.min(100, Integer.parseInt(kv[1])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        List<WatchdogIncidentStore.Incident> incidents = watchdogIncidentStore.readRecent(limit);
        StringBuilder json = new StringBuilder();
        json.append("{\"incidents\":[");
        for (int i = 0; i < incidents.size(); i++) {
            WatchdogIncidentStore.Incident inc = incidents.get(i);
            json.append("{\"ts\":\"").append(inc.timestamp).append("\",\"failures\":").append(inc.consecutiveFailures).append("}");
            if (i < incidents.size() - 1) json.append(",");
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void handleHours(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        String body = readBody(exchange);
        Map<String, String> form = parseForm(body);

        Map<String, String[]> hoursByDay = new LinkedHashMap<>();
        for (String day : DAYS) {
            String open = form.get(day + "_OPEN");
            String close = form.get(day + "_CLOSE");
            if (open == null || close == null) {
                sendText(exchange, 400, "Champ manquant pour " + day);
                return;
            }
            if (!isValidTime(open) || !isValidTime(close)) {
                sendText(exchange, 400, "Format d'heure invalide pour " + day + " (attendu HH:mm)");
                return;
            }
            hoursByDay.put(day, new String[]{open, close});
        }

        try {
            configReader.updateOpeningHours(hoursByDay);
            LOGGER.info("Horaires d'ouverture mis à jour via la page de supervision.");
            sendText(exchange, 200, "OK");
        } catch (IOException e) {
            LOGGER.error("Échec de mise à jour des horaires : {}", e.getMessage(), e);
            sendText(exchange, 500, "Erreur lors de l'écriture du fichier de configuration");
        }
    }

    private void handleReboot(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Méthode non autorisée");
            return;
        }
        LOGGER.warn("Redémarrage de la Raspberry Pi demandé via la page de supervision.");
        sendText(exchange, 200, "Redémarrage en cours...");
        try {
            new ProcessBuilder("sudo", "reboot").start();
        } catch (IOException e) {
            LOGGER.error("Impossible de déclencher le redémarrage (sudoers configuré ? cf README) : {}", e.getMessage(), e);
        }
    }

    private boolean isValidTime(String value) {
        try {
            LocalTime.parse(value, HHMM);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[2048];
            int read;
            while ((read = in.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    private Map<String, String> parseForm(String body) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) {
            return result;
        }
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], "UTF-8");
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
            result.put(key, value);
        }
        return result;
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Authentification HTTP Basic maison (au lieu de BasicAuthenticator du
     * JDK) pour ajouter un verrouillage temporaire par adresse IP après
     * plusieurs échecs consécutifs : protection basique contre le
     * bourrinage de mot de passe (brute force) sur un accès réseau local
     * qui reste, par choix assumé, en HTTP simple (voir la Javadoc de
     * SupervisionServer).
     *
     * Après MAX_ATTEMPTS échecs en moins de WINDOW_MILLIS, l'IP est
     * verrouillée pendant LOCKOUT_MILLIS (réponse 429, sans même vérifier
     * le mot de passe fourni). Un identifiant correct réinitialise le
     * compteur de cette IP.
     */
    private static class RateLimitedAuthenticator extends Authenticator {

        private static final int MAX_ATTEMPTS = 5;
        private static final long WINDOW_MILLIS = 5 * 60 * 1000L;
        private static final long LOCKOUT_MILLIS = 15 * 60 * 1000L;

        private final String realm;
        private final String username;
        private final String password;
        private final Map<String, FailureRecord> failuresByIp = new ConcurrentHashMap<>();

        private static class FailureRecord {
            int count;
            long windowStart;
            long lockedUntil;
        }

        RateLimitedAuthenticator(String realm, String username, String password) {
            this.realm = realm;
            this.username = username;
            this.password = password;
        }

        @Override
        public Result authenticate(HttpExchange exchange) {
            String ip = clientIp(exchange);
            long now = System.currentTimeMillis();
            FailureRecord record = failuresByIp.computeIfAbsent(ip, k -> new FailureRecord());

            synchronized (record) {
                if (record.lockedUntil > now) {
                    long remainingSeconds = (record.lockedUntil - now) / 1000 + 1;
                    exchange.getResponseHeaders().add("Retry-After", String.valueOf(remainingSeconds));
                    return new Retry(429);
                }
            }

            boolean success = checkCredentials(exchange);

            if (success) {
                failuresByIp.remove(ip);
                return new Success(new HttpPrincipal(username, realm));
            }

            synchronized (record) {
                if (now - record.windowStart > WINDOW_MILLIS) {
                    record.windowStart = now;
                    record.count = 0;
                }
                record.count++;
                if (record.count >= MAX_ATTEMPTS) {
                    record.lockedUntil = now + LOCKOUT_MILLIS;
                    LOGGER.warn("Trop de tentatives de connexion échouées à la page de supervision depuis {} ({} essais) : verrouillé {} minutes.",
                            ip, record.count, LOCKOUT_MILLIS / 60000);
                }
            }
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
            return new Retry(401);
        }

        private boolean checkCredentials(HttpExchange exchange) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Basic ")) {
                return false;
            }
            try {
                String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
                int sep = decoded.indexOf(':');
                if (sep < 0) {
                    return false;
                }
                String user = decoded.substring(0, sep);
                String pwd = decoded.substring(sep + 1);
                return username.equals(user) && password.equals(pwd);
            } catch (Exception e) {
                return false;
            }
        }

        private String clientIp(HttpExchange exchange) {
            try {
                return exchange.getRemoteAddress().getAddress().getHostAddress();
            } catch (Exception e) {
                return "inconnu";
            }
        }
    }
}
