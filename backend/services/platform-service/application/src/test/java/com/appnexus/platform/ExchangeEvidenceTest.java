package com.appnexus.platform;

import com.appnexus.platform.localization.ExchangeEvidence;
import java.math.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 历史汇率证据不经过浮点或展示时区，非法汇率与缺少舍入约定必须拒绝。 */
class ExchangeEvidenceTest {
    @Test void evidenceRetainsExactRateAndBusinessDate(){
        var rate=new BigDecimal("7.123456789012345678");var date=LocalDate.of(2026,11,10);
        var evidence=new ExchangeEvidence(UUID.randomUUID(),"USD","CNY",rate,date,"approved-source",Instant.now(),2,RoundingMode.HALF_EVEN);
        assertThat(evidence.rate()).isEqualTo(rate);assertThat(evidence.effectiveDate()).isEqualTo(date);
        assertThatThrownBy(()->new ExchangeEvidence(UUID.randomUUID(),"CNY","CNY",rate,date,"source",Instant.now(),2,RoundingMode.HALF_EVEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ExchangeEvidence(UUID.randomUUID(),"USD","CNY",BigDecimal.ZERO,date,"source",Instant.now(),2,RoundingMode.HALF_EVEN)).isInstanceOf(IllegalArgumentException.class);
    }
}
