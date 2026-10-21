package com.barriodigital.ms_requests.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RequestStatusTest {

    @ParameterizedTest
    @CsvSource({
            "INGRESADO, ADMITIDO",
            "INGRESADO, RECHAZADO",
            "ADMITIDO, EN_GESTION",
            "ADMITIDO, RECHAZADO",
            "EN_GESTION, EN_TERRENO",
            "EN_GESTION, RESUELTO",
            "EN_GESTION, RECHAZADO",
            "EN_TERRENO, RESUELTO"
    })
    void permiteLasTransicionesDeLaTabla(RequestStatus from, RequestStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @Test
    void noPermiteNingunaOtraTransicion() {
        long permitidas = 0;
        for (RequestStatus from : RequestStatus.values()) {
            for (RequestStatus to : RequestStatus.values()) {
                if (from.canTransitionTo(to)) {
                    permitidas++;
                }
            }
        }
        // Las 8 de la tabla: cualquier otra combinación (incluida repetir el estado) no es una transición.
        assertThat(permitidas).isEqualTo(8);
    }

    @Test
    void nadaPasaAEnTerrenoSinGestion() {
        assertThat(RequestStatus.INGRESADO.canTransitionTo(RequestStatus.EN_TERRENO)).isFalse();
        assertThat(RequestStatus.ADMITIDO.canTransitionTo(RequestStatus.EN_TERRENO)).isFalse();
        assertThat(RequestStatus.EN_GESTION.canTransitionTo(RequestStatus.EN_TERRENO)).isTrue();
    }

    @Test
    void resueltoYRechazadoSonFinales() {
        assertThat(RequestStatus.RESUELTO.isFinal()).isTrue();
        assertThat(RequestStatus.RECHAZADO.isFinal()).isTrue();
        assertThat(RequestStatus.INGRESADO.isFinal()).isFalse();
        assertThat(RequestStatus.EN_TERRENO.isFinal()).isFalse();
    }

    @Test
    void elNombreDelEstadoDeGestionVaSinTilde() {
        assertThat(RequestStatus.valueOf("EN_GESTION")).isEqualTo(RequestStatus.EN_GESTION);
        assertThat(RequestStatus.values()).extracting(Enum::name).containsExactly(
                "INGRESADO", "ADMITIDO", "EN_GESTION", "EN_TERRENO", "RESUELTO", "RECHAZADO");
    }
}
