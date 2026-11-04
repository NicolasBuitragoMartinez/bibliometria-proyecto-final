package co.edu.uniquindio.bibliometria.busqueda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import co.edu.uniquindio.bibliometria.modelo.Articulo;

/**
 * Rabin-Karp multipatrón debe encontrar, para cada patrón, las mismas posiciones que KMP y que la
 * fuerza bruta, con patrones de distinta longitud, repetidos y que no aparecen.
 */
class RabinKarpMultipleTest {

    private static final KnuthMorrisPratt KMP = new KnuthMorrisPratt();

    @Test
    void coincideConKmpYFuerzaBrutaEnTextosAleatorios() {
        Random azar = new Random(11);
        for (RabinKarpMultiple multiple : List.of(new RabinKarpMultiple(), new RabinKarpMultiple(13))) {
            for (int prueba = 0; prueba < 300; prueba++) {
                String texto = aleatorio(azar, 1 + azar.nextInt(400), 2 + azar.nextInt(3));
                List<String> patrones = new ArrayList<>();
                int k = 2 + azar.nextInt(9);
                for (int i = 0; i < k; i++) {
                    patrones.add(aleatorio(azar, 1 + azar.nextInt(7), 2 + azar.nextInt(3)));
                }
                patrones.add(patrones.get(0)); // repetido
                patrones.add("zzzz"); // no aparece: el alfabeto del texto llega hasta la «d»
                RabinKarpMultiple.Resultado r = multiple.buscar(texto, patrones);
                for (String patron : patrones) {
                    List<Integer> esperado = fuerzaBruta(texto, patron);
                    assertEquals(esperado, r.posiciones().get(patron), "fuerza bruta con " + patron);
                    assertEquals(KMP.buscar(texto, patron).posiciones(), r.posiciones().get(patron), "KMP con " + patron);
                }
                assertTrue(r.posiciones().get("zzzz").isEmpty());
            }
        }
    }

    @Test
    void unaPasadaPorLongitudDistinta() {
        String texto = "learning analytics and machine learning in assessment";
        RabinKarpMultiple.Resultado r = new RabinKarpMultiple()
                .buscar(texto, List.of("learning", "analysis", "assessment", "learning", "machine"));
        // Longitudes 8 (learning, analysis), 10 (assessment) y 7 (machine): tres pasadas.
        assertEquals(3, r.pasadas());
        assertEquals(4, r.posiciones().size(), "los patrones repetidos se buscan una sola vez");
        assertEquals(List.of(0, 31), r.posiciones().get("learning"));
        assertEquals(List.of(), r.posiciones().get("analysis"));
        assertEquals(List.of(43), r.posiciones().get("assessment"));
        long n = texto.length();
        assertEquals((n - 8 + 1) + (n - 10 + 1) + (n - 7 + 1), r.ventanas());
    }

    @Test
    void casosBorde() {
        RabinKarpMultiple multiple = new RabinKarpMultiple(13);
        RabinKarpMultiple.Resultado r = multiple.buscar("aaaaa", List.of("aa", "aaaaaa", "", "a"));
        assertEquals(List.of(0, 1, 2, 3), r.posiciones().get("aa"));
        assertEquals(List.of(), r.posiciones().get("aaaaaa"), "patrón más largo que el texto");
        assertEquals(List.of(), r.posiciones().get(""), "patrón vacío");
        assertEquals(List.of(0, 1, 2, 3, 4), r.posiciones().get("a"));
        assertEquals(2, r.pasadas(), "la longitud 6 no cabe en el texto y la vacía no se recorre");
        assertTrue(multiple.buscar("", List.of("ab", "c")).posiciones().values().stream().allMatch(List::isEmpty));
    }

    @Test
    void enElCorpusCoincideConLosAlgoritmosDeUnPatron() {
        List<Articulo> articulos = List.of(
                new Articulo(1, "a", "A", List.of("Ana Uno"), 2024, "Learning analytics support Learning and assessment."),
                new Articulo(2, "b", "B", List.of("Beto Dos"), 2023, "Generative AI changes assessment in higher education."));
        BusquedaCorpus.BusquedaMultiple r = BusquedaCorpus.buscarVarios(articulos,
                List.of("learning", "Assessment", "ASSESSMENT", "quantum", "analysis"), true);
        assertEquals(List.of("learning", "assessment", "quantum", "analysis"), r.patrones());
        assertTrue(r.coinciden().values().stream().allMatch(Boolean::booleanValue));
        Map<String, List<Integer>> primero = r.posicionesPorArticulo().get(0);
        assertEquals(List.of(0, 27), primero.get("learning"));
        assertEquals(List.of(), r.posicionesPorArticulo().get(1).get("quantum"));
        // Una pasada por longitud (8, 10 y 7) frente a una por patrón (4) en los demás.
        assertEquals(3, r.algoritmos().get(0).pasadas());
        assertEquals(4, r.algoritmos().get(1).pasadas());
        assertTrue(r.algoritmos().get(0).ventanas() <= r.algoritmos().get(3).ventanas());
    }

    private static List<Integer> fuerzaBruta(String texto, String patron) {
        List<Integer> posiciones = new ArrayList<>();
        if (patron.isEmpty()) {
            return posiciones;
        }
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

    private static String aleatorio(Random azar, int largo, int alfabeto) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) {
            sb.append((char) ('a' + azar.nextInt(alfabeto)));
        }
        return sb.toString();
    }
}
