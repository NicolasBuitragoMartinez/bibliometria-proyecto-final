package co.edu.uniquindio.bibliometria.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExtractorMetadatosTest {

    @Test
    void anioDeLaCitaDeCadaEditorial() {
        assertEquals(2023, ExtractorMetadatos.anio("Computers and Education: Artificial Intelligence 4 (2023) 100118"));
        assertEquals(2024, ExtractorMetadatos.anio("Xia et al. Int J Educ Technol High Educ           (2024) 21:40"));
        assertEquals(2024, ExtractorMetadatos.anio("Educ. Sci. 2024, 14, 636. https://doi.org/10.3390/educsci14060636"));
        assertEquals(2024, ExtractorMetadatos.anio("Eur J Educ. 2024;59:e12663.      | 1 of 21"));
        assertEquals(2024, ExtractorMetadatos.anio("Education + Training\nVol. 66 No. 6, 2024\npp. 609-645"));
    }

    @Test
    void laCitaPrevaleceSobreLosDerechosDeAutor() {
        // Chiu et al. salió en línea en 2022, pero pertenece al volumen de 2023.
        String pagina = "Available online 20 December 2022\n2666-920X/© 2022 The Authors. Published by Elsevier Ltd.\n"
                + "Computers and Education: Artificial Intelligence 4 (2023) 100118";
        assertEquals(2023, ExtractorMetadatos.anio(pagina));
    }

    @Test
    void sinCitaSeUsanLosDerechosDeAutorYSinNingunoEsCero() {
        assertEquals(2019, ExtractorMetadatos.anio("© The Author(s). 2019 Open Access This article is distributed"));
        assertEquals(0, ExtractorMetadatos.anio("Received 5 April 2023", null));
    }
}
