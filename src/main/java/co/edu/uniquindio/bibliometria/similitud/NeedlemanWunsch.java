package co.edu.uniquindio.bibliometria.similitud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Alineamiento global de Needleman-Wunsch entre las secuencias de términos de dos abstracts (tras
 * el preprocesamiento). Programación dinámica con puntaje +1 por coincidencia, -1 por sustitución y
 * -1 por hueco:
 *   F[i][0] = -i,  F[0][j] = -j
 *   F[i][j] = max(F[i-1][j-1] + s(a_i, b_j), F[i-1][j] - 1, F[i][j-1] - 1)
 * El alineamiento óptimo se reconstruye recorriendo la matriz desde F[m][n] hacia F[0][0].
 * Tiempo y espacio O(m·n). Similitud = porcentaje de identidad = coincidencias / longitud del alineamiento.
 */
public final class NeedlemanWunsch implements AlgoritmoSimilitud {

    public static final int COINCIDENCIA = 1;
    public static final int SUSTITUCION = -1;
    public static final int HUECO = -1;
    public static final String SIMBOLO_HUECO = "-";

    /** Resultado del alineamiento: las dos secuencias alineadas (con huecos) y el puntaje óptimo. */
    public record Alineamiento(List<String> a, List<String> b, int puntaje, int coincidencias) {
        public double identidad() {
            return a.isEmpty() ? 1.0 : (double) coincidencias / a.size();
        }
    }

    @Override
    public String nombre() {
        return "Needleman-Wunsch";
    }

    @Override
    public String enfoque() {
        return "Clásico";
    }

    @Override
    public double similitud(CorpusProcesado corpus, int i, int j) {
        return alinear(corpus.tokens(i), corpus.tokens(j)).identidad();
    }

    public static int[][] matriz(List<String> a, List<String> b) {
        int[][] f = new int[a.size() + 1][b.size() + 1];
        for (int i = 0; i <= a.size(); i++) {
            f[i][0] = i * HUECO;
        }
        for (int j = 0; j <= b.size(); j++) {
            f[0][j] = j * HUECO;
        }
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                int diagonal = f[i - 1][j - 1] + puntaje(a.get(i - 1), b.get(j - 1));
                int arriba = f[i - 1][j] + HUECO;
                int izquierda = f[i][j - 1] + HUECO;
                f[i][j] = Math.max(diagonal, Math.max(arriba, izquierda));
            }
        }
        return f;
    }

    public static Alineamiento alinear(List<String> a, List<String> b) {
        int[][] f = matriz(a, b);
        List<String> alineadaA = new ArrayList<>();
        List<String> alineadaB = new ArrayList<>();
        int coincidencias = 0;
        int i = a.size();
        int j = b.size();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && f[i][j] == f[i - 1][j - 1] + puntaje(a.get(i - 1), b.get(j - 1))) {
                alineadaA.add(a.get(i - 1));
                alineadaB.add(b.get(j - 1));
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    coincidencias++;
                }
                i--;
                j--;
            } else if (i > 0 && f[i][j] == f[i - 1][j] + HUECO) {
                alineadaA.add(a.get(i - 1));
                alineadaB.add(SIMBOLO_HUECO);
                i--;
            } else {
                alineadaA.add(SIMBOLO_HUECO);
                alineadaB.add(b.get(j - 1));
                j--;
            }
        }
        Collections.reverse(alineadaA);
        Collections.reverse(alineadaB);
        return new Alineamiento(alineadaA, alineadaB, f[a.size()][b.size()], coincidencias);
    }

    /**
     * Camino óptimo de F[0][0] a F[m][n], con la misma preferencia que alinear ante un empate:
     * diagonal, después arriba (hueco en b) y por último izquierda (hueco en a).
     */
    public static List<int[]> camino(List<String> a, List<String> b, int[][] f) {
        List<int[]> camino = new ArrayList<>();
        int i = a.size();
        int j = b.size();
        camino.add(new int[] {i, j});
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && f[i][j] == f[i - 1][j - 1] + puntaje(a.get(i - 1), b.get(j - 1))) {
                i--;
                j--;
            } else if (i > 0 && f[i][j] == f[i - 1][j] + HUECO) {
                i--;
            } else {
                j--;
            }
            camino.add(new int[] {i, j});
        }
        Collections.reverse(camino);
        return camino;
    }

    private static int puntaje(String x, String y) {
        return x.equals(y) ? COINCIDENCIA : SUSTITUCION;
    }
}
