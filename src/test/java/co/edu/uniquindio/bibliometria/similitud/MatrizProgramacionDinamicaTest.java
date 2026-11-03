package co.edu.uniquindio.bibliometria.similitud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * La matriz y el camino óptimo que muestra el paso a paso de la pestaña de similitud: la última celda
 * debe dar el mismo resultado que cada algoritmo y el camino debe ir de (0,0) a (m,n) sumando, paso a
 * paso, exactamente ese resultado.
 */
class MatrizProgramacionDinamicaTest {

    @Test
    void levenshteinCasoConocido() {
        int[][] d = Levenshtein.matriz("kitten", "sitting");
        assertEquals(3, d[6][7]);
        assertEquals(Levenshtein.distancia("kitten", "sitting"), d[6][7]);
        verificarCaminoLevenshtein("kitten", "sitting", d);
    }

    @Test
    void levenshteinEnTextosAleatorios() {
        Random azar = new Random(3);
        for (int prueba = 0; prueba < 200; prueba++) {
            String a = aleatorio(azar, azar.nextInt(21), 3);
            String b = aleatorio(azar, azar.nextInt(21), 3);
            int[][] d = Levenshtein.matriz(a, b);
            assertEquals(Levenshtein.distancia(a, b), d[a.length()][b.length()]);
            verificarCaminoLevenshtein(a, b, d);
        }
    }

    @Test
    void needlemanWunschEnSecuenciasAleatorias() {
        Random azar = new Random(9);
        String[] vocabulario = {"ai", "education", "learning", "assessment", "review", "students"};
        for (int prueba = 0; prueba < 200; prueba++) {
            List<String> a = terminos(azar, vocabulario, azar.nextInt(21));
            List<String> b = terminos(azar, vocabulario, azar.nextInt(21));
            int[][] f = NeedlemanWunsch.matriz(a, b);
            NeedlemanWunsch.Alineamiento alineamiento = NeedlemanWunsch.alinear(a, b);
            assertEquals(alineamiento.puntaje(), f[a.size()][b.size()]);

            List<int[]> camino = NeedlemanWunsch.camino(a, b, f);
            verificarExtremos(camino, a.size(), b.size());
            // El camino es el mismo alineamiento que reconstruye alinear: un paso por columna alineada.
            assertEquals(alineamiento.a().size(), camino.size() - 1);
            int suma = 0;
            for (int k = 1; k < camino.size(); k++) {
                int[] antes = camino.get(k - 1);
                int[] despues = camino.get(k);
                boolean diagonal = despues[0] == antes[0] + 1 && despues[1] == antes[1] + 1;
                suma += diagonal
                        ? (a.get(antes[0]).equals(b.get(antes[1])) ? NeedlemanWunsch.COINCIDENCIA : NeedlemanWunsch.SUSTITUCION)
                        : NeedlemanWunsch.HUECO;
            }
            assertEquals(f[a.size()][b.size()], suma);
        }
    }

    /** Extremos, pasos válidos y costo total del camino igual a la distancia. */
    private static void verificarCaminoLevenshtein(String a, String b, int[][] d) {
        List<int[]> camino = Levenshtein.camino(a, b, d);
        verificarExtremos(camino, a.length(), b.length());
        int costo = 0;
        for (int k = 1; k < camino.size(); k++) {
            int[] antes = camino.get(k - 1);
            int[] despues = camino.get(k);
            boolean diagonal = despues[0] == antes[0] + 1 && despues[1] == antes[1] + 1;
            costo += diagonal ? (a.charAt(antes[0]) == b.charAt(antes[1]) ? 0 : 1) : 1;
        }
        assertEquals(d[a.length()][b.length()], costo);
    }

    private static void verificarExtremos(List<int[]> camino, int m, int n) {
        assertArrayEquals(new int[] {0, 0}, camino.get(0));
        assertArrayEquals(new int[] {m, n}, camino.get(camino.size() - 1));
        for (int k = 1; k < camino.size(); k++) {
            int di = camino.get(k)[0] - camino.get(k - 1)[0];
            int dj = camino.get(k)[1] - camino.get(k - 1)[1];
            assertTrue((di == 1 || di == 0) && (dj == 1 || dj == 0) && di + dj > 0, "paso no válido en el camino");
        }
    }

    private static String aleatorio(Random azar, int largo, int letras) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) {
            sb.append((char) ('a' + azar.nextInt(letras)));
        }
        return sb.toString();
    }

    private static List<String> terminos(Random azar, String[] vocabulario, int cantidad) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            l.add(vocabulario[azar.nextInt(vocabulario.length)]);
        }
        return l;
    }
}
