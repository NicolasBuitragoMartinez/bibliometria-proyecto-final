package co.edu.uniquindio.bibliometria.agrupamiento;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupamiento jerárquico aglomerativo implementado desde cero. Parte de n grupos de un elemento y
 * en cada paso fusiona los dos grupos más cercanos, hasta dejar uno solo (n - 1 fusiones). La
 * distancia entre el grupo fusionado A ∪ B y otro grupo R se actualiza con la fórmula de
 * Lance-Williams según el criterio:
 *   simple (single linkage):     d(A∪B, R) = min(d(A,R), d(B,R))
 *   completo (complete linkage): d(A∪B, R) = max(d(A,R), d(B,R))
 *   promedio (average, UPGMA):   d(A∪B, R) = (|A| d(A,R) + |B| d(B,R)) / (|A| + |B|)
 * Cada paso busca el mínimo en la matriz (O(n²)) y actualiza una fila (O(n)): en total O(n³) en
 * tiempo y O(n²) en espacio, suficiente para los 20 abstracts.
 */
public final class AgrupamientoJerarquico {

    public enum Criterio {
        SIMPLE("Single linkage"),
        COMPLETO("Complete linkage"),
        PROMEDIO("Average linkage");

        private final String nombre;

        Criterio(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

    /**
     * Una fusión: los identificadores de los dos grupos (0..n-1 son los artículos y n + p el grupo
     * creado en el paso p), la distancia a la que se unen y cuántos artículos tiene el nuevo grupo.
     */
    public record Fusion(int grupoA, int grupoB, double distancia, int tamano) {
    }

    private AgrupamientoJerarquico() {
    }

    public static List<Fusion> agrupar(double[][] distancias, Criterio criterio) {
        int n = distancias.length;
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            d[i] = distancias[i].clone();
        }
        int[] identificador = new int[n];
        int[] tamano = new int[n];
        boolean[] activo = new boolean[n];
        for (int i = 0; i < n; i++) {
            identificador[i] = i;
            tamano[i] = 1;
            activo[i] = true;
        }

        List<Fusion> fusiones = new ArrayList<>();
        for (int paso = 0; paso < n - 1; paso++) {
            // 1. Par de grupos activos más cercano.
            int p = -1;
            int q = -1;
            double minima = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (!activo[i]) {
                    continue;
                }
                for (int j = i + 1; j < n; j++) {
                    if (activo[j] && d[i][j] < minima) {
                        minima = d[i][j];
                        p = i;
                        q = j;
                    }
                }
            }
            int a = Math.min(identificador[p], identificador[q]);
            int b = Math.max(identificador[p], identificador[q]);
            fusiones.add(new Fusion(a, b, minima, tamano[p] + tamano[q]));

            // 2. Lance-Williams: distancia del grupo fusionado (queda en la fila p) a los demás.
            for (int r = 0; r < n; r++) {
                if (!activo[r] || r == p || r == q) {
                    continue;
                }
                double nueva = switch (criterio) {
                    case SIMPLE -> Math.min(d[p][r], d[q][r]);
                    case COMPLETO -> Math.max(d[p][r], d[q][r]);
                    case PROMEDIO -> (tamano[p] * d[p][r] + tamano[q] * d[q][r]) / (tamano[p] + tamano[q]);
                };
                d[p][r] = nueva;
                d[r][p] = nueva;
            }
            activo[q] = false;
            tamano[p] += tamano[q];
            identificador[p] = n + paso;
        }
        return fusiones;
    }

    /** Miembros (artículos 0..n-1) de cada grupo creado por las fusiones, indexado por identificador. */
    public static List<List<Integer>> miembros(int n, List<Fusion> fusiones) {
        List<List<Integer>> miembros = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            miembros.add(List.of(i));
        }
        for (Fusion f : fusiones) {
            List<Integer> union = new ArrayList<>(miembros.get(f.grupoA()));
            union.addAll(miembros.get(f.grupoB()));
            miembros.add(union);
        }
        return miembros;
    }

    /**
     * Etiqueta de grupo (0..k-1) de cada artículo al cortar el árbol en k grupos: se aplican las
     * primeras n - k fusiones y cada grupo que no fue absorbido por otra fusión es un grupo final.
     */
    public static int[] cortar(int n, List<Fusion> fusiones, int k) {
        List<List<Integer>> miembros = miembros(n, fusiones);
        int existentes = n + (n - k);
        boolean[] absorbido = new boolean[existentes];
        for (int p = 0; p < n - k; p++) {
            absorbido[fusiones.get(p).grupoA()] = true;
            absorbido[fusiones.get(p).grupoB()] = true;
        }
        int[] etiqueta = new int[n];
        int siguiente = 0;
        for (int g = 0; g < existentes; g++) {
            if (!absorbido[g]) {
                for (int i : miembros.get(g)) {
                    etiqueta[i] = siguiente;
                }
                siguiente++;
            }
        }
        return etiqueta;
    }
}
