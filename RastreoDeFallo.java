/*
 * RASTREO DE UN FALLO entre servicios, SIN entrar a ningun servidor.
 * Todos los servicios escriben a un LOG CENTRAL (estructurado: servicio, traceId, nivel, msg).
 * Varias peticiones corren entrelazadas; UNA falla a mitad, en servicio-b.
 * Desde el log central, filtrando por el traceId de esa peticion, se reconstruye su
 * recorrido y se ve EXACTAMENTE donde y por que fallo.
 */
import java.util.*;

public class RastreoDeFallo {
    static final List<Log> central = new ArrayList<>();   // el agregador central (Loki, en la vida real)

    public static void main(String[] args) {
        atender("t-AAA", "CAM-002", false);   // peticion sana
        atender("t-BBB", "INC-003", true);    // peticion que FALLA en servicio-b
        atender("t-CCC", "CAM-004", false);   // otra sana

        System.out.println("== LOG CENTRAL (todo mezclado, 3 peticiones) ==");
        central.forEach(l -> System.out.println("  " + l));

        // Un usuario reporta: "la solicitud INC-003 no generó aviso". Buscamos su traza.
        System.out.println("\n== RASTREO: seguir la peticion de INC-003 por su traceId (t-BBB) ==");
        List<Log> traza = central.stream().filter(l -> l.traceId.equals("t-BBB")).toList();
        traza.forEach(l -> System.out.println("  " + l));

        // Diagnostico automatico: donde fallo
        Log fallo = traza.stream().filter(l -> l.nivel.equals("ERROR")).findFirst().orElse(null);
        System.out.println("\n== DIAGNOSTICO (sin entrar a ningun servidor) ==");
        System.out.println("  el fallo ocurrio en: " + fallo.servicio + "  ->  " + fallo.mensaje);

        assert fallo != null && fallo.servicio.equals("servicio-b") : "no se localizo el fallo";
        assert traza.size() == 3 : "la traza esta incompleta";
        System.out.println("  Traza reconstruida de extremo a extremo; causa localizada. Eso es observabilidad.");
    }

    static void atender(String traceId, String solicitud, boolean fallaEnB) {
        log("servicio-a", traceId, "INFO", "recibe enviar(" + solicitud + ")");
        log("servicio-a", traceId, "INFO", "publica evento -> servicio-b");
        if (fallaEnB)
            log("servicio-b", traceId, "ERROR", "consume evento de " + solicitud + ": la base rechazo el aviso");
        else
            log("servicio-b", traceId, "INFO", "consume evento de " + solicitud + ": aviso creado");
    }

    static void log(String servicio, String traceId, String nivel, String msg) {
        central.add(new Log(servicio, traceId, nivel, msg));
    }

    record Log(String servicio, String traceId, String nivel, String mensaje) {
        public String toString() {
            return "{servicio=" + servicio + ", traceId=" + traceId + ", nivel=" + nivel + ", msg=\"" + mensaje + "\"}";
        }
    }
}
