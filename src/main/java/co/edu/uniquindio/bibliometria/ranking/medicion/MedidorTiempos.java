package co.edu.uniquindio.bibliometria.ranking.medicion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import co.edu.uniquindio.bibliometria.ranking.Termino;
import co.edu.uniquindio.bibliometria.ranking.ordenamiento.AlgoritmoOrdenamiento;

/**
 * Mide tiempos reales con {@link System#nanoTime()}. Como la JVM interpreta el
 * código al
 * principio y luego lo compila (JIT), se reportan dos cosas:
 * - la primera ejecución de cada algoritmo ("en frío"), y
 * - la mediana de varias ejecuciones tras un calentamiento ("en caliente").
 * Las repeticiones se hacen por rondas (Counting, Radix, Bucket, Counting, ...)
 * para que
 * cualquier perturbación del sistema (recolector de basura, frecuencia de la
 * CPU) afecte
 * a los tres algoritmos por igual.
 */
public final class MedidorTiempos {

    public record Medicion(String algoritmo, int n, int k, double frioMs,
            double medianaMs, double promedioMs, double minimoMs, int repeticiones) {
    }

    /** Evita que el JIT elimine llamadas cuyo resultado no se usa. */
    private static volatile int sumidero;

    private MedidorTiempos() {
    }

    public static double medirUnaVez(AlgoritmoOrdenamiento algoritmo, Termino[] entrada) {
        long inicio = System.nanoTime();
        Termino[] resultado = algoritmo.ordenar(entrada);
        long fin = System.nanoTime();
        sumidero += resultado.length == 0 ? 0 : resultado[0].frecuencia();
        return (fin - inicio) / 1_000_000.0;
    }

    public static List<Medicion> medir(List<AlgoritmoOrdenamiento> algoritmos, Termino[] entrada,
            int calentamiento, int repeticiones) {
        int cantidad = algoritmos.size();
        double[] frio = new double[cantidad];
        for (int a = 0; a < cantidad; a++) {
            frio[a] = medirUnaVez(algoritmos.get(a), entrada);
        }
        for (int r = 0; r < calentamiento; r++) {
            for (AlgoritmoOrdenamiento algoritmo : algoritmos) {
                medirUnaVez(algoritmo, entrada);
            }
        }
        double[][] tiempos = new double[cantidad][repeticiones];
        for (int r = 0; r < repeticiones; r++) {
            for (int a = 0; a < cantidad; a++) {
                tiempos[a][r] = medirUnaVez(algoritmos.get(a), entrada);
            }
        }

        int k = entrada.length == 0 ? 0
                : AlgoritmoOrdenamiento.frecuenciaMaxima(entrada) - AlgoritmoOrdenamiento.frecuenciaMinima(entrada) + 1;
        List<Medicion> mediciones = new ArrayList<>();
        for (int a = 0; a < cantidad; a++) {
            double[] t = tiempos[a];
            Arrays.sort(t);
            double mediana = repeticiones % 2 == 1
                    ? t[repeticiones / 2]
                    : (t[repeticiones / 2 - 1] + t[repeticiones / 2]) / 2;
            double promedio = Arrays.stream(t).average().orElse(0);
            mediciones.add(new Medicion(algoritmos.get(a).nombre(), entrada.length, k, frio[a],
                    mediana, promedio, t[0], repeticiones));
        }
        return mediciones;
    }
}
