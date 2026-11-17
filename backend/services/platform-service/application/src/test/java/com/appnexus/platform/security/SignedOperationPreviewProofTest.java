package com.appnexus.platform.security;

import static org.assertj.core.api.Assertions.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.*;
import org.junit.jupiter.api.Test;

/** 无数据库签名协议验证：RSA持久身份、跨实例验签、摘要篡改与准确到期边界。 */
class SignedOperationPreviewProofTest {
 @Test void proofBindsPayloadAndExpiresAtBoundaryAcrossInstances() throws Exception {
  var key=new RSAKeyGenerator(2048).keyID("isolated-preview-test").generate();
  JWKSource<SecurityContext> source=(selector,context)->selector.select(new JWKSet(key));
  Instant now=Instant.parse("2026-11-17T00:00:00Z");String digest="a".repeat(64);
  var issuer=new SignedOperationPreviewProof(source,Clock.fixed(now,ZoneOffset.UTC));
  var issued=issuer.issue(digest);
  assertThat(issued.expiresAt()).isEqualTo(now.plusSeconds(900));
  var peer=new SignedOperationPreviewProof(source,Clock.fixed(now.plusSeconds(899),ZoneOffset.UTC));
  assertThat(peer.verify(issued.token(),digest)).isTrue();
  assertThat(peer.verify(issued.token(),"b".repeat(64))).isFalse();
  assertThat(peer.verify(issued.token()+"x",digest)).isFalse();
  assertThat(peer.verify("Bearer "+issued.token(),digest)).isFalse();
  assertThat(new SignedOperationPreviewProof(source,Clock.fixed(now.plusSeconds(900),ZoneOffset.UTC)).verify(issued.token(),digest)).isFalse();
  var rotated=new RSAKeyGenerator(2048).keyID("other-test-key").generate();
  JWKSource<SecurityContext> other=(selector,context)->selector.select(new JWKSet(rotated));
  assertThat(new SignedOperationPreviewProof(other,Clock.fixed(now,ZoneOffset.UTC)).verify(issued.token(),digest)).isFalse();
 }
}
