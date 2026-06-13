package com.sanmoo.eventsourcing.creditaccount.core.projection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionGatingResultTest {

    @Test
    void apply_hasNoReason() {
        var result = ProjectionGatingResult.apply();
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.APPLY);
        assertThat(result.reason()).isNull();
    }

    @Test
    void alreadyApplied_hasNoReason() {
        var result = ProjectionGatingResult.alreadyApplied();
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.ALREADY_APPLIED);
        assertThat(result.reason()).isNull();
    }

    @Test
    void blocked_requiresNonNullReason() {
        assertThatThrownBy(() -> ProjectionGatingResult.blocked(null))
                .isInstanceOf(NullPointerException.class);
        var result = ProjectionGatingResult.blocked("gap detected");
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.BLOCKED);
        assertThat(result.reason()).isEqualTo("gap detected");
    }

    @Test
    void permanentFailure_requiresNonNullReason() {
        assertThatThrownBy(() -> ProjectionGatingResult.permanentFailure(null))
                .isInstanceOf(NullPointerException.class);
        var result = ProjectionGatingResult.permanentFailure("bad version");
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.PERMANENT_FAILURE);
        assertThat(result.reason()).isEqualTo("bad version");
    }
}
