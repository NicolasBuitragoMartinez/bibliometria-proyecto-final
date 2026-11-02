package co.edu.uniquindio.bibliometria.similitud;

import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Similitud del coseno entre los embeddings (vectores densos) de dos abstracts, generados por un
 * modelo de IA preentrenado. Los vectores los produce ia/generar_embeddings.py; aquí solo se
 * calcula la métrica:
 *   cos(θ) = Σ a_k b_k / (√Σ a_k² · √Σ b_k²)      en O(d), con d la dimensión del embedding.
 * También se ofrece la distancia euclidiana, d(a, b) = √Σ (a_k - b_k)².
 */
public final class CosenoEmbeddings implements AlgoritmoSimilitud {

    private final String modelo;
    private final String nombre;

    public CosenoEmbeddings(String modelo, String nombre) {
        this.modelo = modelo;
        this.nombre = nombre;
    }

    public String modelo() {
        return modelo;
    }

    @Override
    public String nombre() {
        return nombre;
    }

    @Override
    public String enfoque() {
        return "IA";
    }

    @Override
    public double similitud(CorpusProcesado corpus, int i, int j) {
        return coseno(corpus.embedding(modelo, i), corpus.embedding(modelo, j));
    }

    public static double coseno(double[] a, double[] b) {
        double producto = 0;
        double normaA = 0;
        double normaB = 0;
        for (int k = 0; k < a.length; k++) {
            producto += a[k] * b[k];
            normaA += a[k] * a[k];
            normaB += b[k] * b[k];
        }
        double normas = Math.sqrt(normaA) * Math.sqrt(normaB);
        return normas == 0 ? 0 : producto / normas;
    }

    public static double distanciaEuclidiana(double[] a, double[] b) {
        double suma = 0;
        for (int k = 0; k < a.length; k++) {
            double diferencia = a[k] - b[k];
            suma += diferencia * diferencia;
        }
        return Math.sqrt(suma);
    }
}
