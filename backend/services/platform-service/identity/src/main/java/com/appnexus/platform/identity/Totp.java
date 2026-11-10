package com.appnexus.platform.identity;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 的 SHA-1 TOTP；固定六位、30 秒步长，密钥与验证码禁止记录日志。 */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp() {}

    /**
     * @param secret 原始随机密钥，建议 20 字节（160 位）
     * @return 无填充的标准 Base32 表示，用于认证器手动绑定
     */
    public static String base32(byte[] secret) {
        StringBuilder result = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : secret) {
            buffer = (buffer << 8) | (value & 255); bits += 8;
            while (bits >= 5) { bits -= 5; result.append(ALPHABET.charAt((buffer >>> bits) & 31)); }
        }
        if (bits > 0) result.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    /**
     * @param secret 原始共享密钥，不接收用户提供的算法或脚本
     * @param counter UTC Unix 秒数除以 30 后的计数器，必须非负
     * @return 固定六位验证码，保留前导零
     */
    public static String code(byte[] secret, long counter) {
        if (counter < 0) throw new IllegalArgumentException("TOTP counter must be non-negative");
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = digest[digest.length - 1] & 15;
            int binary = ((digest[offset] & 127) << 24) | ((digest[offset + 1] & 255) << 16)
                | ((digest[offset + 2] & 255) << 8) | (digest[offset + 3] & 255);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException("TOTP unavailable", impossible); }
    }

    /**
     * @param secret 原始共享密钥
     * @param input 六位数字；其他输入直接拒绝
     * @param counter 当前 30 秒计数器
     * @param previous 已成功消费的最大计数器；初次绑定为 -1
     * @return 匹配且未重放的计数器，失败返回 -1
     */
    public static long match(byte[] secret, String input, long counter, long previous) {
        if (input == null || !input.matches("[0-9]{6}")) return -1;
        long matched = -1;
        for (long step = Math.max(0, counter - 1); step <= counter + 1; step++)
            if (step > previous && MessageDigest.isEqual(code(secret, step).getBytes(StandardCharsets.US_ASCII),
                    input.getBytes(StandardCharsets.US_ASCII))) matched = step;
        return matched;
    }
}
