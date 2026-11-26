package com.veniilocal;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/** Local protocol: UDP discovery, TCP auth(67), status(888), start(1020). */
public final class VeniiClient {
    private static final String HOST = "192.168.78.1";
    private static final int PORT = 9000;
    private static final int AUTH = 67, STATUS = 888, START = 1020;
    private static final int PAUSE = 1060, RESUME = 1070, DOCK = 1050;
    private static final String[] DISCOVERY = {"n1_find", "veniibot_find"};

    private VeniiClient() {}

    private static final class Discovery {
        final String auth, mac;
        Discovery(String auth, String mac) { this.auth = auth; this.mac = mac; }
    }

    /** Credentials exist in-memory only; no logs, on-disk storage or UI exposure. */
    private static Discovery discover() throws Exception {
        InetAddress target = InetAddress.getByName(HOST);
        DatagramSocket udp;
        try { udp = new DatagramSocket(PORT); }
        catch (IOException busy) { udp = new DatagramSocket(); }
        try {
            udp.setSoTimeout(500);
            for (String probe : DISCOVERY) {
                byte[] data = probe.getBytes(StandardCharsets.UTF_8);
                udp.send(new DatagramPacket(data, data.length, target, PORT));
            }
            byte[] buff = new byte[4096];
            long until = System.nanoTime() + 8_000_000_000L;
            while (System.nanoTime() < until) {
                DatagramPacket rx = new DatagramPacket(buff, buff.length);
                try { udp.receive(rx); }
                catch (SocketTimeoutException timeout) { continue; }
                if (!target.equals(rx.getAddress())) continue;
                try {
                    JSONObject reply = new JSONObject(new String(rx.getData(), rx.getOffset(), rx.getLength(), StandardCharsets.UTF_8));
                    String auth = reply.optString("auth", "");
                    String mac = reply.optString("mac", "");
                    if (!auth.isEmpty() && !mac.isEmpty()) return new Discovery(auth, mac);
                } catch (Exception ignored) { /* Discovery packet not meant for us. */ }
            }
        } finally { udp.close(); }
        throw new IOException("Robot non détecté : connectez le téléphone au Wi-Fi VENII.");
    }

    private static void send(Socket socket, String json) throws Exception {
        socket.getOutputStream().write(VeniiFrameCodec.encode(json));
        socket.getOutputStream().flush();
    }

    private static Object receive(Socket socket, int timeoutMs) throws Exception {
        socket.setSoTimeout(timeoutMs);
        return new JSONTokener(VeniiFrameCodec.decodeFrame(socket.getInputStream())).nextValue();
    }

    private static Map<Integer, Object> extract(Object obj) {
        Map<Integer, Object> data = new HashMap<>();
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i = 0; i < array.length(); ++i) addIfCode(array.optJSONObject(i), data);
        } else if (obj instanceof JSONObject) addIfCode((JSONObject) obj, data);
        return data;
    }

    private static void addIfCode(JSONObject obj, Map<Integer, Object> data) {
        if (obj != null && obj.opt("id") instanceof Number) {
            int id = obj.optInt("id", -1);
            if (id >= 0) data.put(id, obj.opt("content"));
        }
    }

    private static String envelope(int code, String mac) throws Exception {
        JSONObject item = new JSONObject();
        item.put("from", "android");
        item.put("to_name", mac);
        item.put("from_name", "local");
        item.put("type", "cmd");
        item.put("id", code);
        JSONArray array = new JSONArray();
        array.put(item);
        return array.toString();
    }

    private static final class Session implements AutoCloseable {
        final Socket socket;
        final String mac;
        Session(Socket socket, String mac) { this.socket = socket; this.mac = mac; }
        @Override public void close() throws IOException { socket.close(); }
    }

    private static Session openSession() throws Exception { return openSession(null); }

    private static Session openSession(LidarReport report) throws Exception {
        Discovery discovery = discover();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(HOST, PORT), 5000);
            JSONObject authMessage = new JSONObject();
            authMessage.put("id", AUTH);
            authMessage.put("content", discovery.auth);
            send(socket, authMessage.toString());
            for (int i = 0; i < 3; i++) {
                Object reply = receive(socket, 8000);
                if (report != null) report.observe(reply);
                if (extract(reply).containsKey(AUTH)) return new Session(socket, discovery.mac);
            }
            throw new IOException("Aucune réponse d'authentification reconnue (67).");
        } catch (Exception err) {
            socket.close();
            throw err;
        }
    }

    private static Map<Integer, Object> requestStatus(Session session) throws Exception {
        send(session.socket, envelope(STATUS, session.mac));
        for (int i = 0; i < 3; i++) {
            Map<Integer, Object> items = extract(receive(session.socket, 9000));
            if (items.containsKey(STATUS)) return items;
        }
        throw new IOException("Aucune réponse d'état reconnue (888).");
    }

    /** Read-only diagnostic: discovery, auth 67 and known status 888, then listen. */
    public static LidarReport listenForLidar() {
        LidarReport report = new LidarReport();
        try (Session session = openSession(report)) {
            send(session.socket, envelope(STATUS, session.mac));
            long deadline = System.nanoTime() + 15_000_000_000L;
            java.io.InputStream input = new java.io.FilterInputStream(session.socket.getInputStream()) {
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new SocketTimeoutException();
                    session.socket.setSoTimeout((int)Math.max(1L, (remaining + 999999L) / 1000000L));
                    return super.read(b, off, len);
                }
            };
            for (int i = 0; i < 120; i++) {
                if (Thread.currentThread().isInterrupted()) { report.end = "Interrompu"; return report; }
                try {
                    Object reply = new JSONTokener(VeniiFrameCodec.decodeFrame(input, 1048576)).nextValue();
                    report.observe(reply);
                } catch (SocketTimeoutException timeout) {
                    report.end = "Fenêtre d’écoute de 15 s terminée (trame partielle éventuelle non comptée).";
                    return report;
                } catch (java.io.EOFException eof) {
                    report.end = "Connexion fermée par le robot.";
                    return report;
                }
            }
            report.end = "Limite de 120 trames atteinte.";
        } catch (Exception failure) {
            report.end = "Écoute interrompue : " + failure.getClass().getSimpleName() + " (connexion, format ou limite de taille).";
        }
        return report;
    }

    public static final class LidarReport {
        private final java.util.TreeMap<Integer, Integer> counts = new java.util.TreeMap<>();
        private final java.util.TreeMap<Integer, Integer> sizes = new java.util.TreeMap<>();
        private final java.util.TreeMap<Integer, String> payloads = new java.util.TreeMap<>();
        private int frames, items, omitted;
        private String end = "En cours";
        private static boolean candidate(int id) {
            return id == 1219 || id == 1220 || id == 800062 || id == 800063 || id == 800064 || id == 800065;
        }
        private static boolean mapPayload(int id) { return id == 1219 || id == 1220 || id == 800064; }
        private void observe(Object value) {
            frames++;
            if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray)value;
                for (int i=0; i<arr.length(); i++) observeItem(arr.optJSONObject(i));
            } else if (value instanceof JSONObject) observeItem((JSONObject)value);
        }
        private void observeItem(JSONObject value) {
            if (value == null || !(value.opt("id") instanceof Number)) return;
            int id = value.optInt("id", -1);
            if (id < 0 || id == AUTH) return; // Never retain auth payload.
            items++;
            if (!counts.containsKey(id) && counts.size() >= 256) { omitted++; return; }
            counts.put(id, counts.getOrDefault(id,0)+1);
            if (!candidate(id)) return;
            Object content = value.opt("content");
            String raw = content == null ? "" : String.valueOf(content);
            sizes.put(id, raw.getBytes(StandardCharsets.UTF_8).length);
            if (mapPayload(id) && raw.length() > 0 && raw.length() <= 500000) payloads.put(id, raw);
        }
        public boolean hasPayload() { return !payloads.isEmpty(); }
        public String rawPayloads() {
            StringBuilder b = new StringBuilder("KOOMA-I • données candidates non décodées\n");
            for (Map.Entry<Integer,String> entry : payloads.entrySet())
                b.append("Code ").append(entry.getKey()).append("\n").append(entry.getValue()).append("\n\n");
            return b.toString();
        }
        public String summary() {
            StringBuilder b = new StringBuilder();
            b.append(end).append("\nTrames JSON : ").append(frames).append(" • éléments hors auth : ").append(items).append("\n");
            b.append("État 888 reçu : ").append(counts.containsKey(STATUS) ? "oui" : "non").append("\n");
            boolean found = false;
            for (int id : new int[]{1219,1220,800062,800063,800064,800065}) {
                int count = counts.getOrDefault(id,0);
                found |= count > 0;
                b.append("Code ").append(id).append(" : ").append(count);
                if (sizes.containsKey(id)) b.append(" • dernier contenu : ").append(sizes.get(id)).append(" octets");
                b.append("\n");
            }
            b.append(found ? "Codes candidats reçus, interprétation à vérifier." : "Aucun code candidat reçu pendant cet essai. Cela ne prouve pas leur indisponibilité.").append("\n");
            b.append("Autres codes : ");
            for (Map.Entry<Integer,Integer> entry : counts.entrySet())
                if (!candidate(entry.getKey())) b.append(entry.getKey()).append("×").append(entry.getValue()).append(" ");
            if (omitted>0) b.append(" • éléments non listés : ").append(omitted);
            b.append("\nContenus carte conservés en mémoire : ").append(payloads.size()).append(" (dernière valeur bornée par code).");
            return b.toString();
        }
    }

    public static Status readStatus() throws Exception {
        try (Session session = openSession()) {
            return new Status(requestStatus(session));
        }
    }

    /** Caller must require physical checks and user confirmation BEFORE invoking. */
    public static StartResult startOnce() throws Exception {
        return commandOnce(START);
    }

    /** Send exactly one movement command after a status check; no auto-retries. */
    public static StartResult commandOnce(int code) throws Exception {
        if (code != START && code != PAUSE && code != RESUME && code != DOCK)
            throw new IllegalArgumentException("Commande non autorisée.");
        try (Session session = openSession()) {
            Status before = new Status(requestStatus(session));
            send(session.socket, envelope(code, session.mac));
            // The device may act without a separate ACK. Never claim physical success from send alone.
            Status after = null;
            try {
                Thread.sleep(1600);
                after = new Status(requestStatus(session));
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) { /* command may close socket or produce no subsequent state */ }
            return new StartResult(before, after);
        }
    }

    public static final class StartResult {
        public final Status before, after;
        StartResult(Status before, Status after) { this.before = before; this.after = after; }
    }

    public static final class Status {
        public final String generalState, powerReport, suction, volume, error;
        public final int messageCount;
        Status(Map<Integer, Object> payload) {
            messageCount = payload.size();
            generalState = format(payload.get(888));
            powerReport = format(payload.get(60));
            suction = format(payload.get(1300));
            volume = format(payload.get(1502));
            error = format(payload.get(70));
        }
        private static String format(Object value) {
            if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
            return "—"; // Deliberately never expose raw auth/MAC/other payloads.
        }
    }
}
