package co.edu.uniquindio.bibliometria.texto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Preprocesamiento del abstract:
 * 1) minúsculas, eliminación de tildes y de ligaduras tipográficas, 2) tokenización por caracteres que no son letras
 * (descarta números y signos), 3) eliminación de stopwords en inglés y español y de
 * tokens de una sola letra.
 */
public final class Preprocesador {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern NO_LETRAS = Pattern.compile("[^a-z]+");
    private static final int LONGITUD_MINIMA = 2;

    private final Set<String> stopwords;

    public Preprocesador() {
        this.stopwords = new HashSet<>();
        stopwords.addAll(leerRecurso("/stopwords/en.txt"));
        stopwords.addAll(leerRecurso("/stopwords/es.txt"));
    }

    public List<String> tokenizar(String texto) {
        List<String> tokens = new ArrayList<>();
        for (String token : NO_LETRAS.split(normalizar(texto))) {
            if (token.length() >= LONGITUD_MINIMA && !stopwords.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** NFKD separa las tildes de su letra y descompone ligaduras (ﬁ → fi); luego se quitan las tildes. */
    private static String normalizar(String texto) {
        String descompuesto = Normalizer.normalize(texto, Normalizer.Form.NFKD);
        return MARCAS_DIACRITICAS.matcher(descompuesto).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static List<String> leerRecurso(String ruta) {
        try (InputStream entrada = Preprocesador.class.getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró el recurso " + ruta);
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            return lector.lines()
                    .map(String::strip)
                    .filter(linea -> !linea.isEmpty() && !linea.startsWith("#"))
                    .map(Preprocesador::normalizar)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
