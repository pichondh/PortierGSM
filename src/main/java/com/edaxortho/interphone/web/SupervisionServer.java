package com.edaxortho.interphone.web;

import com.edaxortho.interphone.bean.OpeningHours;
import com.edaxortho.interphone.configuration.ConfigReader;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Petit serveur HTTP embarqué (com.sun.net.httpserver du JDK, sans
 * dépendance externe) exposant une page de supervision en réseau local :
 * qualité du signal GSM avec historique, consultation/modification des
 * horaires d'ouverture, et redémarrage de la Raspberry Pi.
 *
 * Protégé par authentification HTTP Basic (WEB_USERNAME / WEB_PASSWORD dans
 * config.properties). Pensé pour un accès réseau local uniquement : pas de
 * HTTPS, à ne pas exposer directement sur Internet (pas de port forwarding).
 */
public class SupervisionServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisionServer.class);
    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final ConfigReader configReader;
    private final SignalHistoryStore signalHistoryStore;
    private final int port;
    private final String username;
    private final String password;
    private final String dashboardHtml;

    private HttpServer server;

    public SupervisionServer(ConfigReader configReader, SignalHistoryStore signalHistoryStore,
                              int port, String username, String password) throws IOException {
        this.configReader = configReader;
        this.signalHistoryStore = signalHistoryStore;
        this.port = port;
        this.username = username;
        this.password = password;
        this.dashboardHtml = loadDashboardHtml();
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

        BasicAuthenticator authenticator = new BasicAuthenticator("PortierGSM") {
            @Override
            public boolean checkCredentials(String user, String pwd) {
                return username.equals(user) && password.equals(pwd);
            }
        };

        addContext("/", this::handleDashboard, authenticator);
        addContext("/api/status", this::handleStatus, authenticator);
        addContext("/api/history", this::handleHistory, authenticator);
        addContext("/api/hours", this::handleHours, authenticator);
        addContext("/api/reboot", this::handleReboot, authenticator);

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void addContext(String path, HttpHandler handler, BasicAuthenticator authenticator) {
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

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"csq\":").append(lastCsq == null ? "null" : lastCsq).append(",");
        json.append("\"dbm\":").append(dbm == null ? "null" : dbm).append(",");
        json.append("\"quality\":\"").append(escapeJson(SignalHistoryStore.qualityLabel(lastCsq))).append("\",");
        json.append("\"lastUpdate\":").append(lastTs == null ? "null" : "\"" + lastTs + "\"").append(",");

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
}
