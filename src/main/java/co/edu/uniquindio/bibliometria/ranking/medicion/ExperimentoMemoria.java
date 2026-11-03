package co.edu.uniquindio.bibliometria.ranking.medicion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import co.edu.uniquindio.bibliometria.ranking.medicion.MedidorTiempos.Medicion;
import co.edu.uniquindio.bibliometria.ranking.Termino;
import co.edu.uniquindio.bibliometria.ranking.ordenamiento.AlgoritmoOrdenamiento;

/**
 * Experimento de jerarquía de memoria (opción --memoria). Sirve para comprobar que el aumento
 * del tiempo por término con n grande se debe a la memoria y no a que crezca el número de
 * operaciones.
 *
 * Para cada n se arman dos listas con las mismas frecuencias en las mismas posiciones, así que
 * cada algoritmo ejecuta exactamente las mismas instrucciones sobre las dos. Solo cambia dónde
 * quedan los objetos Termino en la memoria:
 *  - contigua: los objetos se crean en el orden del arreglo; recorrer el arreglo es recorrer la
 *    memoria de forma secuencial y el procesador puede anticipar (prefetch) las líneas de caché.
 *  - dispersa: los objetos se crean en un orden aleatorio; posiciones vecinas del arreglo apuntan
 *    a direcciones lejanas y cada lectura de la frecuencia puede ser un fallo de caché.
 * Todos los términos comparten la misma cadena, de modo que entre dos objetos Termino no queda
 * ningún otro objeto y el tamaño de los datos que se recorren es fácil de calcular.
 */
public final class ExperimentoMemoria {

    public record Resultado(String disposicion, Medicion medicion) {
    }

    private static final int[] TAMANOS = {1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000,
            200_000, 500_000, 1_000_000, 2_000_000};
    private static final String[] DISPOSICIONES = {"contigua", "dispersa"};
    private static final String PALABRA = "t";
    private static final long SEMILLA = 42;

    private ExperimentoMemoria() {
    }

    public static List<Resultado> medir(Termino[] corpus, List<AlgoritmoOrdenamiento> algoritmos) {
        Random azar = new Random(SEMILLA);
        List<Resultado> resultados = new ArrayList<>();
        for (int n : TAMANOS) {
            int[] frecuencias = new int[n];
            for (int i = 0; i < n; i++) {
                frecuencias[i] = corpus[azar.nextInt(corpus.length)].frecuencia();
            }
            Termino[] contigua = new Termino[n];
            for (int i = 0; i < n; i++) {
                contigua[i] = new Termino(PALABRA, frecuencias[i]);
            }
            Termino[] dispersa = new Termino[n];
            for (int i : permutacion(n, azar)) {
                dispersa[i] = new Termino(PALABRA, frecuencias[i]);
            }
            // La recolección completa compacta el heap sin cambiar el orden relativo de los objetos
            // y los pasa a la generación vieja, donde ya no se mueven durante la medición.
            System.gc();
            resultados.addAll(medirIntercalado(new Termino[][] {contigua, dispersa}, algoritmos));
        }
        return resultados;
    }

    /** Bytes que ocupa cada término en el heap (el objeto más su referencia en el arreglo). */
    public static double bytesPorTermino() {
        int n = 1_000_000;
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long antes = runtime.totalMemory() - runtime.freeMemory();
        Termino[] lista = new Termino[n];
        for (int i = 0; i < n; i++) {
            lista[i] = new Termino(PALABRA, i);
        }
        System.gc();
        long despues = runtime.totalMemory() - runtime.freeMemory();
        double bytes = (double) (despues - antes) / n;
        return lista[n - 1].frecuencia() >= 0 ? bytes : 0;
    }

    /**
     * Rondas intercaladas: en cada ronda se ordenan las dos listas con los tres algoritmos, para
     * que una pausa del sistema afecte por igual a todas las combinaciones.
     */
    private static List<Resultado> medirIntercalado(Termino[][] listas, List<AlgoritmoOrdenamiento> algoritmos) {
        int n = listas[0].length;
        int repeticiones = Math.max(5, Math.min(100, 2_000_000 / n));
        int combinaciones = listas.length * algoritmos.size();
        double[] frio = new double[combinaciones];
        double[][] tiempos = new double[combinaciones][repeticiones];
        for (int r = -repeticiones; r < repeticiones; r++) { // las rondas negativas son de calentamiento
            for (int l = 0; l < listas.length; l++) {
                for (int a = 0; a < algoritmos.size(); a++) {
                    double ms = MedidorTiempos.medirUnaVez(algoritmos.get(a), listas[l]);
                    int c = l * algoritmos.size() + a;
                    if (r == -repeticiones) {
                        frio[c] = ms;
                    } else if (r >= 0) {
                        tiempos[c][r] = ms;
                    }
                }
            }
        }
        int k = AlgoritmoOrdenamiento.frecuenciaMaxima(listas[0]) - AlgoritmoOrdenamiento.frecuenciaMinima(listas[0]) + 1;
        List<Resultado> resultados = new ArrayList<>();
        for (int l = 0; l < listas.length; l++) {
            for (int a = 0; a < algoritmos.size(); a++) {
                double[] t = tiempos[l * algoritmos.size() + a];
                double promedio = Arrays.stream(t).average().orElse(0);
                double minimo = Arrays.stream(t).min().orElse(0);
                resultados.add(new Resultado(DISPOSICIONES[l], new Medicion(algoritmos.get(a).nombre(), n, k,
                        frio[l * algoritmos.size() + a], Corridas.mediana(t), promedio, minimo, repeticiones)));
            }
        }
        return resultados;
    }

    /** Permutación aleatoria de 0..n-1 (Fisher-Yates). */
    private static int[] permutacion(int n, Random azar) {
        int[] orden = new int[n];
        for (int i = 0; i < n; i++) {
            orden[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = azar.nextInt(i + 1);
            int temporal = orden[i];
            orden[i] = orden[j];
            orden[j] = temporal;
        }
        return orden;
    }
}
