package br.com.example.activation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ActivationRequestTest {
    @Test
    void validTransition() {
        var r = ActivationRequest.create("E", "C", "P", "owner");
        r.changeStatusTo(ActivationStatus.PROCESSING);
        r.changeStatusTo(ActivationStatus.COMPLETED);
        assertThat(r.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void rejectsInvalidTransition() {
        var r = ActivationRequest.create("E", "C", "P", "owner");
        assertThatThrownBy(() -> r.changeStatusTo(ActivationStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
    }
}
