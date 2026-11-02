package co.edu.uniquindio.bibliometria.experimentos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico;
import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Criterio;
import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;
import co.edu.uniquindio.bibliometria.agrupamiento.AnalisisAgrupamiento;
import co.edu.uniquindio.bibliometria.busqueda.BoyerMoore;
import co.edu.uniquindio.bibliometria.busqueda.KnuthMorrisPratt;
import co.edu.uniquindio.bibliometria.busqueda.RabinKarp;
import co.edu.uniquindio.bibliometria.similitud.CosenoEmbeddings;
import co.edu.uniquindio.bibliometria.similitud.CosenoTfIdf;
import co.edu.uniquindio.bibliometria.similitud.Jaccard;
import co.edu.uniquindio.bibliometria.similitud.Levenshtein;
import co.edu.uniquindio.bibliometria.similitud.NeedlemanWunsch;
import co.edu.uniquindio.bibliometria.similitud.NeedlemanWunsch.Alineamiento;
import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Genera las demostraciones paso a paso del documento técnico (casos de estudio de los tres
 * requerimientos) como archivos de texto en resultados/trazas/. Todos los números de las tablas
 * del documento salen de aquí, calculados por las mismas clases que usa la aplicación.
 */
public final class Trazas {

    /** Caso de estudio de similitud: artículos 11 y 16 (IA generativa y evaluación). */
    public static final int ARTICULO_A = 11;
    public static final int ARTICULO_B = 16;
    /** Palabras de cada abstract para la matriz de Levenshtein. */
    public static final String PALABRA_A = "generative";
    public static final String PALABRA_B = "emergence";
    public static final int TOKENS_NW = 8;
    /** Caso de estudio de búsqueda: patrón y frase del artículo 18. */
    public static final String PATRON = "analysis";
    public static final String FRASE = "This study begins with a content analysis that explores GAI’s transformative impact";
    public static final long PRIMO_PEQUENO = 13;
    /** Subconjunto de artículos para mostrar a mano las actualizaciones de Lance-Williams. */
    public static final int[] SUBCONJUNTO = {11, 15, 16, 17, 19};

    private Trazas() {
    }

    public static void generar(CorpusProcesado corpus, Path carpeta) throws IOException {
        Files.createDirectories(carpeta);
        int a = corpus.indice(ARTICULO_A);
        int b = corpus.indice(ARTICULO_B);
        escribir(carpeta.resolve("1_levenshtein.txt"), levenshtein(corpus, a, b));
        escribir(carpeta.resolve("2_needleman_wunsch.txt"), needlemanWunsch(corpus, a, b));
        escribir(carpeta.resolve("3_jaccard.txt"), jaccard(corpus, a, b));
        escribir(carpeta.resolve("4_coseno_tfidf.txt"), cosenoTfIdf(corpus, a, b));
        escribir(carpeta.resolve("5_embeddings.txt"), embeddings(corpus, a, b));
        escribir(carpeta.resolve("6_agrupamiento.txt"), agrupamiento(corpus));
        escribir(carpeta.resolve("7_kmp.txt"), kmp());
        escribir(carpeta.resolve("8_boyer_moore.txt"), boyerMoore());
        escribir(carpeta.resolve("9_rabin_karp.txt"), rabinKarp());
        System.out.println("Trazas guardadas en " + carpeta.toAbsolutePath());
    }

    private static List<String> levenshtein(CorpusProcesado corpus, int a, int b) {
        List<String> l = new ArrayList<>();
        int[][] d = Levenshtein.matriz(PALABRA_A, PALABRA_B);
        l.add("Matriz D para \"" + PALABRA_A + "\" (filas) y \"" + PALABRA_B + "\" (columnas)");
        StringBuilder encabezado = new StringBuilder("\t\tε");
        for (char c : PALABRA_B.toCharArray()) {
            encabezado.append('\t').append(c);
        }
        l.add(encabezado.toString());
        for (int i = 0; i <= PALABRA_A.length(); i++) {
            StringBuilder fila = new StringBuilder(i == 0 ? "ε" : String.valueOf(PALABRA_A.charAt(i - 1)));
            fila.append('\t');
            for (int j = 0; j <= PALABRA_B.length(); j++) {
                fila.append('\t').append(d[i][j]);
            }
            l.add(fila.toString());
        }
        l.add(String.format(Locale.ROOT, "Distancia(%s, %s) = %d; similitud = 1 - %d/%d = %.4f", PALABRA_A, PALABRA_B,
                d[PALABRA_A.length()][PALABRA_B.length()], d[PALABRA_A.length()][PALABRA_B.length()],
                Math.max(PALABRA_A.length(), PALABRA_B.length()), Levenshtein.similitud(PALABRA_A, PALABRA_B)));
        String ta = corpus.textoNormalizado(a);
        String tb = corpus.textoNormalizado(b);
        int distancia = Levenshtein.distancia(ta, tb);
        l.add(String.format(Locale.ROOT, "Abstracts completos: |a| = %d, |b| = %d caracteres, celdas = %d, distancia = %d,"
                + " similitud = %.4f", ta.length(), tb.length(), (long) (ta.length() + 1) * (tb.length() + 1), distancia,
                Levenshtein.similitud(ta, tb)));
        return l;
    }

    private static List<String> needlemanWunsch(CorpusProcesado corpus, int a, int b) {
        List<String> l = new ArrayList<>();
        List<String> ta = corpus.tokens(a).subList(0, TOKENS_NW);
        List<String> tb = corpus.tokens(b).subList(0, TOKENS_NW);
        int[][] f = NeedlemanWunsch.matriz(ta, tb);
        l.add("Matriz F (coincidencia +1, sustitución -1, hueco -1). Filas: " + ta + "; columnas: " + tb);
        l.add("\t\t-\t" + String.join("\t", tb));
        for (int i = 0; i <= ta.size(); i++) {
            StringBuilder fila = new StringBuilder(i == 0 ? "-" : ta.get(i - 1)).append('\t');
            for (int j = 0; j <= tb.size(); j++) {
                fila.append('\t').append(f[i][j]);
            }
            l.add(fila.toString());
        }
        Alineamiento parcial = NeedlemanWunsch.alinear(ta, tb);
        l.add("Alineamiento A: " + String.join(" | ", parcial.a()));
        l.add("Alineamiento B: " + String.join(" | ", parcial.b()));
        l.add(String.format(Locale.ROOT, "Puntaje = %d, coincidencias = %d, longitud = %d, identidad = %.4f",
                parcial.puntaje(), parcial.coincidencias(), parcial.a().size(), parcial.identidad()));
        Alineamiento completo = NeedlemanWunsch.alinear(corpus.tokens(a), corpus.tokens(b));
        l.add(String.format(Locale.ROOT, "Abstracts completos: %d y %d términos, puntaje = %d, coincidencias = %d,"
                + " longitud del alineamiento = %d, identidad = %.4f", corpus.tokens(a).size(), corpus.tokens(b).size(),
                completo.puntaje(), completo.coincidencias(), completo.a().size(), completo.identidad()));
        return l;
    }

    private static List<String> jaccard(CorpusProcesado corpus, int a, int b) {
        Set<String> sa = new TreeSet<>(corpus.tokens(a));
        Set<String> sb = new TreeSet<>(corpus.tokens(b));
        Set<String> interseccion = new TreeSet<>(sa);
        interseccion.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        List<String> l = new ArrayList<>();
        l.add("|A| = " + sa.size() + ", |B| = " + sb.size() + ", |A ∩ B| = " + interseccion.size() + ", |A ∪ B| = "
                + union.size());
        l.add("A ∩ B = " + interseccion);
        l.add(String.format(Locale.ROOT, "J = %d / %d = %.4f", interseccion.size(), union.size(),
                Jaccard.similitud(corpus.tokens(a), corpus.tokens(b))));
        return l;
    }

    private static List<String> cosenoTfIdf(CorpusProcesado corpus, int a, int b) {
        Map<String, Double> va = corpus.tfidf(a);
        Map<String, Double> vb = corpus.tfidf(b);
        List<String> l = new ArrayList<>();
        l.add("termino\tf_a\tf_b\tdf\tidf\tw_a\tw_b\tw_a*w_b");
        List<String> comunes = new ArrayList<>();
        for (String t : va.keySet()) {
            if (vb.containsKey(t)) {
                comunes.add(t);
            }
        }
        comunes.sort((x, y) -> Double.compare(va.get(y) * vb.get(y), va.get(x) * vb.get(x)));
        double producto = 0;
        for (String t : comunes) {
            producto += va.get(t) * vb.get(t);
            long fa = corpus.tokens(a).stream().filter(t::equals).count();
            long fb = corpus.tokens(b).stream().filter(t::equals).count();
            double idf = Math.log((double) corpus.tamano() / corpus.frecuenciaDocumental(t));
            l.add(String.format(Locale.ROOT, "%s\t%d\t%d\t%d\t%.4f\t%.5f\t%.5f\t%.7f", t, fa, fb,
                    corpus.frecuenciaDocumental(t), idf, va.get(t), vb.get(t), va.get(t) * vb.get(t)));
        }
        double coseno = CosenoTfIdf.coseno(va, vb);
        l.add(String.format(Locale.ROOT, "|d_a| = %d, |d_b| = %d términos; términos distintos: %d y %d; comunes: %d",
                corpus.tokens(a).size(), corpus.tokens(b).size(), va.size(), vb.size(), comunes.size()));
        l.add(String.format(Locale.ROOT, "A·B = %.6f, ‖A‖ = %.6f, ‖B‖ = %.6f, cos = %.4f, ángulo = %.2f grados", producto,
                CosenoTfIdf.norma(va), CosenoTfIdf.norma(vb), coseno, Math.toDegrees(Math.acos(Math.min(1, coseno)))));
        return l;
    }

    private static List<String> embeddings(CorpusProcesado corpus, int a, int b) {
        List<String> l = new ArrayList<>();
        for (String modelo : List.of("glove", "minilm")) {
            if (!corpus.tieneEmbeddings(modelo)) {
                l.add(modelo + ": no hay embeddings (ejecute ia/generar_embeddings.py)");
                continue;
            }
            double[] ea = corpus.embedding(modelo, a);
            double[] eb = corpus.embedding(modelo, b);
            double producto = 0;
            double na = 0;
            double nb = 0;
            for (int k = 0; k < ea.length; k++) {
                producto += ea[k] * eb[k];
                na += ea[k] * ea[k];
                nb += eb[k] * eb[k];
            }
            double coseno = CosenoEmbeddings.coseno(ea, eb);
            l.add(String.format(Locale.ROOT, "%s: dimensión %d; a[0..4] = %s; b[0..4] = %s", modelo, ea.length,
                    primeros(ea), primeros(eb)));
            l.add(String.format(Locale.ROOT, "%s: A·B = %.6f, ‖A‖ = %.6f, ‖B‖ = %.6f, cos = %.4f, ángulo = %.2f grados,"
                    + " distancia euclidiana = %.4f", modelo, producto, Math.sqrt(na), Math.sqrt(nb), coseno,
                    Math.toDegrees(Math.acos(Math.min(1, coseno))), CosenoEmbeddings.distanciaEuclidiana(ea, eb)));
        }
        return l;
    }

    private static List<String> agrupamiento(CorpusProcesado corpus) {
        List<String> l = new ArrayList<>();
        double[][] completo = AnalisisAgrupamiento.distancias(corpus, "tfidf");
        int k = SUBCONJUNTO.length;
        double[][] d = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                d[i][j] = completo[corpus.indice(SUBCONJUNTO[i])][corpus.indice(SUBCONJUNTO[j])];
            }
        }
        l.add("Matriz de distancias TF-IDF (1 - coseno) del subconjunto " + java.util.Arrays.toString(SUBCONJUNTO));
        for (int i = 0; i < k; i++) {
            StringBuilder fila = new StringBuilder("A" + SUBCONJUNTO[i]);
            for (int j = 0; j < k; j++) {
                fila.append(String.format(Locale.ROOT, "\t%.4f", d[i][j]));
            }
            l.add(fila.toString());
        }
        for (Criterio criterio : Criterio.values()) {
            l.add("");
            l.add(criterio.nombre() + " (subconjunto):");
            for (Fusion f : AgrupamientoJerarquico.agrupar(d, criterio)) {
                l.add(String.format(Locale.ROOT, "  une %s y %s a distancia %.4f (tamaño %d)", nombre(f.grupoA(), k),
                        nombre(f.grupoB(), k), f.distancia(), f.tamano()));
            }
        }
        for (String representacion : AnalisisAgrupamiento.representaciones(corpus).keySet()) {
            l.add("");
            l.add("Corpus completo, representación " + representacion + ":");
            for (AnalisisAgrupamiento.Resultado r : AnalisisAgrupamiento.analizar(corpus, representacion)) {
                StringBuilder siluetas = new StringBuilder();
                for (int g = AnalisisAgrupamiento.GRUPOS_MINIMOS; g <= AnalisisAgrupamiento.GRUPOS_MAXIMOS; g++) {
                    siluetas.append(String.format(Locale.ROOT, " k=%d:%.4f", g, r.siluetaPorK()[g]));
                }
                l.add(String.format(Locale.ROOT, "  %s: CCC = %.4f; mejor k = %d (silueta %.4f);%s", r.criterio().nombre(),
                        r.correlacionCofenetica(), r.mejorK(), r.mejorSilueta(), siluetas));
            }
        }
        return l;
    }

    private static String nombre(int grupo, int hojas) {
        return grupo < hojas ? "A" + SUBCONJUNTO[grupo] : "G" + (grupo - hojas + 1);
    }

    private static List<String> kmp() {
        List<String> l = new ArrayList<>();
        int[] pi = KnuthMorrisPratt.funcionPrefijos(PATRON);
        l.add("q\t" + numeros(PATRON.length()));
        l.add("P[q]\t" + String.join("\t", PATRON.split("")));
        l.add("π[q]\t" + unir(pi));
        l.add("Texto: \"" + FRASE + "\" (n = " + FRASE.length() + ")");
        String texto = FRASE.toLowerCase(Locale.ROOT);
        int q = 0;
        for (int i = 0; i < texto.length(); i++) {
            while (q > 0 && texto.charAt(i) != PATRON.charAt(q)) {
                l.add(String.format("i=%d '%c' ≠ P[%d]='%c' → q = π[%d] = %d", i, texto.charAt(i), q, PATRON.charAt(q),
                        q - 1, pi[q - 1]));
                q = pi[q - 1];
            }
            if (texto.charAt(i) == PATRON.charAt(q)) {
                q++;
                l.add(String.format("i=%d '%c' = P[%d] → q = %d", i, texto.charAt(i), q - 1, q));
            }
            if (q == PATRON.length()) {
                l.add(String.format("OCURRENCIA en la posición %d; q = π[%d] = %d", i - q + 1, q - 1, pi[q - 1]));
                q = pi[q - 1];
            }
        }
        var r = new KnuthMorrisPratt().buscar(texto, PATRON);
        l.add("Comparaciones = " + r.comparaciones() + ", posiciones = " + r.posiciones());
        return l;
    }

    private static List<String> boyerMoore() {
        List<String> l = new ArrayList<>();
        BoyerMoore.TablaCaracterMalo malo = BoyerMoore.tablaCaracterMalo(PATRON);
        StringBuilder caracteres = new StringBuilder("c");
        StringBuilder ultimas = new StringBuilder("última(c)");
        for (char c : new TreeSet<>(PATRON.chars().mapToObj(x -> (char) x).toList())) {
            caracteres.append('\t').append(c);
            ultimas.append('\t').append(malo.ultima(c));
        }
        l.add(caracteres + "\totro");
        l.add(ultimas + "\t-1");
        int[] sufijo = BoyerMoore.tablaSufijoBueno(PATRON);
        l.add("j\t" + numeros(PATRON.length() + 1));
        l.add("desplazamiento[j]\t" + unir(sufijo));
        String texto = FRASE.toLowerCase(Locale.ROOT);
        int m = PATRON.length();
        int s = 0;
        while (s <= texto.length() - m) {
            int j = m - 1;
            while (j >= 0 && PATRON.charAt(j) == texto.charAt(s + j)) {
                j--;
            }
            if (j < 0) {
                l.add(String.format("s=%d: OCURRENCIA; desplaza desplazamiento[0] = %d", s, sufijo[0]));
                s += sufijo[0];
            } else {
                char c = texto.charAt(s + j);
                int porMalo = j - malo.ultima(c);
                int porSufijo = sufijo[j + 1];
                l.add(String.format("s=%d: falla en j=%d (texto '%c' ≠ patrón '%c', %d coincidencias); carácter malo = %d,"
                        + " sufijo bueno = %d → desplaza %d", s, j, c, PATRON.charAt(j), m - 1 - j, porMalo, porSufijo,
                        Math.max(porMalo, porSufijo)));
                s += Math.max(porMalo, porSufijo);
            }
        }
        var r = new BoyerMoore().buscar(texto, PATRON);
        l.add("Comparaciones = " + r.comparaciones() + ", alineaciones = " + r.ventanas() + ", posiciones = " + r.posiciones());
        return l;
    }

    private static List<String> rabinKarp() {
        List<String> l = new ArrayList<>();
        RabinKarp rk = new RabinKarp(PRIMO_PEQUENO);
        String texto = FRASE.toLowerCase(Locale.ROOT);
        int m = PATRON.length();
        long hp = rk.hash(PATRON, m);
        long peso = rk.pesoMayor(m);
        l.add(String.format("d = %d, q = %d, m = %d, d^(m-1) mod q = %d", RabinKarp.BASE, PRIMO_PEQUENO, m, peso));
        long h = 0;
        StringBuilder horner = new StringBuilder("h(P): ");
        for (int i = 0; i < m; i++) {
            h = (RabinKarp.BASE * h + PATRON.charAt(i)) % PRIMO_PEQUENO;
            horner.append(String.format("'%c'(%d)→%d ", PATRON.charAt(i), (int) PATRON.charAt(i), h));
        }
        l.add(horner.toString());
        l.add("h(P) = " + hp);
        l.add("s\tventana\th(ventana)\tresultado");
        long hv = rk.hash(texto, m);
        for (int s = 0; s <= texto.length() - m; s++) {
            String ventana = texto.substring(s, s + m);
            String resultado = "";
            if (hv == hp) {
                resultado = ventana.equals(PATRON) ? "coincide el hash y el texto: OCURRENCIA" : "colisión (espuria)";
            }
            l.add(s + "\t" + ventana + "\t" + hv + "\t" + resultado);
            if (s < texto.length() - m) {
                hv = rk.rodar(hv, texto.charAt(s), texto.charAt(s + m), peso);
            }
        }
        var r = rk.buscar(texto, PATRON);
        var grande = new RabinKarp().buscar(texto, PATRON);
        l.add(String.format("q = %d: colisiones = %d, comparaciones = %d; q = %d: colisiones = %d, comparaciones = %d",
                PRIMO_PEQUENO, r.colisiones(), r.comparaciones(), RabinKarp.PRIMO, grande.colisiones(),
                grande.comparaciones()));
        return l;
    }

    private static String primeros(double[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < 5 && k < v.length; k++) {
            sb.append(k == 0 ? "" : ", ").append(String.format(Locale.ROOT, "%.4f", v[k]));
        }
        return sb.append("]").toString();
    }

    private static String numeros(int cantidad) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cantidad; i++) {
            sb.append(i == 0 ? "" : "\t").append(i);
        }
        return sb.toString();
    }

    private static String unir(int[] valores) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valores.length; i++) {
            sb.append(i == 0 ? "" : "\t").append(valores[i]);
        }
        return sb.toString();
    }

    private static void escribir(Path archivo, List<String> lineas) throws IOException {
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }
}
