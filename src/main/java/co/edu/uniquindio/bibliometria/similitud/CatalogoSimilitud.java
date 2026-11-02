package co.edu.uniquindio.bibliometria.similitud;

import java.util.ArrayList;
import java.util.List;

import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/** Los seis algoritmos del requerimiento 1: cuatro clásicos y dos basados en modelos de IA. */
public final class CatalogoSimilitud {

    private CatalogoSimilitud() {
    }

    public static List<AlgoritmoSimilitud> disponibles(CorpusProcesado corpus) {
        List<AlgoritmoSimilitud> algoritmos = new ArrayList<>(List.of(
                new Levenshtein(), new NeedlemanWunsch(), new Jaccard(), new CosenoTfIdf()));
        if (corpus.tieneEmbeddings("glove")) {
            algoritmos.add(new CosenoEmbeddings("glove", "GloVe (embeddings de palabras)"));
        }
        if (corpus.tieneEmbeddings("minilm")) {
            algoritmos.add(new CosenoEmbeddings("minilm", "MiniLM (Sentence-BERT)"));
        }
        return algoritmos;
    }
}
