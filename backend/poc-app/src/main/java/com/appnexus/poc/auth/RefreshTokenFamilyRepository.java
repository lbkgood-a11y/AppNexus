package com.appnexus.poc.auth;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenFamilyRepository {
    Optional<RefreshTokenFamily> findByFamilyId(UUID familyId);
    void save(RefreshTokenFamily family);
    boolean rotateIfCurrent(UUID familyId, String expectedHash, RefreshTokenFamily replacement);
    void revoke(UUID familyId);
}
