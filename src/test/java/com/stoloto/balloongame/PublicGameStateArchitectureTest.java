package com.stoloto.balloongame;

import com.stoloto.balloongame.api.dto.PublicGameState;
import com.stoloto.balloongame.api.dto.WsGameEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14 — public WS DTO must not declare PF secret fields (I1 by construction).
 */
class PublicGameStateArchitectureTest {

    private static final Set<String> FORBIDDEN = Set.of(
            "crashPoint", "serverSeed", "server_seed", "clientSeed", "crash_point");

    @Test
    void publicGameState_hasNoSecretFields() {
        Set<String> names = Arrays.stream(PublicGameState.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertThat(names).doesNotContainAnyElementsOf(FORBIDDEN);
    }

    @Test
    void wsGameEvent_onlyTypeAndPublicState() {
        Set<String> names = Arrays.stream(WsGameEvent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("type", "state");
        assertThat(WsGameEvent.class.getRecordComponents()[1].getType())
                .isEqualTo(PublicGameState.class);
    }
}
