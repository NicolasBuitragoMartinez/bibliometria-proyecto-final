package co.edu.uniquindio.bibliometria.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DelimitadorAbstractTest {

    @Test
    void formatoIeee() {
        String texto = "A Survey of Diffusion Models\nJ. Pérez, A. Gómez\n"
                + "Abstract—Diffusion models generate high-quality im-\nages from noise.\n"
                + "Index Terms—diffusion, generative models\nI. INTRODUCTION\nRecent work...";
        assertEquals("Diffusion models generate high-quality images from noise.", DelimitadorAbstract.extraer(texto));
    }

    @Test
    void formatoElsevierConLetrasEspaciadas() {
        String texto = "A R T I C L E I N F O\nKeywords:\nLLM\nA B S T R A C T\n"
                + "Large language models are evaluated\non clinical text.\n1. Introduction\nThe use of...";
        assertEquals("Large language models are evaluated on clinical text.", DelimitadorAbstract.extraer(texto));
    }

    @Test
    void encabezadoEnLineaPropiaYKeywords() {
        String texto = "Title\nABSTRACT\nWe propose a transformer for code.\nKeywords: code generation\n";
        assertEquals("We propose a transformer for code.", DelimitadorAbstract.extraer(texto));
    }

    @Test
    void prefiereAbstractSobreResumen() {
        String texto = "Resumen: Texto en español.\nPalabras clave: IA\nAbstract: English text.\nKeywords: AI\n";
        assertEquals("English text.", DelimitadorAbstract.extraer(texto));
    }

    @Test
    void elsevierConKeywordsEntreEncabezadoYTexto() {
        String texto = "A R T I C L E I N F O A B S T R A C T\nKeywords:\nHuman-centered AI\nAI in education\n"
                + "The rapid expansion of Learning Analytics and Artificial Intelligence offers new\n"
                + "systems.1. Introduction\nArtificial Intelligence advancements...";
        assertEquals("The rapid expansion of Learning Analytics and Artificial Intelligence offers new systems.",
                DelimitadorAbstract.extraer(texto));
    }

    @Test
    void encabezadoPegadoALineaAnteriorYLigaduras() {
        String texto = "license (http://creativecommons.org/licenses/by-nc-nd/4.0/).A B S T R A C T\n"
                + "We evaluated deﬁ-\nnitions of AIEd.1. Introduction\nArtiﬁcial...";
        assertEquals("We evaluated definitions of AIEd.", DelimitadorAbstract.extraer(texto));
    }

    @Test
    void wileyConKeywordsEspaciado() {
        String texto = "Abstract\nThis critical review investigates teachers' frameworks.\nK E Y W O R D S\nAI\n"
                + "1 | INTRODUC TION\nArtificial...";
        assertEquals("This critical review investigates teachers' frameworks.", DelimitadorAbstract.extraer(texto));
    }

    @Test
    void guionAlFinalDeLinea() {
        assertEquals("AI technologies, AI-driven assessment and AI-driven tools",
                DelimitadorAbstract.limpiar("AI tech-\nnologies, AI-\ndriven assessment and AI-driven tools"));
    }

    @Test
    void compuestoQueApareceConGuionEnElArticulo() {
        String abstractCrudo = "an instance-\nbased approach";
        String articulo = abstractCrudo + "\nWe compare the instance-based method with others.";
        assertEquals("an instance-based approach", DelimitadorAbstract.limpiar(abstractCrudo, articulo,
                DelimitadorAbstract.vocabulario(articulo)));
    }

    @Test
    void guionBlandoAlFinalDeLinea() {
        String texto = DelimitadorAbstract.normalizar("emerging tech­\nnologies in schools");
        assertEquals("emerging technologies in schools", DelimitadorAbstract.limpiar(texto));
    }

    @Test
    void palabraPartidaSinGuionVisible() {
        // El PDF tenía un guion invisible: el extractor entrega "pro" y "grams" en líneas distintas.
        String abstractCrudo = "educational pro\ngrams and learning\nanalytics";
        String articulo = abstractCrudo + "\nthese programs use learning analytics in class";
        assertEquals("educational programs and learning analytics", DelimitadorAbstract.limpiar(abstractCrudo,
                articulo, DelimitadorAbstract.vocabulario(articulo)));
    }

    @Test
    void finPorCambioDeDisenoSinMarcador() {
        String linea = "This line of the abstract spans the whole width of the page and is long";
        String texto = "Abstract\n" + linea + "\n" + linea + "\n" + linea + "\nends here.\n"
                + "Short column line one\nShort column line two\nShort column line three\nmore body";
        assertEquals(linea + " " + linea + " " + linea + " ends here.", DelimitadorAbstract.extraer(texto));
    }

    @Test
    void sinEncabezadoDevuelveVacio() {
        assertEquals("", DelimitadorAbstract.extraer("Un texto sin la sección buscada."));
    }
}
