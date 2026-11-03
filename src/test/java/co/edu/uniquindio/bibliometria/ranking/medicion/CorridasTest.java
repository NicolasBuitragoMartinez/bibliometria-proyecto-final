package co.edu.uniquindio.bibliometria.ranking.medicion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CorridasTest {

    @Test
    void medianaConCantidadParYImpar() {
        assertEquals(2.0, Corridas.mediana(new double[] {3, 1, 2}), 1e-12);
        assertEquals(2.5, Corridas.mediana(new double[] {4, 1, 3, 2}), 1e-12);
    }

    @Test
    void cuartilesConInterpolacionLineal() {
        double[] valores = {1, 2, 3, 4};
        assertEquals(1.75, Corridas.cuartil(valores, 0.25), 1e-12);
        assertEquals(3.25, Corridas.cuartil(valores, 0.75), 1e-12);
        assertEquals(1.0, Corridas.cuartil(valores, 0.0), 1e-12);
        assertEquals(4.0, Corridas.cuartil(valores, 1.0), 1e-12);
    }

    @Test
    void noModificaElArregloRecibido() {
        double[] valores = {5, 1, 4};
        Corridas.cuartil(valores, 0.5);
        assertEquals(5.0, valores[0], 1e-12);
    }
}
