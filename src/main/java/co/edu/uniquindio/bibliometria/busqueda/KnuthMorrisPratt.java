package co.edu.uniquindio.bibliometria.busqueda;

import java.util.ArrayList;
import java.util.List;

/**
 * Knuth-Morris-Pratt. El preprocesamiento calcula la función de prefijos π del patrón:
 * π[q] = longitud del prefijo propio más largo de P[0..q] que también es sufijo de P[0..q].
 * Durante la búsqueda, cuando hay una discrepancia después de q coincidencias, el patrón se desplaza
 * para que su prefijo de longitud π[q-1] quede alineado con el texto ya leído: el índice del texto
 * nunca retrocede. Tiempo: O(m) de preprocesamiento + O(n) de búsqueda (a lo sumo 2n comparaciones).
 */
public final class KnuthMorrisPratt implements AlgoritmoBusqueda {

    @Override
    public String nombre() {
        return "Knuth-Morris-Pratt";
    }

    public static int[] funcionPrefijos(String patron) {
        int m = patron.length();
        int[] pi = new int[m];
        int k = 0;
        for (int q = 1; q < m; q++) {
            while (k > 0 && patron.charAt(k) != patron.charAt(q)) {
                k = pi[k - 1];
            }
            if (patron.charAt(k) == patron.charAt(q)) {
                k++;
            }
            pi[q] = k;
        }
        return pi;
    }

    @Override
    public Resultado buscar(String texto, String patron) {
        long inicio = System.nanoTime();
        int[] pi = funcionPrefijos(patron);
        long finPreproceso = System.nanoTime();

        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        long ventanas = patron.isEmpty() ? 0 : 1;
        int n = texto.length();
        int m = patron.length();
        int q = 0; // caracteres del patrón que coinciden hasta ahora
        for (int i = 0; i < n && m > 0; i++) {
            while (q > 0 && texto.charAt(i) != patron.charAt(q)) {
                comparaciones++;
                q = pi[q - 1];
                ventanas++;
            }
            comparaciones++;
            if (texto.charAt(i) == patron.charAt(q)) {
                q++;
            } else if (q == 0) {
                ventanas++;
            }
            if (q == m) {
                posiciones.add(i - m + 1);
                q = pi[q - 1];
                ventanas++;
            }
        }
        long fin = System.nanoTime();
        return new Resultado(posiciones, comparaciones, ventanas, 0, finPreproceso - inicio, fin - finPreproceso);
    }
}
