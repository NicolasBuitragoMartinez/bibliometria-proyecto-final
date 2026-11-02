package co.edu.uniquindio.bibliometria.corpus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ubica el abstract dentro del texto plano de un artículo: empieza después del encabezado
 * "Abstract" (o "Resumen" si no hay abstract en inglés) y termina en el primer marcador
 * que suele seguirle (Keywords, Index Terms, Introduction, ©, ...). Contempla los formatos
 * con letras espaciadas de Elsevier y Wiley ("A B S T R A C T", "K E Y W O R D S").
 * Si no hay marcador de cierre, el fin se detecta por el cambio de diseño de la página
 * (el abstract ocupa todo el ancho y el cuerpo del artículo va en columnas).
 */
public final class DelimitadorAbstract {

    /** "Abstract" al inicio de línea, o "A B S T R A C T" en cualquier posición. */
    private static final Pattern INICIO_ABSTRACT = Pattern.compile(
            "(?im)(?:^[ \\t]*abstract\\b|a[ \\t]+b[ \\t]+s[ \\t]+t[ \\t]+r[ \\t]+a[ \\t]+c[ \\t]+t)"
                    + "[ \\t.:\\u2014\\u2013-]*");

    private static final Pattern INICIO_RESUMEN = Pattern.compile(
            "(?im)^[ \\t]*resumen\\b[ \\t.:\\u2014\\u2013-]*");

    /** Bloque de palabras clave que en algunos formatos aparece entre el encabezado y el texto. */
    private static final Pattern BLOQUE_KEYWORDS = Pattern.compile("(?i)^\\s*(?:keywords|key\\s*words)\\b");
    private static final int LARGO_MAXIMO_LINEA_KEYWORD = 60;

    private static final Pattern FIN = Pattern.compile(
            "(?im)"
                    // Marcadores al inicio de línea.
                    + "^[ \\t]*(?:keywords|key\\s*words|key\\s+terms|index\\s+terms|palabras\\s+clave"
                    + "|ccs\\s+concepts|acm\\s+reference\\s+format|highlights|copyright|\\u00a9"
                    + "|(?:(?:1|i)\\.?\\s+)?introduc\\s?(?:tion|ci[oó]n)\\b)"
                    // Marcadores con letras espaciadas, en cualquier posición.
                    + "|k[ \\t]+e[ \\t]+y[ \\t]+w[ \\t]+o[ \\t]+r[ \\t]+d[ \\t]+s"
                    + "|a[ \\t]+r[ \\t]+t[ \\t]+i[ \\t]+c[ \\t]+l[ \\t]+e[ \\t]+i[ \\t]+n[ \\t]+f[ \\t]+o"
                    // "...theories.1. Introduction": el extractor pegó el título de la sección al abstract.
                    + "|(?<=[.)])[ \\t]*(?:1|i)\\.?[ \\t]+introduc\\s?tion\\b"
                    + "|(?<=[.)])[ \\t]*(?:keywords|key\\s*words)\\b");

    /** Final de línea entre dos palabras, con o sin guion: "tech-\nnologies", "pro\ngrams". */
    private static final Pattern CORTE_DE_LINEA = Pattern.compile("(\\p{L}+)(-?)[ \\t]*\\R[ \\t]*(?=(\\p{L}+))");
    private static final Pattern GUION_BLANDO_FIN_DE_LINEA = Pattern.compile("\\u00AD[ \\t]*(\\R)");
    private static final Pattern PALABRA = Pattern.compile("\\p{L}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    /** Cambio de diseño: tras el abstract vienen al menos 3 líneas más cortas que el 80 % de las del abstract. */
    private static final int LINEAS_PARA_CONFIRMAR_CAMBIO = 3;
    private static final double PROPORCION_LINEA_CORTA = 0.8;

    /** Límite de seguridad cuando no se encuentra el final. */
    public static final int MAX_PALABRAS = 450;

    private DelimitadorAbstract() {
    }

    /** Variante sin el texto completo del artículo: usa las mismas páginas como vocabulario. */
    public static String extraer(String textoArticulo) {
        return extraer(textoArticulo, textoArticulo);
    }

    /**
     * Devuelve el abstract en un solo párrafo, o cadena vacía si no hay encabezado reconocible.
     *
     * @param primerasPaginas texto de las páginas donde está el abstract
     * @param textoCompleto   texto de todo el artículo, usado como vocabulario
     */
    public static String extraer(String primerasPaginas, String textoCompleto) {
        String texto = normalizar(primerasPaginas);
        Matcher inicio = INICIO_ABSTRACT.matcher(texto);
        if (!inicio.find()) {
            inicio = INICIO_RESUMEN.matcher(texto);
            if (!inicio.find()) {
                return "";
            }
        }
        String resto = saltarBloqueKeywords(texto.substring(inicio.end()));
        Matcher fin = FIN.matcher(resto);
        String abstractCrudo = fin.find() ? resto.substring(0, fin.start()) : cortarPorCambioDeDiseno(resto);
        String completo = normalizar(textoCompleto);
        return recortar(limpiar(abstractCrudo, completo, vocabulario(completo)));
    }

    /**
     * NFKC convierte ligaduras tipográficas (ﬁ, ﬂ, ﬀ) en letras normales. Los guiones tipográficos
     * pasan a "-"; el guion blando (U+00AD) al final de línea cuenta como guion y en otra posición
     * se elimina.
     */
    static String normalizar(String texto) {
        String t = Normalizer.normalize(texto, Normalizer.Form.NFKC).replace('‐', '-').replace('‑', '-');
        t = GUION_BLANDO_FIN_DE_LINEA.matcher(t).replaceAll("-$1");
        return t.replace("­", "");
    }

    /** Si tras el encabezado vienen las palabras clave (líneas cortas sin punto final), las omite. */
    private static String saltarBloqueKeywords(String texto) {
        if (!BLOQUE_KEYWORDS.matcher(texto).find()) {
            return texto;
        }
        String[] lineas = texto.strip().split("\\R", -1);
        int i = 1;
        while (i < lineas.length) {
            String linea = lineas[i].strip();
            if (linea.length() >= LARGO_MAXIMO_LINEA_KEYWORD || linea.endsWith(".")) {
                break;
            }
            i++;
        }
        return String.join("\n", Arrays.copyOfRange(lineas, i, lineas.length));
    }

    /**
     * Cuando no hay marcador de cierre (artículo 9 del corpus), el abstract termina en la primera
     * línea que acaba en punto y va seguida de varias líneas claramente más cortas: el abstract
     * ocupa todo el ancho de la página y el cuerpo del artículo empieza a dos columnas.
     */
    static String cortarPorCambioDeDiseno(String texto) {
        String[] lineas = texto.split("\\R", -1);
        List<Integer> largos = new ArrayList<>();
        for (String linea : lineas) {
            if (!linea.isBlank() && largos.size() < 5) {
                largos.add(linea.strip().length());
            }
        }
        if (largos.isEmpty()) {
            return texto;
        }
        largos.sort(null);
        double limite = largos.get(largos.size() / 2) * PROPORCION_LINEA_CORTA;
        for (int i = 0; i < lineas.length; i++) {
            if (!lineas[i].strip().endsWith(".")) {
                continue;
            }
            int cortas = 0;
            int revisadas = 0;
            for (int j = i + 1; j < lineas.length && revisadas < LINEAS_PARA_CONFIRMAR_CAMBIO; j++) {
                if (lineas[j].isBlank()) {
                    continue;
                }
                revisadas++;
                if (lineas[j].strip().length() < limite) {
                    cortas++;
                }
            }
            if (revisadas == LINEAS_PARA_CONFIRMAR_CAMBIO && cortas == LINEAS_PARA_CONFIRMAR_CAMBIO) {
                return String.join("\n", Arrays.copyOfRange(lineas, 0, i + 1));
            }
        }
        return texto;
    }

    /** Palabras que aparecen en medio de una línea (ni al principio ni al final) en todo el artículo. */
    static Set<String> vocabulario(String texto) {
        Set<String> palabras = new HashSet<>();
        for (String linea : texto.split("\\R")) {
            List<String> enLinea = new ArrayList<>();
            Matcher m = PALABRA.matcher(linea);
            while (m.find()) {
                enLinea.add(m.group().toLowerCase(Locale.ROOT));
            }
            for (int i = 1; i < enLinea.size() - 1; i++) {
                palabras.add(enLinea.get(i));
            }
        }
        return palabras;
    }

    /**
     * Repara las palabras partidas al final de línea y deja el abstract en un solo párrafo.
     * - Con guion ("tech-\nnologies"): se une, salvo que la parte izquierda sea una sigla
     *   ("AI-driven") o que la forma con guion aparezca en otra parte del artículo ("instance-based").
     * - Sin guion ("pro\ngrams", cuando el PDF usa un guion invisible): se une solo si la palabra
     *   unida existe en el artículo y las dos partes no son, a la vez, palabras del artículo.
     */
    static String limpiar(String texto, String textoCompleto, Set<String> vocabulario) {
        Matcher corte = CORTE_DE_LINEA.matcher(texto);
        StringBuilder unido = new StringBuilder();
        while (corte.find()) {
            String izquierda = corte.group(1);
            String derecha = corte.group(3);
            String reemplazo;
            if (!corte.group(2).isEmpty()) {
                boolean compuesta = esSigla(izquierda) || textoCompleto.contains(izquierda + "-" + derecha);
                reemplazo = compuesta ? izquierda + "-" : izquierda;
            } else {
                String izq = izquierda.toLowerCase(Locale.ROOT);
                String der = derecha.toLowerCase(Locale.ROOT);
                boolean partida = Character.isLowerCase(derecha.charAt(0))
                        && vocabulario.contains(izq + der)
                        && !(vocabulario.contains(izq) && vocabulario.contains(der));
                reemplazo = partida ? izquierda : izquierda + " ";
            }
            corte.appendReplacement(unido, Matcher.quoteReplacement(reemplazo));
        }
        corte.appendTail(unido);
        return ESPACIOS.matcher(unido).replaceAll(" ").strip();
    }

    /** Variante usada por las pruebas: el propio texto es el vocabulario. */
    static String limpiar(String texto) {
        return limpiar(texto, texto, vocabulario(texto));
    }

    private static boolean esSigla(String palabra) {
        return palabra.length() >= 2 && palabra.chars().allMatch(Character::isUpperCase);
    }

    private static String recortar(String texto) {
        String[] palabras = texto.split(" ");
        if (palabras.length <= MAX_PALABRAS) {
            return texto;
        }
        return String.join(" ", Arrays.copyOf(palabras, MAX_PALABRAS));
    }
}
