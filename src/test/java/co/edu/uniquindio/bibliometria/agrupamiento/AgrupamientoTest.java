package co.edu.uniquindio.bibliometria.agrupamiento;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Criterio;
import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;

class AgrupamientoTest {

    /** Cuatro puntos en una recta: 0, 1, 5, 6 (distancia = diferencia absoluta). */
    private static final double[][] D = {
        {0, 1, 5, 6},
        {1, 0, 4, 5},
        {5, 4, 0, 1},
        {6, 5, 1, 0},
    };

    @Test
    void criteriosDeEnlaceSegunLanceWilliams() {
        assertEquals(4.0, AgrupamientoJerarquico.agrupar(D, Criterio.SIMPLE).get(2).distancia(), 1e-12);
        assertEquals(6.0, AgrupamientoJerarquico.agrupar(D, Criterio.COMPLETO).get(2).distancia(), 1e-12);
        assertEquals(5.0, AgrupamientoJerarquico.agrupar(D, Criterio.PROMEDIO).get(2).distancia(), 1e-12);
    }

    @Test
    void fusionesYCorte() {
        List<Fusion> fusiones = AgrupamientoJerarquico.agrupar(D, Criterio.PROMEDIO);
        assertEquals(new Fusion(0, 1, 1.0, 2), fusiones.get(0));
        assertEquals(new Fusion(2, 3, 1.0, 2), fusiones.get(1));
        assertEquals(new Fusion(4, 5, 5.0, 4), fusiones.get(2));
        assertArrayEquals(new int[] {0, 0, 1, 1}, AgrupamientoJerarquico.cortar(4, fusiones, 2));
        int[] cuatro = AgrupamientoJerarquico.cortar(4, fusiones, 4);
        assertEquals(4, java.util.Arrays.stream(cuatro).distinct().count());
    }

    @Test
    void metricasInternas() {
        List<Fusion> fusiones = AgrupamientoJerarquico.agrupar(D, Criterio.PROMEDIO);
        // Distancias originales 1, 5, 6, 4, 5, 1 y cofenéticas 1, 5, 5, 5, 5, 1: r = 21,33 / √(23,33 · 21,33).
        assertEquals(Math.sqrt(64.0 / 70.0), EvaluacionInterna.correlacionCofenetica(D, fusiones), 1e-12);
        // Silueta con grupos {0,1} y {2,3}: a = 1 para todos; b = 5.5, 4.5, 4.5, 5.5.
        double esperado = ((1 - 1 / 5.5) + (1 - 1 / 4.5) + (1 - 1 / 4.5) + (1 - 1 / 5.5)) / 4;
        assertEquals(esperado, EvaluacionInterna.silueta(D, new int[] {0, 0, 1, 1}), 1e-12);
    }

    @Test
    void dendrogramaEsSvgConUnaEtiquetaPorHoja() {
        String svg = Dendrograma.svg(List.of("A1", "A2", "A3", "A4"), AgrupamientoJerarquico.agrupar(D, Criterio.SIMPLE), 2);
        assertTrue(svg.startsWith("<svg"));
        assertEquals(4, svg.split("<text[^>]*text-anchor=\"end\"", -1).length - 1);
    }
}
