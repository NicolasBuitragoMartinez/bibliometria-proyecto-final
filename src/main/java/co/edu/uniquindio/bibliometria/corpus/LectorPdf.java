package co.edu.uniquindio.bibliometria.corpus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Extrae de un PDF, con Apache PDFBox, todo lo que necesita el corpus:
 * - el texto de las primeras páginas (donde está el abstract) y el del artículo completo, que se usa
 *   como vocabulario para unir las palabras partidas al final de línea;
 * - las líneas de la primera página con el tamaño de su letra, para ubicar el título y los autores;
 * - los metadatos del documento (diccionario Info y XMP), que en muchas editoriales traen título y autores.
 */
public final class LectorPdf {

    private static final int PAGINAS_DEL_ABSTRACT = 3;

    /**
     * Una línea de texto de la primera página, el tamaño de letra más grande que aparece en ella y
     * solo las palabras escritas con ese tamaño (en Emerald el encabezado de margen, en letra pequeña,
     * queda en la misma línea que el título).
     */
    public record Linea(String texto, float tamano, String textoMayor) {
    }

    public record TextoPdf(String primerasPaginas, String completo, List<Linea> lineasPrimeraPagina,
            String tituloInfo, String autorInfo, String xmp) {
    }

    private LectorPdf() {
    }

    public static TextoPdf leer(Path pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper inicio = new PDFTextStripper();
            inicio.setStartPage(1);
            inicio.setEndPage(Math.min(PAGINAS_DEL_ABSTRACT, documento.getNumberOfPages()));
            String primerasPaginas = inicio.getText(documento);
            String completo = new PDFTextStripper().getText(documento);

            LectorDeLineas lineas = new LectorDeLineas();
            lineas.setStartPage(1);
            lineas.setEndPage(1);
            lineas.setSortByPosition(true);
            lineas.getText(documento);

            PDDocumentInformation info = documento.getDocumentInformation();
            return new TextoPdf(primerasPaginas, completo, lineas.lineas, info.getTitle(), info.getAuthor(),
                    leerXmp(documento));
        }
    }

    private static String leerXmp(PDDocument documento) throws IOException {
        PDMetadata metadatos = documento.getDocumentCatalog().getMetadata();
        if (metadatos == null) {
            return "";
        }
        try (InputStream entrada = metadatos.exportXMPMetadata()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Arma las líneas de la página a partir de las palabras que escribe PDFBox y guarda para cada
     * una el tamaño de letra más grande de sus caracteres.
     */
    private static final class LectorDeLineas extends PDFTextStripper {

        private final List<Linea> lineas = new ArrayList<>();
        private final StringBuilder actual = new StringBuilder();
        private final List<String> fragmentos = new ArrayList<>();
        private final List<Float> tamanos = new ArrayList<>();
        private float tamanoActual;

        LectorDeLineas() throws IOException {
            super();
        }

        @Override
        protected void writeString(String texto, List<TextPosition> posiciones) throws IOException {
            float tamano = 0;
            for (TextPosition p : posiciones) {
                tamano = Math.max(tamano, p.getFontSizeInPt());
            }
            tamanoActual = Math.max(tamanoActual, tamano);
            fragmentos.add(texto);
            tamanos.add(tamano);
            actual.append(texto);
            super.writeString(texto, posiciones);
        }

        @Override
        protected void writeWordSeparator() throws IOException {
            actual.append(' ');
            super.writeWordSeparator();
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            cerrarLinea();
            super.writeLineSeparator();
        }

        @Override
        protected void endPage(PDPage pagina) throws IOException {
            cerrarLinea();
            super.endPage(pagina);
        }

        private void cerrarLinea() {
            if (!actual.toString().isBlank()) {
                StringBuilder mayor = new StringBuilder();
                for (int i = 0; i < fragmentos.size(); i++) {
                    if (tamanos.get(i) >= tamanoActual * 0.99f) {
                        mayor.append(mayor.length() == 0 ? "" : " ").append(fragmentos.get(i).strip());
                    }
                }
                lineas.add(new Linea(actual.toString().strip(), tamanoActual, mayor.toString()));
            }
            actual.setLength(0);
            fragmentos.clear();
            tamanos.clear();
            tamanoActual = 0;
        }
    }
}
