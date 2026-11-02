package co.edu.uniquindio.bibliometria.similitud;

import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Distancia de edición de Levenshtein entre los abstracts normalizados, carácter por carácter.
 * Programación dinámica: D[i][j] es el mínimo número de inserciones, eliminaciones y sustituciones
 * para convertir los primeros i caracteres de a en los primeros j de b.
 *   D[i][0] = i,  D[0][j] = j
 *   D[i][j] = min(D[i-1][j] + 1, D[i][j-1] + 1, D[i-1][j-1] + (a[i] == b[j] ? 0 : 1))
 * Tiempo O(m·n). Para comparar abstracts solo se guardan dos filas (espacio O(n)); la matriz
 * completa se construye únicamente para la demostración paso a paso con textos cortos.
 * Similitud = 1 - D[m][n] / max(m, n).
 */
public final class Levenshtein implements AlgoritmoSimilitud {

    @Override
    public String nombre() {
        return "Levenshtein";
    }

    @Override
    public String enfoque() {
        return "Clásico";
    }

    @Override
    public double similitud(CorpusProcesado corpus, int i, int j) {
        return similitud(corpus.textoNormalizado(i), corpus.textoNormalizado(j));
    }

    public static double similitud(String a, String b) {
        int mayor = Math.max(a.length(), b.length());
        return mayor == 0 ? 1.0 : 1.0 - (double) distancia(a, b) / mayor;
    }

    public static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int sustitucion = anterior[j - 1] + (ca == b.charAt(j - 1) ? 0 : 1);
                int eliminacion = anterior[j] + 1;
                int insercion = actual[j - 1] + 1;
                actual[j] = Math.min(sustitucion, Math.min(eliminacion, insercion));
            }
            int[] temporal = anterior;
            anterior = actual;
            actual = temporal;
        }
        return anterior[b.length()];
    }

    /** Matriz completa (m+1) x (n+1), para la demostración paso a paso. */
    public static int[][] matriz(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(d[i - 1][j - 1] + costo, Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1));
            }
        }
        return d;
    }
}
