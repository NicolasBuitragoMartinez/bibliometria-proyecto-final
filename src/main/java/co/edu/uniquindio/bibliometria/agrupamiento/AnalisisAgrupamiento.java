package co.edu.uniquindio.bibliometria.agrupamiento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Criterio;
import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;
import co.edu.uniquindio.bibliometria.modelo.Articulo;
import co.edu.uniquindio.bibliometria.similitud.CosenoEmbeddings;
import co.edu.uniquindio.bibliometria.similitud.CosenoTfIdf;
import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Flujo completo del requerimiento 2: representación de los abstracts (TF-IDF o embeddings),
 * matriz de distancias d = 1 - coseno, los tres criterios de enlace, las métricas internas y el
 * dendrograma de cada uno.
 */
public final class AnalisisAgrupamiento {

    public static final int GRUPOS_MINIMOS = 2;
    public static final int GRUPOS_MAXIMOS = 8;

    /** Resultado de un criterio: fusiones, CCC, silueta para cada k y el k con mejor silueta. */
    public record Resultado(Criterio criterio, List<Fusion> fusiones, double correlacionCofenetica,
            double[] siluetaPorK, int mejorK, long nanos, String svg) {

        public double mejorSilueta() {
            return siluetaPorK[mejorK];
        }
    }

    private AnalisisAgrupamiento() {
    }

    public static double[][] distancias(CorpusProcesado corpus, String representacion) {
        int n = corpus.tamano();
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double similitud = representacion.equals("tfidf")
                        ? CosenoTfIdf.coseno(corpus.tfidf(i), corpus.tfidf(j))
                        : CosenoEmbeddings.coseno(corpus.embedding(representacion, i), corpus.embedding(representacion, j));
                d[i][j] = 1 - similitud;
                d[j][i] = d[i][j];
            }
        }
        return d;
    }

    public static List<Resultado> analizar(CorpusProcesado corpus, String representacion) {
        double[][] d = distancias(corpus, representacion);
        int n = d.length;
        List<String> etiquetas = corpus.articulos().stream().map(Articulo::etiqueta).toList();
        List<Resultado> resultados = new ArrayList<>();
        for (Criterio criterio : Criterio.values()) {
            long inicio = System.nanoTime();
            List<Fusion> fusiones = AgrupamientoJerarquico.agrupar(d, criterio);
            long nanos = System.nanoTime() - inicio;
            double ccc = EvaluacionInterna.correlacionCofenetica(d, fusiones);
            double[] silueta = new double[GRUPOS_MAXIMOS + 1];
            int mejorK = GRUPOS_MINIMOS;
            for (int k = GRUPOS_MINIMOS; k <= Math.min(GRUPOS_MAXIMOS, n - 1); k++) {
                silueta[k] = EvaluacionInterna.silueta(d, AgrupamientoJerarquico.cortar(n, fusiones, k));
                if (silueta[k] > silueta[mejorK]) {
                    mejorK = k;
                }
            }
            resultados.add(new Resultado(criterio, fusiones, ccc, silueta, mejorK, nanos,
                    Dendrograma.svg(etiquetas, fusiones, mejorK)));
        }
        return resultados;
    }

    /** Representaciones disponibles: TF-IDF siempre y los modelos de IA si están sus embeddings. */
    public static Map<String, String> representaciones(CorpusProcesado corpus) {
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("tfidf", "TF-IDF");
        if (corpus.tieneEmbeddings("minilm")) {
            mapa.put("minilm", "Embeddings MiniLM");
        }
        if (corpus.tieneEmbeddings("glove")) {
            mapa.put("glove", "Embeddings GloVe");
        }
        return mapa;
    }
}
