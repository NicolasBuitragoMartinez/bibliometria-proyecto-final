package co.edu.uniquindio.bibliometria.ranking.medicion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import co.edu.uniquindio.bibliometria.ranking.medicion.MedidorTiempos.Medicion;
import co.edu.uniquindio.bibliometria.ranking.Termino;
import co.edu.uniquindio.bibliometria.ranking.ordenamiento.AlgoritmoOrdenamiento;

/**
 * Experimento complementario (opción --escalabilidad). El corpus real tiene pocos términos,
 * así que los tiempos son fracciones de milisegundo; aquí se generan listas más grandes para
 * observar cómo crece el tiempo:
 * A) n crece y las frecuencias se muestrean de la distribución real del corpus (k fijo).
 * B) n fijo y el rango de frecuencias k crece (frecuencias uniformes en [1, k]).
 */
public final class ExperimentoEscalabilidad {

    private static final int[] TAMANOS = {1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000, 200_000,
            500_000, 1_000_000};
    private static final int N_FIJO = 100_000;
    private static final int[] RANGOS = {100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};
    private static final long SEMILLA = 42;

    private ExperimentoEscalabilidad() {
    }

    public static List<Medicion> variarN(Termino[] corpus, List<AlgoritmoOrdenamiento> algoritmos) {
        Random azar = new Random(SEMILLA);
        List<Medicion> resultados = new ArrayList<>();
        for (int n : TAMANOS) {
            Termino[] lista = new Termino[n];
            for (int i = 0; i < n; i++) {
                lista[i] = new Termino("t" + i, corpus[azar.nextInt(corpus.length)].frecuencia());
            }
            resultados.addAll(medirTodos(lista, algoritmos));
        }
        return resultados;
    }

    public static List<Medicion> variarK(List<AlgoritmoOrdenamiento> algoritmos) {
        Random azar = new Random(SEMILLA);
        List<Medicion> resultados = new ArrayList<>();
        for (int k : RANGOS) {
            Termino[] lista = new Termino[N_FIJO];
            for (int i = 0; i < N_FIJO; i++) {
                lista[i] = new Termino("t" + i, 1 + azar.nextInt(k));
            }
            resultados.addAll(medirTodos(lista, algoritmos));
        }
        return resultados;
    }

    private static List<Medicion> medirTodos(Termino[] lista, List<AlgoritmoOrdenamiento> algoritmos) {
        int repeticiones = Math.max(5, Math.min(100, 2_000_000 / lista.length));
        return MedidorTiempos.medir(algoritmos, lista, repeticiones, repeticiones);
    }
}
