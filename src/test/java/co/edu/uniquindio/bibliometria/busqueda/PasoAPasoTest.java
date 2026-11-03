package co.edu.uniquindio.bibliometria.busqueda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import co.edu.uniquindio.bibliometria.busqueda.AlgoritmoBusqueda.Resultado;
import co.edu.uniquindio.bibliometria.busqueda.PasoAPaso.Recorrido;

/** El paso a paso debe recorrer el texto igual que cada algoritmo: mismas posiciones y comparaciones. */
class PasoAPasoTest {

    @Test
    void coincideConLasImplementacionesEnTextosAleatorios() {
        Random azar = new Random(5);
        for (int prueba = 0; prueba < 300; prueba++) {
            String texto = aleatorio(azar, 1 + azar.nextInt(120), 2 + azar.nextInt(3));
            String patron = aleatorio(azar, 1 + azar.nextInt(6), 2);
            verificar(PasoAPaso.kmp(texto, patron), new KnuthMorrisPratt().buscar(texto, patron));
            Recorrido bm = PasoAPaso.boyerMoore(texto, patron);
            Resultado esperadoBm = new BoyerMoore().buscar(texto, patron);
            verificar(bm, esperadoBm);
            assertEquals(esperadoBm.ventanas(), bm.alineaciones().size());
            Recorrido rk = PasoAPaso.rabinKarp(texto, patron, 13);
            Resultado esperadoRk = new RabinKarp(13).buscar(texto, patron);
            verificar(rk, esperadoRk);
            assertEquals(esperadoRk.colisiones(), rk.colisiones());
        }
    }

    @Test
    void casoDeEstudioDelDocumento() {
        String frase = "This study begins with a content analysis that explores GAI’s transformative impact";
        for (Recorrido r : PasoAPaso.recorrer(frase, "analysis", 13)) {
            assertEquals(List.of(33), r.posiciones(), r.algoritmo());
        }
    }

    @Test
    void laOracionContieneElPatronYNoSuperaElMaximo() {
        String texto = "First sentence here. " + "word ".repeat(80) + "the pattern analysis appears " + "tail ".repeat(80) + ".";
        int posicion = texto.length() / 2;
        int[] rango = PasoAPaso.oracion(texto, posicion, 8);
        assertTrue(rango[0] <= posicion && posicion + 8 <= rango[1]);
        assertTrue(rango[1] - rango[0] <= PasoAPaso.MAXIMO_TEXTO);
        assertEquals(21, PasoAPaso.oracion("First sentence here. Second one.", 25, 3)[0]);
    }

    private static void verificar(Recorrido recorrido, Resultado esperado) {
        assertEquals(esperado.posiciones(), recorrido.posiciones(), recorrido.algoritmo());
        assertEquals(esperado.comparaciones(), recorrido.comparaciones(), recorrido.algoritmo());
    }

    private static String aleatorio(Random azar, int largo, int letras) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) {
            sb.append((char) ('a' + azar.nextInt(letras)));
        }
        return sb.toString();
    }
}
