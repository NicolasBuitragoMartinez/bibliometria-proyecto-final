package co.edu.uniquindio.bibliometria.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import co.edu.uniquindio.bibliometria.corpus.ExtractorMetadatos.Metadatos;
import co.edu.uniquindio.bibliometria.corpus.LectorPdf.TextoPdf;
import co.edu.uniquindio.bibliometria.modelo.Articulo;

/**
 * Carga los artículos de la carpeta {@code pdfs}. Lo extraído de cada PDF se guarda en archivos de
 * texto que el programa reutiliza en las ejecuciones siguientes (así el servidor arranca rápido):
 * {@code abstracts/<archivo>.txt} con el abstract y {@code metadatos/<archivo>.txt} con el título en
 * la primera línea, los autores, separados por "; ", en la segunda y el año en la tercera. Si se
 * borran, se vuelven a generar a partir de los PDF.
 */
public final class CargadorCorpus {

    private static final Pattern NUMERO_INICIAL = Pattern.compile("^(\\d{1,9})");
    private static final String SEPARADOR_AUTORES = "; ";

    private CargadorCorpus() {
    }

    public static List<Articulo> cargar(Path carpetaCorpus) throws IOException {
        Path carpetaPdfs = carpetaCorpus.resolve("pdfs");
        Path carpetaAbstracts = carpetaCorpus.resolve("abstracts");
        Path carpetaMetadatos = carpetaCorpus.resolve("metadatos");
        Files.createDirectories(carpetaAbstracts);
        Files.createDirectories(carpetaMetadatos);

        List<Path> pdfs;
        try (Stream<Path> archivos = Files.list(carpetaPdfs)) {
            pdfs = archivos.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.comparingInt(CargadorCorpus::numeroInicial)
                            .thenComparing(p -> p.getFileName().toString()))
                    .toList();
        }

        List<Articulo> articulos = new ArrayList<>();
        for (Path pdf : pdfs) {
            String nombre = pdf.getFileName().toString();
            String base = nombre.substring(0, nombre.length() - 4);
            Path txtAbstract = carpetaAbstracts.resolve(base + ".txt");
            Path txtMetadatos = carpetaMetadatos.resolve(base + ".txt");

            TextoPdf texto = null;
            if (!Files.exists(txtAbstract)) {
                texto = LectorPdf.leer(pdf);
                Files.writeString(txtAbstract, DelimitadorAbstract.extraer(texto.primerasPaginas(), texto.completo()),
                        StandardCharsets.UTF_8);
            }
            List<String> lineas = Files.exists(txtMetadatos)
                    ? Files.readAllLines(txtMetadatos, StandardCharsets.UTF_8) : List.of();
            if (lineas.size() < 3) {
                // Falta el archivo, o es de una versión anterior que no guardaba el año: se completa sin
                // cambiar el título y los autores que ya tenía, que pueden haberse revisado a mano.
                Metadatos m = ExtractorMetadatos.extraer(texto != null ? texto : LectorPdf.leer(pdf));
                lineas = List.of(lineas.size() > 0 ? lineas.get(0) : m.titulo(),
                        lineas.size() > 1 ? lineas.get(1) : String.join(SEPARADOR_AUTORES, m.autores()),
                        String.valueOf(m.anio()));
                Files.writeString(txtMetadatos, String.join("\n", lineas) + "\n", StandardCharsets.UTF_8);
            }

            String abstractTexto = Files.readString(txtAbstract, StandardCharsets.UTF_8).strip();
            String titulo = lineas.get(0).isBlank() ? base : lineas.get(0).strip();
            List<String> autores = lineas.get(1).isBlank() ? List.of()
                    : Arrays.stream(lineas.get(1).split(SEPARADOR_AUTORES)).map(String::strip).toList();
            int anio = lineas.get(2).strip().matches("\\d{4}") ? Integer.parseInt(lineas.get(2).strip()) : 0;
            articulos.add(new Articulo(numeroInicial(pdf), nombre, titulo, autores, anio, abstractTexto));
        }
        return articulos;
    }

    /** Número con el que empieza el nombre del archivo ("12. A meta..." → 12), para el orden 1..20. */
    private static int numeroInicial(Path archivo) {
        Matcher numero = NUMERO_INICIAL.matcher(archivo.getFileName().toString());
        return numero.find() ? Integer.parseInt(numero.group(1)) : Integer.MAX_VALUE;
    }
}
