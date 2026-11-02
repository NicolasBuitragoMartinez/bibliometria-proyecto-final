package co.edu.uniquindio.bibliometria.similitud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import co.edu.uniquindio.bibliometria.similitud.NeedlemanWunsch.Alineamiento;

class SimilitudTest {

    @Test
    void levenshteinEnCasosConocidos() {
        assertEquals(3, Levenshtein.distancia("kitten", "sitting"));
        assertEquals(0, Levenshtein.distancia("education", "education"));
        assertEquals(9, Levenshtein.distancia("", "education"));
        int[][] d = Levenshtein.matriz("generative", "emergence");
        assertEquals(Levenshtein.distancia("generative", "emergence"), d[10][9]);
        assertEquals(1.0, Levenshtein.similitud("ai", "ai"), 1e-12);
    }

    @Test
    void needlemanWunschAlineaConHuecos() {
        Alineamiento a = NeedlemanWunsch.alinear(List.of("generative", "artificial", "intelligence"),
                List.of("emergence", "generative", "artificial", "intelligence"));
        assertEquals(List.of("-", "generative", "artificial", "intelligence"), a.a());
        assertEquals(3, a.coincidencias());
        assertEquals(2, a.puntaje()); // 3 coincidencias - 1 hueco
        assertEquals(0.75, a.identidad(), 1e-12);
    }

    @Test
    void jaccardIgnoraRepeticionesYOrden() {
        assertEquals(1.0 / 3, Jaccard.similitud(List.of("ai", "ai", "education"), List.of("ai", "learning")), 1e-12);
        assertEquals(1.0, Jaccard.similitud(List.of("a", "b"), List.of("b", "a")), 1e-12);
    }

    @Test
    void cosenoDeVectoresDispersosYDensos() {
        Map<String, Double> a = Map.of("x", 1.0, "y", 1.0);
        Map<String, Double> b = Map.of("x", 1.0);
        assertEquals(1 / Math.sqrt(2), CosenoTfIdf.coseno(a, b), 1e-12);
        assertEquals(0.0, CosenoTfIdf.coseno(Map.of("x", 1.0), Map.of("y", 1.0)), 1e-12);
        assertEquals(1.0, CosenoEmbeddings.coseno(new double[] {1, 2, 3}, new double[] {2, 4, 6}), 1e-12);
        assertEquals(5.0, CosenoEmbeddings.distanciaEuclidiana(new double[] {0, 0}, new double[] {3, 4}), 1e-12);
    }
}
