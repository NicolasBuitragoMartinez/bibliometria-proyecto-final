package co.edu.uniquindio.bibliometria.texto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.uniquindio.bibliometria.ranking.ContadorFrecuencias;

import co.edu.uniquindio.bibliometria.ranking.Termino;

class PreprocesadorTest {

    private final Preprocesador preprocesador = new Preprocesador();

    @Test
    void eliminaStopwordsNumerosYSignos() {
        assertEquals(List.of("generative", "ai", "models", "gpt", "produce", "text"),
                preprocesador.tokenizar("Generative AI models, such as GPT-4, can produce text in 2024."));
    }

    @Test
    void normalizaTildesYMayusculas() {
        assertEquals(List.of("inteligencia", "artificial", "generativa", "educacion"),
                preprocesador.tokenizar("La Inteligencia Artificial Generativa en la Educación"));
    }

    @Test
    void cuentaEnOrdenDePrimeraAparicion() {
        Termino[] frecuencias = ContadorFrecuencias.contar(List.of(
                List.of("model", "data", "model"),
                List.of("data", "model", "text")));
        assertArrayEquals(new Termino[] {
                new Termino("model", 3), new Termino("data", 2), new Termino("text", 1)}, frecuencias);
    }
}
