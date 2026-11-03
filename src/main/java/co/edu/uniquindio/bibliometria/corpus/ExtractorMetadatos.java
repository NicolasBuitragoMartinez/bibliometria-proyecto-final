package co.edu.uniquindio.bibliometria.corpus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.edu.uniquindio.bibliometria.corpus.LectorPdf.Linea;
import co.edu.uniquindio.bibliometria.corpus.LectorPdf.TextoPdf;

/**
 * Extrae el título, los autores y el año de un artículo. Cada editorial guarda estos datos de forma
 * distinta, así que se combinan tres fuentes y se toma la más completa:
 * 1. Título: el del diccionario Info del PDF si parece un título real; si no (por ejemplo
 *    "ET-04-2023-0117_proof" en Emerald, o vacío en Wiley), las líneas de la primera página
 *    escritas con la letra más grande.
 * 2. Autores: la lista dc:creator de los metadatos XMP (Elsevier y Springer la traen completa),
 *    el campo Author del diccionario Info, o la línea que sigue al título en la primera página.
 *    Se elige la fuente que da más nombres válidos.
 * 3. Año: el de la línea de cita de la revista en la primera página, que cada editorial escribe a su
 *    manera; si no aparece, el del aviso de derechos de autor.
 */
public final class ExtractorMetadatos {

    public record Metadatos(String titulo, List<String> autores, int anio) {
    }

    private static final Pattern CREADOR_XMP = Pattern.compile(
            "<dc:creator>\\s*<rdf:(?:Seq|Bag)>(.*?)</rdf:(?:Seq|Bag)>", Pattern.DOTALL);
    private static final Pattern ELEMENTO_XMP = Pattern.compile("<rdf:li[^>]*>(.*?)</rdf:li>", Pattern.DOTALL);
    private static final Pattern TITULO_NO_VALIDO = Pattern.compile("(?i)proof|\\.indd|untitled|\\.pdf|^\\s*$");
    private static final Pattern SEPARADOR_AUTORES = Pattern.compile("\\s*(?:,|;|\\||\\band\\b|&)\\s*");
    /** Marcas que acompañan a los nombres: números de afiliación, asteriscos, ORCID, etc. */
    private static final Pattern MARCAS_NOMBRE = Pattern.compile("[\\d*†‡§¶✉]+|\\s+(?=\\s)");
    /** Un nombre: de 2 a 5 palabras que empiezan en mayúscula (se admiten iniciales y guiones). */
    private static final Pattern NOMBRE = Pattern.compile(
            "\\p{Lu}[\\p{L}.'’-]*(?:\\s+(?:\\p{Lu}[\\p{L}.'’-]*|de|van|von|da|del)){1,4}");
    /**
     * Línea de cita de la revista, con el año del número: Elsevier y Springer "3 (2022) 100101" o
     * "(2024) 21:40", MDPI "Educ. Sci. 2024, 14, 636.", Wiley "Eur J Educ. 2024;59:e12663" y Emerald
     * "Vol. 66 No. 6, 2024".
     */
    private static final List<Pattern> CITA_REVISTA = List.of(
            Pattern.compile("\\(((?:19|20)\\d{2})\\)\\s*(?:\\d{5,}|\\d+:\\d+)"),
            Pattern.compile("\\b((?:19|20)\\d{2}),\\s*\\d+,\\s*\\d+\\."),
            Pattern.compile("\\b((?:19|20)\\d{2});\\s*\\d+:"),
            Pattern.compile("Vol\\.\\s*\\d+\\s+No\\.\\s*\\d+,\\s*((?:19|20)\\d{2})"));
    /** Último recurso: el año del aviso de derechos de autor ("© 2022 The Authors", "© The Author(s) 2024"). */
    private static final Pattern DERECHOS = Pattern.compile("©[^©\\n]{0,60}?\\b((?:19|20)\\d{2})\\b");
    private static final double PROPORCION_TITULO = 0.92;
    private static final int MAXIMO_LINEAS_TITULO = 5;

    private ExtractorMetadatos() {
    }

    public static Metadatos extraer(TextoPdf pdf) {
        String titulo = extraerTitulo(pdf);
        List<String> autores = mejorLista(List.of(
                autoresXmp(pdf.xmp()),
                separarNombres(pdf.autorInfo()),
                autoresDespuesDelTitulo(pdf.lineasPrimeraPagina(), titulo)));
        StringBuilder primeraPagina = new StringBuilder();
        for (Linea l : pdf.lineasPrimeraPagina()) {
            primeraPagina.append(l.texto()).append('\n');
        }
        return new Metadatos(titulo, autores, anio(primeraPagina.toString(), pdf.primerasPaginas()));
    }

    /**
     * Año de publicación: primero se busca la cita de la revista y, si no aparece, el aviso de derechos de
     * autor, en cada texto en el orden dado (la primera página antes que las siguientes). 0 si no hay año.
     */
    static int anio(String... textos) {
        for (List<Pattern> patrones : List.of(CITA_REVISTA, List.of(DERECHOS))) {
            for (String texto : textos) {
                for (Pattern patron : patrones) {
                    Matcher m = patron.matcher(texto == null ? "" : texto);
                    if (m.find()) {
                        return Integer.parseInt(m.group(1));
                    }
                }
            }
        }
        return 0;
    }

    static String extraerTitulo(TextoPdf pdf) {
        String info = pdf.tituloInfo() == null ? "" : pdf.tituloInfo().strip();
        if (!TITULO_NO_VALIDO.matcher(info).find() && info.split("\\s+").length >= 3) {
            return limpiar(info);
        }
        return tituloPorTamanoDeLetra(pdf.lineasPrimeraPagina());
    }

    /**
     * El título son las palabras escritas con la letra más grande de la primera página, en líneas
     * consecutivas (se ignoran las líneas de una o dos palabras, como el nombre de la sección
     * "REVIEW", y se tolera una línea intermedia en letra pequeña, como el encabezado de margen).
     */
    static String tituloPorTamanoDeLetra(List<Linea> lineas) {
        float mayor = 0;
        for (Linea l : lineas) {
            if (l.textoMayor().split("\s+").length >= 3) {
                mayor = Math.max(mayor, l.tamano());
            }
        }
        StringBuilder titulo = new StringBuilder();
        int tomadas = 0;
        int saltos = 0;
        for (Linea l : lineas) {
            boolean grande = l.tamano() >= mayor * PROPORCION_TITULO;
            if (grande && tomadas < MAXIMO_LINEAS_TITULO) {
                titulo.append(titulo.length() == 0 ? "" : " ").append(l.textoMayor());
                tomadas++;
                saltos = 0;
            } else if (tomadas > 0 && ++saltos > 1) {
                break;
            }
        }
        return limpiar(titulo.toString());
    }

    static List<String> autoresXmp(String xmp) {
        List<String> autores = new ArrayList<>();
        if (xmp == null) {
            return autores;
        }
        Matcher creador = CREADOR_XMP.matcher(xmp);
        if (creador.find()) {
            Matcher elemento = ELEMENTO_XMP.matcher(creador.group(1));
            while (elemento.find()) {
                String nombre = limpiar(elemento.group(1));
                if (esNombre(nombre)) {
                    autores.add(nombre);
                }
            }
        }
        return autores;
    }

    /**
     * Recorre la primera página buscando las líneas que forman el título, en orden (se tolera una
     * línea intermedia ajena, como el encabezado de margen), y separa los nombres de la línea que
     * sigue al título.
     */
    static List<String> autoresDespuesDelTitulo(List<Linea> lineas, String titulo) {
        String objetivo = soloLetras(titulo);
        if (objetivo.isEmpty()) {
            return List.of();
        }
        int posicion = 0;
        boolean salto = false;
        for (int i = 0; i < lineas.size(); i++) {
            String letras = soloLetras(lineas.get(i).textoMayor());
            if (letras.isEmpty()) {
                continue;
            }
            if (posicion > 0 && objetivo.startsWith(letras, posicion)) {
                posicion += letras.length();
                salto = false;
            } else if (posicion > 0 && !salto) {
                salto = true;
                continue;
            } else {
                posicion = objetivo.startsWith(letras) ? letras.length() : 0;
                salto = false;
            }
            if (posicion >= objetivo.length() * 0.95 && i + 1 < lineas.size()) {
                // Los autores pueden ocupar dos líneas; se toma la segunda si la primera termina en "," o "and".
                List<String> autores = separarNombres(lineas.get(i + 1).texto());
                if (i + 2 < lineas.size() && lineas.get(i + 1).texto().strip().matches(".*(?:,|and)$")) {
                    autores.addAll(separarNombres(lineas.get(i + 2).texto()));
                }
                return autores;
            }
        }
        return List.of();
    }

    static List<String> separarNombres(String texto) {
        List<String> nombres = new ArrayList<>();
        if (texto == null) {
            return nombres;
        }
        String sinMarcas = MARCAS_NOMBRE.matcher(limpiar(texto)).replaceAll("");
        for (String parte : SEPARADOR_AUTORES.split(sinMarcas)) {
            String nombre = parte.strip();
            if (esNombre(nombre)) {
                nombres.add(nombre);
            }
        }
        return nombres;
    }

    private static boolean esNombre(String texto) {
        return NOMBRE.matcher(texto).matches() && texto.length() <= 45;
    }

    private static List<String> mejorLista(List<List<String>> candidatas) {
        List<String> mejor = List.of();
        for (List<String> lista : candidatas) {
            if (lista.size() > mejor.size()) {
                mejor = lista;
            }
        }
        return List.copyOf(mejor);
    }

    private static String limpiar(String texto) {
        String t = Normalizer.normalize(texto, Normalizer.Form.NFKC)
                .replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"");
        return t.replaceAll("\\s+", " ").strip();
    }

    private static String soloLetras(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
    }
}
