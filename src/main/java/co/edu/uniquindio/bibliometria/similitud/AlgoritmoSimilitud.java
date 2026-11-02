package co.edu.uniquindio.bibliometria.similitud;

import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Medida de similitud entre dos abstracts del corpus: 1 indica textos idénticos (o vectores con la
 * misma dirección) y 0 textos sin nada en común. Las medidas clásicas están en [0, 1]; el coseno
 * entre embeddings podría ser negativo en teoría, aunque en este corpus nunca lo es.
 */
public interface AlgoritmoSimilitud {

    String nombre();

    /** "Clásico" o "IA". */
    String enfoque();

    /** Similitud entre los artículos en las posiciones i y j del corpus (0..n-1). */
    double similitud(CorpusProcesado corpus, int i, int j);
}
