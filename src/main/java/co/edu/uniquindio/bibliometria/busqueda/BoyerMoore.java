package co.edu.uniquindio.bibliometria.busqueda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boyer-Moore con las dos heurísticas. El patrón se compara de derecha a izquierda y, ante una
 * discrepancia, se desplaza lo máximo que permitan:
 * - regla del carácter malo: alinear la última aparición en el patrón del carácter del texto que
 *   falló (o saltar el patrón completo si no aparece). Tabla ultima[c], O(m + σ).
 * - regla del sufijo bueno: alinear otra aparición del sufijo que ya coincidió, o el prefijo más largo
 *   del patrón que sea sufijo de esa parte. Tabla desplazamiento[j], O(m).
 * Peor caso O(n·m) (O(n + m) con la variante de Galil), pero en texto natural suele examinar mucho
 * menos de n caracteres: el mejor caso es O(n / m).
 */
public final class BoyerMoore implements AlgoritmoBusqueda {

    private static final int ASCII = 256;

    /** Última posición de cada carácter en el patrón (-1 si no aparece). */
    public static final class TablaCaracterMalo {
        private final int[] ascii = new int[ASCII];
        private final Map<Character, Integer> otros = new HashMap<>();

        TablaCaracterMalo(String patron) {
            for (int c = 0; c < ASCII; c++) {
                ascii[c] = -1;
            }
            for (int j = 0; j < patron.length(); j++) {
                char c = patron.charAt(j);
                if (c < ASCII) {
                    ascii[c] = j;
                } else {
                    otros.put(c, j);
                }
            }
        }

        public int ultima(char c) {
            return c < ASCII ? ascii[c] : otros.getOrDefault(c, -1);
        }
    }

    @Override
    public String nombre() {
        return "Boyer-Moore";
    }

    public static TablaCaracterMalo tablaCaracterMalo(String patron) {
        return new TablaCaracterMalo(patron);
    }

    /**
     * desplazamiento[j] = cuánto mover el patrón cuando P[j..m-1] ya coincidió y falló P[j-1]
     * (desplazamiento[0] se usa tras una ocurrencia completa). Se calcula con los bordes de los sufijos.
     */
    public static int[] tablaSufijoBueno(String patron) {
        int m = patron.length();
        int[] desplazamiento = new int[m + 1];
        int[] borde = new int[m + 1];
        // Caso 1: el sufijo bueno aparece en otra parte del patrón.
        int i = m;
        int j = m + 1;
        borde[i] = j;
        while (i > 0) {
            while (j <= m && patron.charAt(i - 1) != patron.charAt(j - 1)) {
                if (desplazamiento[j] == 0) {
                    desplazamiento[j] = j - i;
                }
                j = borde[j];
            }
            i--;
            j--;
            borde[i] = j;
        }
        // Caso 2: solo una parte del sufijo bueno coincide con un prefijo del patrón.
        j = borde[0];
        for (i = 0; i <= m; i++) {
            if (desplazamiento[i] == 0) {
                desplazamiento[i] = j;
            }
            if (i == j) {
                j = borde[j];
            }
        }
        return desplazamiento;
    }

    @Override
    public Resultado buscar(String texto, String patron) {
        long inicio = System.nanoTime();
        int m = patron.length();
        TablaCaracterMalo malo = tablaCaracterMalo(patron);
        int[] sufijo = tablaSufijoBueno(patron);
        long finPreproceso = System.nanoTime();

        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        long ventanas = 0;
        int n = texto.length();
        int s = 0;
        while (m > 0 && s <= n - m) {
            ventanas++;
            int j = m - 1;
            while (j >= 0) {
                comparaciones++;
                if (patron.charAt(j) != texto.charAt(s + j)) {
                    break;
                }
                j--;
            }
            if (j < 0) {
                posiciones.add(s);
                s += sufijo[0];
            } else {
                s += Math.max(sufijo[j + 1], j - malo.ultima(texto.charAt(s + j)));
            }
        }
        long fin = System.nanoTime();
        return new Resultado(posiciones, comparaciones, ventanas, 0, finPreproceso - inicio, fin - finPreproceso);
    }
}
