package co.edu.uniquindio.bibliometria.busqueda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rabin-Karp para varios patrones a la vez. Los patrones se agrupan por longitud y, para cada
 * longitud m, el texto se recorre una sola vez con el hash rodante de RabinKarp: el hash de cada
 * ventana se calcula una vez y se busca en una tabla hash propia (hash -> patrones de longitud m con
 * ese hash), así que se compara con todos los patrones de esa longitud en la misma pasada. Cuando el
 * hash coincide se verifica carácter por carácter, porque puede ser una colisión.
 * Con k patrones de L longitudes distintas: preprocesamiento O(Σ m) y búsqueda O(L·n) esperada,
 * frente a O(k·n) si se ejecuta un algoritmo de un solo patrón k veces. Peor caso O(n·Σ m), si
 * todas las ventanas colisionan con todos los patrones.
 */
public final class RabinKarpMultiple {

    /**
     * Resultado de la búsqueda: posiciones (base 0) de cada patrón distinto, en el orden en que se
     * dieron; comparaciones de caracteres al verificar; ventanas examinadas (una por posición y por
     * longitud distinta); pasadas sobre el texto (longitudes distintas que caben en él); hash
     * iguales que no eran el patrón; y tiempos en nanosegundos.
     */
    public record Resultado(Map<String, List<Integer>> posiciones, long comparaciones, long ventanas, int pasadas,
            long colisiones, long nanosPreproceso, long nanosBusqueda) {
    }

    /**
     * Tabla hash de direccionamiento abierto (sondeo lineal) de hash -> patrones con ese hash. Guarda
     * las claves en un arreglo de long para no crear un objeto Long por cada ventana consultada. Casi
     * todas las ventanas no son ningún patrón, así que la tabla se deja muy dispersa (al menos 16
     * casillas por patrón): la casilla consultada casi siempre está vacía y la consulta termina con un
     * solo acceso, sin el costo de un salto mal predicho por el procesador.
     */
    private static final class TablaHash {
        private final long[] claves;
        private final boolean[] ocupada;
        private final List<List<String>> valores;
        private final int mascara;

        TablaHash(int elementos) {
            int capacidad = 64;
            while (capacidad < 16 * elementos) {
                capacidad *= 2;
            }
            claves = new long[capacidad];
            ocupada = new boolean[capacidad];
            valores = new ArrayList<>();
            for (int i = 0; i < capacidad; i++) {
                valores.add(null);
            }
            mascara = capacidad - 1;
        }

        private int casilla(long clave) {
            int i = (int) (clave ^ (clave >>> 32)) & mascara;
            while (ocupada[i] && claves[i] != clave) {
                i = (i + 1) & mascara;
            }
            return i;
        }

        void agregar(long clave, String patron) {
            int i = casilla(clave);
            if (!ocupada[i]) {
                ocupada[i] = true;
                claves[i] = clave;
                valores.set(i, new ArrayList<>());
            }
            valores.get(i).add(patron);
        }

        /** Patrones cuyo hash es la clave, o null si no hay ninguno. */
        List<String> obtener(long clave) {
            int i = casilla(clave);
            return ocupada[i] ? valores.get(i) : null;
        }
    }

    private final RabinKarp rabinKarp;

    public RabinKarpMultiple() {
        this(RabinKarp.PRIMO);
    }

    /** Con un primo pequeño (por ejemplo 13) las colisiones son frecuentes: útil para las pruebas. */
    public RabinKarpMultiple(long primo) {
        this.rabinKarp = new RabinKarp(primo);
    }

    public String nombre() {
        return "Rabin-Karp multipatrón";
    }

    public Resultado buscar(String texto, List<String> patrones) {
        long inicio = System.nanoTime();
        // Preprocesamiento: patrones distintos agrupados por longitud y, por longitud, la tabla hash -> patrones.
        Map<String, List<Integer>> posiciones = new LinkedHashMap<>();
        Map<Integer, List<String>> grupos = new LinkedHashMap<>();
        for (String patron : patrones) {
            if (posiciones.containsKey(patron)) {
                continue; // patrón repetido: se busca una sola vez
            }
            posiciones.put(patron, new ArrayList<>());
            if (!patron.isEmpty()) {
                grupos.computeIfAbsent(patron.length(), l -> new ArrayList<>()).add(patron);
            }
        }
        Map<Integer, TablaHash> porLongitud = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> grupo : grupos.entrySet()) {
            TablaHash tabla = new TablaHash(grupo.getValue().size());
            for (String patron : grupo.getValue()) {
                tabla.agregar(rabinKarp.hash(patron, grupo.getKey()), patron);
            }
            porLongitud.put(grupo.getKey(), tabla);
        }
        long finPreproceso = System.nanoTime();

        int n = texto.length();
        long comparaciones = 0;
        long ventanas = 0;
        long colisiones = 0;
        int pasadas = 0;
        for (Map.Entry<Integer, TablaHash> grupo : porLongitud.entrySet()) {
            int m = grupo.getKey();
            if (m > n) {
                continue;
            }
            pasadas++;
            TablaHash tabla = grupo.getValue();
            long peso = rabinKarp.pesoMayor(m);
            long hashVentana = rabinKarp.hash(texto, m);
            for (int s = 0; s <= n - m; s++) {
                ventanas++;
                List<String> candidatos = tabla.obtener(hashVentana);
                if (candidatos != null) {
                    for (String patron : candidatos) {
                        int j = 0;
                        while (j < m) {
                            comparaciones++;
                            if (texto.charAt(s + j) != patron.charAt(j)) {
                                break;
                            }
                            j++;
                        }
                        if (j == m) {
                            posiciones.get(patron).add(s);
                        } else {
                            colisiones++;
                        }
                    }
                }
                if (s < n - m) {
                    hashVentana = rabinKarp.rodar(hashVentana, texto.charAt(s), texto.charAt(s + m), peso);
                }
            }
        }
        long fin = System.nanoTime();
        return new Resultado(posiciones, comparaciones, ventanas, pasadas, colisiones, finPreproceso - inicio,
                fin - finPreproceso);
    }
}
