package co.edu.uniquindio.bibliometria.ranking.ordenamiento;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import co.edu.uniquindio.bibliometria.ranking.Termino;

/**
 * Compara cada algoritmo contra un ordenamiento de referencia estable (TimSort de
 * Arrays.sort). Si el resultado coincide elemento a elemento, el algoritmo ordena bien
 * y además respeta el orden de entrada en los empates.
 */
class OrdenamientosTest {

    private static final List<AlgoritmoOrdenamiento> ALGORITMOS =
            List.of(new CountingSort(), new RadixSort(), new BucketSort());

    @Test
    void coincidenConReferenciaEstable() {
        Random azar = new Random(7);
        int[] tamanos = {0, 1, 2, 10, 1_000, 20_000};
        int[] rangos = {1, 5, 100, 1_000_000};
        for (int n : tamanos) {
            for (int k : rangos) {
                Termino[] entrada = generar(azar, n, k);
                verificar(entrada);
            }
        }
    }

    @Test
    void distribucionTipoZipf() {
        Random azar = new Random(11);
        Termino[] entrada = new Termino[5_000];
        for (int i = 0; i < entrada.length; i++) {
            entrada[i] = new Termino("t" + i, Math.max(1, 400 / (1 + azar.nextInt(400))));
        }
        verificar(entrada);
    }

    @Test
    void noModificanLaEntrada() {
        Termino[] entrada = generar(new Random(3), 500, 50);
        Termino[] copia = entrada.clone();
        for (AlgoritmoOrdenamiento algoritmo : ALGORITMOS) {
            algoritmo.ordenar(entrada);
            assertArrayEquals(copia, entrada, algoritmo.nombre());
        }
    }

    private static void verificar(Termino[] entrada) {
        Termino[] esperado = entrada.clone();
        Arrays.sort(esperado, Comparator.comparingInt(Termino::frecuencia).reversed());
        for (AlgoritmoOrdenamiento algoritmo : ALGORITMOS) {
            assertArrayEquals(esperado, algoritmo.ordenar(entrada),
                    algoritmo.nombre() + " con n=" + entrada.length);
        }
    }

    private static Termino[] generar(Random azar, int n, int k) {
        Termino[] terminos = new Termino[n];
        for (int i = 0; i < n; i++) {
            terminos[i] = new Termino("t" + i, 1 + azar.nextInt(k));
        }
        return terminos;
    }
}
