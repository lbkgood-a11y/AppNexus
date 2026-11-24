package com.appnexus.platform.persistence.configuration;

import com.obs.services.ObsClient;
import com.appnexus.platform.configuration.port.AttachmentBlobStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** 文件启用后按可信租户选桶，禁止使用其他租户或部署共享凭据兜底。 */
@Component
final class AttachmentObjectStorage implements AutoCloseable, AttachmentBlobStorage {
    private final Map<UUID, Connection> tenants = new HashMap<>();
    private final Connection legacy;

    AttachmentObjectStorage(Environment env) throws IOException {
        String file = env.getProperty("app.attachment.storage.tenant-file", "");
        if (file.isBlank()) {
            legacy = new Connection(key -> env.getProperty("app.attachment.storage." + key, ""), false);
            return;
        }
        legacy = null;
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        try {
            for (String key : properties.stringPropertyNames()) {
                if (!key.matches("tenant\\.[0-9a-f-]{36}\\.(provider|bucket|endpoint|region|access-key|secret-key)")) {
                    throw new IllegalArgumentException("租户附件存储配置项无效");
                }
                UUID tenant = UUID.fromString(key.split("\\.")[1]);
                tenants.computeIfAbsent(tenant, id -> new Connection(setting -> {
                    String raw = properties.getProperty("tenant." + id + "." + setting, "");
                    // 文件模式直接读取受部署权限保护的秘密，避免依赖启动进程中的凭据变量。
                    if (setting.endsWith("-key") && raw.startsWith("${")) {
                        throw new IllegalArgumentException("租户附件存储文件须填写实际凭据");
                    }
                    return raw;
                }, true));
            }
        } catch (RuntimeException failure) {
            try { close(); } catch (IOException closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String writePath(UUID tenantId, String attachmentId) {
        Connection connection = legacy == null ? tenants.get(tenantId) : legacy;
        if (connection == null || !connection.configured()) {
            throw new IllegalStateException("当前租户未配置附件存储");
        }
        return "attachments/" + tenantId + "/"
                + (legacy == null ? connection.fingerprint + "/" : "") + attachmentId;
    }

    /** {@inheritDoc} */
    @Override
    public void put(String path, byte[] data, String contentType) {
        String[] parts = path.split("/");
        if (!"attachments".equals(parts[0])) throw new IllegalStateException("附件对象路径无效");
        Connection connection;
        if (legacy != null && parts.length == 3) {
            connection = legacy;
        } else if (parts.length == 4) {
            connection = tenants.get(UUID.fromString(parts[1]));
            if (connection == null || !connection.fingerprint.equals(parts[2])) {
                throw new IllegalStateException("附件原存储配置不可用");
            }
        } else {
            throw new IllegalStateException("附件原存储配置不可用");
        }
        connection.put(path, data, contentType);
    }

    /** 关闭已装配的厂商客户端。 */
    @Override
    public void close() throws IOException {
        if (legacy != null) legacy.close();
        IOException failure = null;
        for (Connection connection : tenants.values()) {
            try {
                connection.close();
            } catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private static final class Connection implements AutoCloseable {
        private final String bucket;
        private final String fingerprint;
        private final S3Client s3;
        private final ObsClient obs;

        Connection(Function<String, String> values, boolean tenantSpecific) {
            String provider = tenantSpecific ? required(values, "provider") : values.apply("provider");
            bucket = provider.isBlank() ? "" : required(values, "bucket");
            String endpoint = values.apply("endpoint");
            if (!endpoint.isBlank()) {
                URI uri = URI.create(endpoint);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                    throw new IllegalArgumentException("附件存储端点必须使用有效的 HTTPS 地址");
                }
            }
            fingerprint = fingerprint(provider, bucket, endpoint, values.apply("region"));
            if ("obs".equals(provider)) {
                if (endpoint.isBlank()) throw new IllegalArgumentException("OBS 必须配置端点");
                var config = new com.obs.services.ObsConfiguration();
                config.setEndPoint(endpoint);
                config.setConnectionTimeout(5000);
                config.setSocketTimeout(30000);
                obs = new ObsClient(required(values, "access-key"),
                        required(values, "secret-key"), config);
                s3 = null;
            } else {
                obs = null;
                s3 = switch (provider) {
                    case "" -> null;
                    case "s3", "oss" -> newS3(values, provider, endpoint, tenantSpecific);
                    default -> throw new IllegalArgumentException("不支持的附件存储服务");
                };
            }
        }

        private static S3Client newS3(Function<String, String> values, String provider, String endpoint, boolean tenantSpecific) {
            boolean oss = "oss".equals(provider);
            var builder = S3Client.builder()
                    .region(Region.of(required(values, "region")))
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallAttemptTimeout(Duration.ofSeconds(20))
                            .apiCallTimeout(Duration.ofSeconds(45)).build());
            if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
            if (oss || tenantSpecific) {
                if (oss && endpoint.isBlank()) throw new IllegalArgumentException("OSS 必须配置 S3 兼容端点");
                builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        required(values, "access-key"),
                        required(values, "secret-key"))));
            }
            if (oss) {
                builder.serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).build());
                builder.requestChecksumCalculation(software.amazon.awssdk.core.checksums.RequestChecksumCalculation.WHEN_REQUIRED);
            }
            return builder.build();
        }

        private static String required(Function<String, String> values, String name) {
            String value = values.apply(name);
            if (value.isBlank()) throw new IllegalArgumentException("缺少附件存储配置：" + name);
            return value;
        }

        /** @return 部署已配置可用客户端，实际连通性由写入结果确定 */
        boolean configured() {
            return s3 != null || obs != null;
        }

        /** 写入经过安全检查的原件，使用固定对象路径以保证重试不会生成第二份。 */
        void put(String path, byte[] data, String contentType) {
            if (s3 != null) {
                s3.putObject(builder -> builder.bucket(bucket).key(path).contentType(contentType),
                        RequestBody.fromBytes(data));
            } else if (obs != null) {
                var metadata = new com.obs.services.model.ObjectMetadata();
                metadata.setContentLength((long) data.length);
                metadata.setContentType(contentType);
                obs.putObject(bucket, path, new ByteArrayInputStream(data), metadata);
            } else {
                throw new IllegalStateException("附件对象存储未配置");
            }
        }

        /** 容器停止时关闭底层连接池。 */
        @Override
        public void close() throws IOException {
            if (s3 != null) s3.close();
            if (obs != null) obs.close();
        }

        private static String fingerprint(String provider, String bucket, String endpoint, String region) {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(
                        String.join("\0", provider, bucket, endpoint, region).getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException("附件存储标识无法生成", failure);
            }
        }
    }
}
