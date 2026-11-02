package co.edu.uniquindio.bibliometria.agrupamiento;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;

/**
 * Dibuja el dendrograma como SVG, en horizontal: los artículos a la izquierda y la distancia de
 * fusión en el eje horizontal. El orden de las hojas se obtiene recorriendo el árbol desde la raíz
 * (primero el grupo A y después el B de cada fusión), así ninguna rama se cruza con otra. Si se pide
 * un corte en k grupos, las ramas de cada grupo se colorean distinto y se marca la altura del corte.
 */
public final class Dendrograma {

    private static final String[] COLORES = {"#2060C0", "#D96028", "#289870", "#8E44AD", "#C0392B", "#B7950B",
            "#16A085", "#7F8C8D"};
    private static final String COLOR_TRONCO = "#555555";
    private static final int ALTO_FILA = 22;
    private static final int ANCHO_ETIQUETAS = 190;
    private static final int ANCHO_ARBOL = 430;
    private static final int MARGEN = 18;

    private Dendrograma() {
    }

    public static String svg(List<String> etiquetas, List<Fusion> fusiones, int gruposColor) {
        int n = etiquetas.size();
        List<Integer> orden = new ArrayList<>();
        ordenarHojas(n, fusiones, 2 * n - 2, orden);
        double[] y = new double[2 * n - 1];
        double[] x = new double[2 * n - 1];
        for (int fila = 0; fila < n; fila++) {
            y[orden.get(fila)] = MARGEN + fila * ALTO_FILA + ALTO_FILA / 2.0;
        }
        double maxima = fusiones.isEmpty() ? 1 : fusiones.get(fusiones.size() - 1).distancia();
        double minima = fusiones.isEmpty() ? 0 : fusiones.get(0).distancia();
        // Con TF-IDF todas las fusiones ocurren entre 0,85 y 1: el eje empieza un poco antes de la
        // primera fusión (en múltiplos de 0,05) para que el árbol no quede pegado al borde derecho.
        double inicio = Math.max(0, Math.floor((minima - 0.15 * (maxima - minima)) / 0.05) * 0.05);
        double fin = maxima + 0.03 * (maxima - inicio);
        double escala = ANCHO_ARBOL / (fin - inicio <= 0 ? 1 : fin - inicio);

        int[] etiquetaGrupo = gruposColor > 1 ? AgrupamientoJerarquico.cortar(n, fusiones, gruposColor) : new int[n];
        int[] colorDe = new int[2 * n - 1];
        for (int i = 0; i < n; i++) {
            colorDe[i] = etiquetaGrupo[i];
        }

        int ancho = ANCHO_ETIQUETAS + ANCHO_ARBOL + 2 * MARGEN;
        int alto = 2 * MARGEN + n * ALTO_FILA + 30;
        double origen = ANCHO_ETIQUETAS + MARGEN;
        StringBuilder svg = new StringBuilder();
        svg.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" font-family=\"Georgia, serif\""
                        + " font-size=\"12\" role=\"img\">", ancho, alto));

        for (int p = 0; p < fusiones.size(); p++) {
            Fusion f = fusiones.get(p);
            int nuevo = n + p;
            x[nuevo] = origen + (f.distancia() - inicio) * escala;
            y[nuevo] = (y[f.grupoA()] + y[f.grupoB()]) / 2;
            boolean mismoGrupo = colorDe[f.grupoA()] == colorDe[f.grupoB()] && colorDe[f.grupoA()] >= 0;
            colorDe[nuevo] = mismoGrupo ? colorDe[f.grupoA()] : -1;
            String color = gruposColor > 1 && mismoGrupo ? COLORES[colorDe[nuevo] % COLORES.length] : COLOR_TRONCO;
            for (int hijo : new int[] {f.grupoA(), f.grupoB()}) {
                double xHijo = hijo < n ? origen : x[hijo];
                svg.append(String.format(Locale.ROOT,
                        "<path d=\"M%.1f %.1f H%.1f V%.1f\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.8\"/>",
                        xHijo, y[hijo], x[nuevo], y[nuevo], color));
            }
        }

        for (int fila = 0; fila < n; fila++) {
            int hoja = orden.get(fila);
            String color = gruposColor > 1 ? COLORES[etiquetaGrupo[hoja] % COLORES.length] : "#222222";
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"end\" dominant-baseline=\"middle\" fill=\"%s\">%s</text>",
                    origen - 6, y[hoja], color, escapar(etiquetas.get(hoja))));
        }

        if (gruposColor > 1 && gruposColor < n) {
            double corte = (fusiones.get(n - gruposColor - 1).distancia() + fusiones.get(n - gruposColor).distancia()) / 2;
            double xCorte = origen + (corte - inicio) * escala;
            svg.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%d\" x2=\"%.1f\" y2=\"%d\" stroke=\"#999\""
                    + " stroke-dasharray=\"4 3\"/>", xCorte, MARGEN - 6, xCorte, MARGEN + n * ALTO_FILA));
        }

        double base = MARGEN + n * ALTO_FILA + 6;
        svg.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#444\"/>",
                origen, base, origen + ANCHO_ARBOL, base));
        for (int t = 0; t <= 5; t++) {
            double valor = inicio + (fin - inicio) * t / 5;
            double xt = origen + (valor - inicio) * escala;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#444\"/>"
                            + "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" font-size=\"11\" fill=\"#333\">%s</text>",
                    xt, base, xt, base + 4, xt, base + 16, String.format(Locale.ROOT, "%.2f", valor).replace('.', ',')));
        }
        svg.append(String.format(Locale.ROOT,
                "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" font-size=\"11\" fill=\"#333\">"
                        + "Distancia (1 - similitud del coseno)%s</text>",
                origen + ANCHO_ARBOL / 2.0, base + 30.0,
                inicio > 0 ? String.format(Locale.ROOT, ", eje desde %.2f", inicio).replace('.', ',') : ""));
        svg.append("</svg>");
        return svg.toString();
    }

    /** Recorrido en profundidad desde la raíz que deja las hojas en el orden del dibujo. */
    static void ordenarHojas(int n, List<Fusion> fusiones, int grupo, List<Integer> orden) {
        if (grupo < n) {
            orden.add(grupo);
            return;
        }
        Fusion f = fusiones.get(grupo - n);
        ordenarHojas(n, fusiones, f.grupoA(), orden);
        ordenarHojas(n, fusiones, f.grupoB(), orden);
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
