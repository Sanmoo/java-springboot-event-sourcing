package com.sanmoo.eventsourcing.creditaccount.adapter.out.uuid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generateReturnsUuidVersionSeven() {
        var generator = new UuidV7Generator();

        var uuid = generator.generate();

        assertThat(uuid.version()).isEqualTo(7);
    }
}
