/*
 * servicio-solicitudes (A) — recibe una solicitud, genera un traceId, y llama a notificaciones (B)
 * pasando el MISMO traceId en la cabecera X-Trace-Id. Loguea en JSON a stdout. Java puro, sin Spring.
 *   env: PORT (def 8080), SERVICIO_B_URL (def http://localhost:8081/eventos)
 */
import com.sun.net.httpserver.*;
import java.io.*; import java.net.*; import java.net.http.*;
import java.nio.charset.StandardCharsets; import java.util.*;

public class ServicioA {
    static final String SERVICIO = "servicio-solicitudes";
    static final String B = env("SERVICIO_B_URL", "http://localhost:8081/eventos");
    static final HttpClient http = HttpClient.newHttpClient();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8080"));
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/solicitudes", ex -> {
            String traceId = "t-" + UUID.randomUUID().toString().substring(0, 8);
            String id = params(ex).getOrDefault("id", "CAM-000");
            log("INFO", traceId, "recibe enviar(" + id + ")");
            int status;
            try {
                log("INFO", traceId, "publica evento -> notificaciones");
                HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create(B + "?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8)))
                        .header("X-Trace-Id", traceId).GET().build(),      // <-- la traza viaja en la cabecera
                    HttpResponse.BodyHandlers.ofString());
                status = r.statusCode();
                log(status >= 500 ? "ERROR" : "INFO", traceId, "notificaciones respondio " + status);
            } catch (Exception e) {
                status = 502; log("ERROR", traceId, "no se pudo contactar notificaciones: " + e.getMessage());
            }
            responder(ex, status >= 500 ? 500 : 200, "{\"traceId\":\"" + traceId + "\",\"status\":" + status + "}");
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
