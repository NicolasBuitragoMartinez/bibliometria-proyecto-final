package co.edu.uniquindio.bibliometria.texto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import co.edu.uniquindio.bibliometria.modelo.Articulo;

/**
 * Representaciones de los abstracts que usan los algoritmos. Se calculan una sola vez al cargar el
 * corpus:
 * - texto normalizado (minúsculas, sin tildes, solo letras y espacios), para Levenshtein;
 * - tokens tras el preprocesamiento (sin stopwords), para Needleman-Wunsch y Jaccard;
 * - vectores TF-IDF dispersos, para el coseno y el agrupamiento;
 * - embeddings de los modelos de IA (si existen los archivos generados por ia/generar_embeddings.py).
 */
public final class CorpusProcesado {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern NO_LETRAS = Pattern.compile("[^a-z]+");

    private final List<Articulo> articulos;
    private final List<String> textosNormalizados = new ArrayList<>();
    private final List<List<String>> tokens = new ArrayList<>();
    private final List<Map<String, Double>> tfidf = new ArrayList<>();
    private final Map<String, Integer> frecuenciaDocumental = new LinkedHashMap<>();
    private final Map<String, double[][]> embeddings;

    public CorpusProcesado(List<Articulo> articulos, Map<String, double[][]> embeddings) {
        this.articulos = List.copyOf(articulos);
        this.embeddings = embeddings;
        Preprocesador preprocesador = new Preprocesador();
        for (Articulo a : articulos) {
            textosNormalizados.add(normalizar(a.abstractTexto()));
            tokens.add(preprocesador.tokenizar(a.abstractTexto()));
        }
        calcularTfIdf();
    }

    /** tf(t, d) = f(t, d) / |d|   e   idf(t) = ln(N / df(t)). */
    private void calcularTfIdf() {
        for (List<String> documento : tokens) {
            for (String termino : new LinkedHashSet<>(documento)) {
                frecuenciaDocumental.merge(termino, 1, Integer::sum);
            }
        }
        int n = tokens.size();
        for (List<String> documento : tokens) {
            Map<String, Integer> conteo = new LinkedHashMap<>();
            for (String termino : documento) {
                conteo.merge(termino, 1, Integer::sum);
            }
            Map<String, Double> vector = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : conteo.entrySet()) {
                double tf = (double) e.getValue() / documento.size();
                double idf = Math.log((double) n / frecuenciaDocumental.get(e.getKey()));
                vector.put(e.getKey(), tf * idf);
            }
            tfidf.add(vector);
        }
    }

    public static String normalizar(String texto) {
        String sinTildes = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFKD)).replaceAll("");
        return NO_LETRAS.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    public int tamano() {
        return articulos.size();
    }

    public List<Articulo> articulos() {
        return articulos;
    }

    /** Posición (0..n-1) del artículo con ese número (1..20). */
    public int indice(int numeroArticulo) {
        for (int i = 0; i < articulos.size(); i++) {
            if (articulos.get(i).numero() == numeroArticulo) {
                return i;
            }
        }
        throw new IllegalArgumentException("No existe el artículo " + numeroArticulo);
    }

    public String textoNormalizado(int i) {
        return textosNormalizados.get(i);
    }

    public List<String> tokens(int i) {
        return tokens.get(i);
    }

    public Map<String, Double> tfidf(int i) {
        return tfidf.get(i);
    }

    public int frecuenciaDocumental(String termino) {
        return frecuenciaDocumental.getOrDefault(termino, 0);
    }

    public boolean tieneEmbeddings(String modelo) {
        return embeddings.containsKey(modelo);
    }

    public double[] embedding(String modelo, int i) {
        double[][] vectores = embeddings.get(modelo);
        if (vectores == null) {
            throw new IllegalStateException("No se encontraron los embeddings del modelo " + modelo
                    + "; ejecute ia/generar_embeddings.py");
        }
        return vectores[i];
    }
}
