package co.edu.uniquindio.bibliometria.similitud;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Coeficiente de Jaccard entre los conjuntos de términos de dos abstracts:
 *   J(A, B) = |A ∩ B| / |A ∪ B| = |A ∩ B| / (|A| + |B| - |A ∩ B|)
 * Ignora cuántas veces aparece cada término y el orden. Con tablas hash, construir los conjuntos
 * cuesta O(m + n) y contar la intersección O(min(|A|, |B|)) en promedio.
 */
public final class Jaccard implements AlgoritmoSimilitud {

    @Override
    public String nombre() {
        return "Jaccard";
    }

    @Override
    public String enfoque() {
        return "Clásico";
    }

    @Override
    public double similitud(CorpusProcesado corpus, int i, int j) {
        return similitud(corpus.tokens(i), corpus.tokens(j));
    }

    public static double similitud(List<String> a, List<String> b) {
        Set<String> conjuntoA = new HashSet<>(a);
        Set<String> conjuntoB = new HashSet<>(b);
        int interseccion = interseccion(conjuntoA, conjuntoB);
        int union = conjuntoA.size() + conjuntoB.size() - interseccion;
        return union == 0 ? 1.0 : (double) interseccion / union;
    }

    public static int interseccion(Set<String> a, Set<String> b) {
        Set<String> menor = a.size() <= b.size() ? a : b;
        Set<String> mayor = menor == a ? b : a;
        int comunes = 0;
        for (String termino : menor) {
            if (mayor.contains(termino)) {
                comunes++;
            }
        }
        return comunes;
    }
}
