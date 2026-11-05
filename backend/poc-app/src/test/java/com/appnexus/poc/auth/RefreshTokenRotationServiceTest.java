package com.appnexus.poc.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenRotationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    @Test
    void rotatesOnceAndReplayRevokesTheWholeDeviceFamily() {
        InMemoryRepository repository = new InMemoryRepository();
        UUID familyId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        repository.save(new RefreshTokenFamily(
                familyId, sessionId, RefreshTokenRotationService.hash("token-1"), 1,
                RefreshTokenFamily.Status.ACTIVE, NOW.plusSeconds(3600)));
        RefreshTokenRotationService service = new RefreshTokenRotationService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.rotate(familyId, "token-1", "token-2").generation()).isEqualTo(2);
        assertThat(repository.findByFamilyId(familyId).orElseThrow().currentTokenHash())
                .isEqualTo(RefreshTokenRotationService.hash("token-2"));

        assertThatThrownBy(() -> service.rotate(familyId, "token-1", "token-3"))
                .isInstanceOf(RefreshTokenRotationService.RefreshTokenReplayDetected.class);
        assertThat(repository.findByFamilyId(familyId).orElseThrow().status())
                .isEqualTo(RefreshTokenFamily.Status.REVOKED);
        assertThatThrownBy(() -> service.rotate(familyId, "token-2", "token-4"))
                .isInstanceOf(RefreshTokenRotationService.InvalidRefreshToken.class);
    }

    private static final class InMemoryRepository implements RefreshTokenFamilyRepository {
        private final Map<UUID, RefreshTokenFamily> families = new HashMap<>();
        public Optional<RefreshTokenFamily> findByFamilyId(UUID familyId) {
            return Optional.ofNullable(families.get(familyId));
        }
        public void save(RefreshTokenFamily family) { families.put(family.familyId(), family); }
        public boolean rotateIfCurrent(UUID familyId, String expectedHash, RefreshTokenFamily replacement) {
            return families.computeIfPresent(familyId, (id, current) ->
                    current.currentTokenHash().equals(expectedHash) && current.status() == RefreshTokenFamily.Status.ACTIVE
                            ? replacement : current) == replacement;
        }
        public void revoke(UUID familyId) {
            families.computeIfPresent(familyId, (id, family) -> new RefreshTokenFamily(
                    family.familyId(), family.sessionId(), family.currentTokenHash(), family.generation(),
                    RefreshTokenFamily.Status.REVOKED, family.expiresAt()));
        }
    }
}
