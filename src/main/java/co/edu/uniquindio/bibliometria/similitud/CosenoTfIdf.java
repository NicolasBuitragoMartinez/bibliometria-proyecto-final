package co.edu.uniquindio.bibliometria.similitud;

import java.util.Map;

import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Similitud del coseno entre los vectores TF-IDF de dos abstracts (modelo de espacio vectorial):
 *   w(t, d) = tf(t, d) · idf(t),  tf = f(t, d) / |d|,  idf = ln(N / df(t))
 *   cos(θ) = (A · B) / (‖A‖ ‖B‖)
 * Los vectores son dispersos (solo los términos presentes), así que el producto punto cuesta
 * O(min(|A|, |B|)) consultas a la tabla hash del otro vector.
 */
public final class CosenoTfIdf implements AlgoritmoSimilitud {

    @Override
    public String nombre() {
        return "Coseno TF-IDF";
    }

    @Override
    public String enfoque() {
        return "Clásico";
    }

    @Override
    public double similitud(CorpusProcesado corpus, int i, int j) {
        return coseno(corpus.tfidf(i), corpus.tfidf(j));
    }

    public static double coseno(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> menor = a.size() <= b.size() ? a : b;
        Map<String, Double> mayor = menor == a ? b : a;
        double producto = 0;
        for (Map.Entry<String, Double> e : menor.entrySet()) {
            Double otro = mayor.get(e.getKey());
            if (otro != null) {
                producto += e.getValue() * otro;
            }
        }
        double normas = norma(a) * norma(b);
        return normas == 0 ? 0 : producto / normas;
    }

    public static double norma(Map<String, Double> vector) {
        double suma = 0;
        for (double w : vector.values()) {
            suma += w * w;
        }
        return Math.sqrt(suma);
    }
}
