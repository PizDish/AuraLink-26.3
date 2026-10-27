package dev.auralink.client.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Localhost-only browser companion; never handles Yandex accounts or tokens. */
public final class BridgeServer {
    public static final int PORT = 27363;
    public record RemoteState(String title, String artist, String album, boolean playing,
                              int position, int duration, long updated) {
        public static RemoteState empty() { return new RemoteState("", "", "", false, 0, 0, 0); }
    }
    private record Command(long id, String action) { }
    private final String secret;
    private final ConcurrentLinkedQueue<Command> outgoing = new ConcurrentLinkedQueue<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicReference<RemoteState> state = new AtomicReference<>(RemoteState.empty());
    private HttpServer server;
    private String startupError = "";

    public BridgeServer(String secret) { this.secret = secret; }
    public RemoteState state() { return state.get(); }
    public String startupError() { return startupError; }
    public boolean connected() { return System.currentTimeMillis() - state.get().updated() < 8000; }

    public boolean send(String action) {
        if (!connected()) return false;
        if (!switch (action) {
            case "play_pause", "next", "previous", "like", "shuffle", "repeat" -> true;
            default -> false;
        }) return false;
        outgoing.add(new Command(ids.incrementAndGet(), action));
        return true;
    }
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/v1/", this::handle);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "AuraLink-local-bridge");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
        } catch (IOException ex) { startupError = ex.getClass().getSimpleName() + ": " + ex.getMessage(); }
    }
    public void close() { if (server != null) server.stop(0); }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String origin = ex.getRequestHeaders().getFirst("Origin");
            if (origin != null && !(origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://"))) {
                respond(ex, 403, "{\"error\":\"extension origin required\"}"); return;
            }
            if (origin != null) {
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
                ex.getResponseHeaders().set("Vary", "Origin");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "X-AuraLink-Key, Content-Type");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            }
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            if (!secret.equals(ex.getRequestHeaders().getFirst("X-AuraLink-Key"))) {
                respond(ex, 401, "{\"error\":\"Pair the browser extension\"}"); return;
            }
            String path = ex.getRequestURI().getPath();
            if (path.equals("/v1/poll") && ex.getRequestMethod().equals("GET")) {
                Command cmd = outgoing.poll();
                JsonObject json = new JsonObject();
                json.addProperty("id", cmd == null ? 0 : cmd.id());
                json.addProperty("action", cmd == null ? "" : cmd.action());
                respond(ex, 200, json.toString());
            } else if (path.equals("/v1/state") && ex.getRequestMethod().equals("POST")) {
                String raw = new String(ex.getRequestBody().readNBytes(8193), StandardCharsets.UTF_8);
                if (raw.length() > 8192) { respond(ex, 413, "{}"); return; }
                JsonObject j = JsonParser.parseString(raw).getAsJsonObject();
                state.set(new RemoteState(str(j,"title"),str(j,"artist"),str(j,"album"),bool(j,"playing"),
                    number(j,"position"),number(j,"duration"),System.currentTimeMillis()));
                respond(ex, 200, "{\"ok\":true}");
            } else { respond(ex, 404, "{\"error\":\"unknown route\"}"); }
        } catch (RuntimeException err) { respond(ex, 400, "{\"error\":\"invalid request\"}"); }
        finally { ex.close(); }
    }
    private static String str(JsonObject j, String key) {
        String value = !j.has(key) || j.get(key).isJsonNull() ? "" : j.get(key).getAsString();
        return value.substring(0, Math.min(250, value.length()));
    }
    private static boolean bool(JsonObject j, String key) { return j.has(key) && j.get(key).getAsBoolean(); }
    private static int number(JsonObject j, String key) { return j.has(key) ? Math.clamp(j.get(key).getAsInt(),0,86_400) : 0; }
    private static void respond(HttpExchange ex, int status, String data) throws IOException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
