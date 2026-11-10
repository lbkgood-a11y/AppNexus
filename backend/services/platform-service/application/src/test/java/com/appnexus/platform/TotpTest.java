package com.appnexus.platform;

import com.appnexus.platform.identity.Totp;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 使用 RFC 6238 公布的测试向量验证互操作性，而非复制算法计算预期结果。 */
class TotpTest {
    private final byte[] secret="12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    @Test void publishedSha1VectorsMatchSixDigitProjection(){
        long[] seconds={59,1111111109,1111111111,1234567890,2000000000,20000000000L};
        String[] expected={"287082","081804","050471","005924","279037","353130"};
        for(int index=0;index<seconds.length;index++)assertThat(Totp.code(secret,seconds[index]/30)).isEqualTo(expected[index]);
        assertThat(Totp.base32(secret)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }
    @Test void replayAndInvalidInputsAreRejected(){
        assertThat(Totp.match(secret,"287082",1,-1)).isEqualTo(1);
        assertThat(Totp.match(secret,"287082",1,1)).isEqualTo(-1);
        assertThat(Totp.match(secret,null,1,-1)).isEqualTo(-1);
        assertThat(Totp.match(secret,"2870820",1,-1)).isEqualTo(-1);
        assertThat(Totp.match(secret,"287082",5,-1)).isEqualTo(-1);
    }
}
