package co.edu.uniquindio.bibliometria.ranking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Construye la lista de frecuencias del corpus. Se usa {@link LinkedHashMap} para que la
 * lista quede en orden de primera aparición: así la entrada a los tres algoritmos es
 * determinista y, como los tres son estables, los empates se resuelven igual en todos.
 */
public final class ContadorFrecuencias {

    private ContadorFrecuencias() {
    }

    public static Termino[] contar(List<List<String>> tokensPorDocumento) {
        Map<String, Integer> frecuencias = new LinkedHashMap<>();
        for (List<String> tokens : tokensPorDocumento) {
            for (String token : tokens) {
                frecuencias.merge(token, 1, Integer::sum);
            }
        }
        return frecuencias.entrySet().stream()
                .map(e -> new Termino(e.getKey(), e.getValue()))
                .toArray(Termino[]::new);
    }
}
