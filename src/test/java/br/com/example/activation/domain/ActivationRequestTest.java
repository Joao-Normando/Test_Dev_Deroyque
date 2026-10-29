package br.com.example.activation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ActivationRequestTest {
    @Test
    void validTransition() {
        var r = ActivationRequest.create("E", "C", "P", "owner");
        r.markProcessing();
        r.markCompleted();
        assertThat(r.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void rejectsInvalidTransition() {
        var r = ActivationRequest.create("E", "C", "P", "owner");
        assertThatThrownBy(r::markCompleted).isInstanceOf(IllegalStateException.class);
    }
}
