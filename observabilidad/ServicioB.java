/*
 * servicio-notificaciones (B) — consume el evento que le manda A. Lee el traceId de la cabecera
 * X-Trace-Id (la MISMA traza que generó A) y lo usa en sus logs. Regla de juguete: los id que
 * terminan en "3" fallan (la "base" los rechaza) -> ERROR + 500. Java puro, sin Spring.
 */
import com.sun.net.httpserver.*;
import java.io.*; import java.net.*;
import java.nio.charset.StandardCharsets; import java.util.*;

public class ServicioB {
    static final String SERVICIO = "servicio-notificaciones";
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8080"));
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/eventos", ex -> {
            String traceId = ex.getRequestHeaders().getFirst("X-Trace-Id");   // <-- la MISMA traza de A
            if (traceId == null) traceId = "t-SINTRAZA";
            String id = params(ex).getOrDefault("id", "CAM-000");
            log("INFO", traceId, "consume evento de " + id);
            if (id.endsWith("3")) { log("ERROR", traceId, "la base rechazo el aviso de " + id + " (timeout)"); responder(ex, 500, "error"); }
            else { log("INFO", traceId, "aviso creado para " + id); responder(ex, 200, "ok"); }
        });
        s.createContext("/health", ex -> responder(ex, 200, "{\"status\":\"UP\"}"));
        s.start();
        System.out.println("{\"servicio\":\"" + SERVICIO + "\",\"msg\":\"arriba en :" + port + "\"}");
    }
    static void log(String nivel, String traceId, String msg) {
        System.out.println(String.format("{\"nivel\":\"%s\",\"servicio\":\"%s\",\"traceId\":\"%s\",\"msg\":\"%s\"}", nivel, SERVICIO, traceId, msg));
    }
    static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }
    static Map<String,String> params(HttpExchange ex) { Map<String,String> m = new HashMap<>(); String q = ex.getRequestURI().getRawQuery(); if (q != null) for (String p : q.split("&")) { String[] kv = p.split("=", 2); if (kv.length == 2) m.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8)); } return m; }
    static void responder(HttpExchange ex, int code, String body) throws IOException { byte[] b = body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().add("Content-Type", "application/json"); ex.sendResponseHeaders(code, b.length); try (OutputStream os = ex.getResponseBody()) { os.write(b); } }
}
