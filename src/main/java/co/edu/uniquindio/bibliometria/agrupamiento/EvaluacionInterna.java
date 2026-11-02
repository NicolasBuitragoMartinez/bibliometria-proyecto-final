package co.edu.uniquindio.bibliometria.agrupamiento;

import java.util.List;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;

/**
 * Métricas internas para comparar los agrupamientos (no necesitan etiquetas externas):
 * - Coeficiente de correlación cofenética (CCC): correlación de Pearson entre la distancia original
 *   de cada par de artículos y su distancia cofenética, que es la altura de la fusión en la que el
 *   par queda en el mismo grupo. Un CCC cercano a 1 indica que el dendrograma respeta las distancias.
 * - Coeficiente de silueta: para cada artículo i, s(i) = (b - a) / max(a, b), con a la distancia
 *   media a su propio grupo y b la menor distancia media a otro grupo. Va de -1 a 1; valores altos
 *   indican grupos compactos y bien separados.
 */
public final class EvaluacionInterna {

    private EvaluacionInterna() {
    }

    public static double[][] distanciasCofeneticas(int n, List<Fusion> fusiones) {
        double[][] cofenetica = new double[n][n];
        List<List<Integer>> miembros = AgrupamientoJerarquico.miembros(n, fusiones);
        for (Fusion f : fusiones) {
            for (int x : miembros.get(f.grupoA())) {
                for (int y : miembros.get(f.grupoB())) {
                    cofenetica[x][y] = f.distancia();
                    cofenetica[y][x] = f.distancia();
                }
            }
        }
        return cofenetica;
    }

    public static double correlacionCofenetica(double[][] distancias, List<Fusion> fusiones) {
        int n = distancias.length;
        double[][] cofenetica = distanciasCofeneticas(n, fusiones);
        int pares = n * (n - 1) / 2;
        double[] x = new double[pares];
        double[] y = new double[pares];
        int p = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                x[p] = distancias[i][j];
                y[p] = cofenetica[i][j];
                p++;
            }
        }
        return pearson(x, y);
    }

    public static double silueta(double[][] distancias, int[] etiquetas) {
        int n = distancias.length;
        int grupos = 0;
        for (int e : etiquetas) {
            grupos = Math.max(grupos, e + 1);
        }
        double total = 0;
        for (int i = 0; i < n; i++) {
            double[] suma = new double[grupos];
            int[] cantidad = new int[grupos];
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    suma[etiquetas[j]] += distancias[i][j];
                    cantidad[etiquetas[j]]++;
                }
            }
            if (cantidad[etiquetas[i]] == 0) {
                continue; // un artículo solo en su grupo aporta s(i) = 0
            }
            double a = suma[etiquetas[i]] / cantidad[etiquetas[i]];
            double b = Double.POSITIVE_INFINITY;
            for (int g = 0; g < grupos; g++) {
                if (g != etiquetas[i] && cantidad[g] > 0) {
                    b = Math.min(b, suma[g] / cantidad[g]);
                }
            }
            total += (b - a) / Math.max(a, b);
        }
        return total / n;
    }

    static double pearson(double[] x, double[] y) {
        double mediaX = 0;
        double mediaY = 0;
        for (int i = 0; i < x.length; i++) {
            mediaX += x[i];
            mediaY += y[i];
        }
        mediaX /= x.length;
        mediaY /= y.length;
        double covarianza = 0;
        double varianzaX = 0;
        double varianzaY = 0;
        for (int i = 0; i < x.length; i++) {
            covarianza += (x[i] - mediaX) * (y[i] - mediaY);
            varianzaX += (x[i] - mediaX) * (x[i] - mediaX);
            varianzaY += (y[i] - mediaY) * (y[i] - mediaY);
        }
        return covarianza / Math.sqrt(varianzaX * varianzaY);
    }
}
