package co.edu.uniquindio.bibliometria.busqueda;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class BusquedaTest {

    private static final List<AlgoritmoBusqueda> ALGORITMOS = List.of(new KnuthMorrisPratt(), new BoyerMoore(),
            new RabinKarp(), new RabinKarp(13));

    /** Referencia: compara el patrón en cada posición, sin ninguna optimización. */
    private static List<Integer> fuerzaBruta(String texto, String patron) {
        List<Integer> posiciones = new ArrayList<>();
        for (int s = 0; s + patron.length() <= texto.length(); s++) {
            int j = 0;
            while (j < patron.length() && texto.charAt(s + j) == patron.charAt(j)) {
                j++;
            }
            if (j == patron.length()) {
                posiciones.add(s);
            }
        }
        return posiciones;
    }

    @Test
    void funcionDePrefijosDeKmp() {
        assertArrayEquals(new int[] {0, 0, 1, 2, 3, 0, 1}, KnuthMorrisPratt.funcionPrefijos("ababaca"));
        assertArrayEquals(new int[] {0, 0, 1, 0, 0, 0, 0, 0}, KnuthMorrisPratt.funcionPrefijos("analysis"));
        assertArrayEquals(new int[] {0, 1, 2, 3, 0}, KnuthMorrisPratt.funcionPrefijos("aaaab"));
    }

    @Test
    void coincidenConFuerzaBrutaEnTextosAleatorios() {
        Random azar = new Random(7);
        for (int prueba = 0; prueba < 300; prueba++) {
            String texto = aleatorio(azar, 1 + azar.nextInt(400), 1 + azar.nextInt(3));
            String patron = aleatorio(azar, 1 + azar.nextInt(6), 1 + azar.nextInt(3));
            List<Integer> esperado = fuerzaBruta(texto, patron);
            for (AlgoritmoBusqueda a : ALGORITMOS) {
                assertEquals(esperado, a.buscar(texto, patron).posiciones(), a.nombre() + " con " + patron);
            }
        }
    }

    @Test
    void ocurrenciasSolapadasYPatronMasLargoQueElTexto() {
        for (AlgoritmoBusqueda a : ALGORITMOS) {
            assertEquals(List.of(0, 1, 2, 3), a.buscar("aaaaa", "aa").posiciones(), a.nombre());
            assertEquals(List.of(), a.buscar("ab", "abc").posiciones(), a.nombre());
            assertEquals(List.of(25), a.buscar("this study begins with a content analysis", "content analysis")
                    .posiciones(), a.nombre());
        }
    }

    @Test
    void textoOPatronVaciosYMayusculas() {
        for (AlgoritmoBusqueda a : ALGORITMOS) {
            assertEquals(List.of(), a.buscar("", "ai").posiciones(), a.nombre() + " con texto vacío");
            assertEquals(List.of(), a.buscar("learning", "").posiciones(), a.nombre() + " con patrón vacío");
            assertEquals(List.of(0), a.buscar("ai", "ai").posiciones(), a.nombre() + " con texto igual al patrón");
            assertEquals(List.of(), a.buscar("Learning", "learning").posiciones(), a.nombre() + " distingue mayúsculas");
            assertEquals(List.of(4), a.buscar("año educación", "educ").posiciones(), a.nombre() + " fuera de ASCII");
        }
        assertEquals("learning ai", BusquedaCorpus.minusculas("LEARNING Ai"));
    }

    @Test
    void rabinKarpConPrimoPequenoDetectaColisiones() {
        String texto = "this study begins with a content analysis that explores gai's transformative impact";
        AlgoritmoBusqueda.Resultado r = new RabinKarp(13).buscar(texto, "analysis");
        assertEquals(fuerzaBruta(texto, "analysis"), r.posiciones());
        assertEquals(r.ventanas(), texto.length() - "analysis".length() + 1);
    }

    private static String aleatorio(Random azar, int largo, int alfabeto) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) {
            sb.append((char) ('a' + azar.nextInt(alfabeto)));
        }
        return sb.toString();
    }
}
