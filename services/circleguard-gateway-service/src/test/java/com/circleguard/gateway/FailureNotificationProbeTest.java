package com.circleguard.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueba temporal para verificar la notificación de fallo de HU-GATEWAY-04.
 * Se elimina después de capturar la evidencia; no debe fusionarse.
 */
class FailureNotificationProbeTest {

    @Test
    void failsOnPurpose() {
        fail("Fallo intencional para validar la notificación de HU-GATEWAY-04");
    }
}
