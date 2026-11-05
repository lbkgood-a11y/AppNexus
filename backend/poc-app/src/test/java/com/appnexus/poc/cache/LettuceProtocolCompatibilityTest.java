package com.appnexus.poc.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("container")
class LettuceProtocolCompatibilityTest {

    @Test
    void supportsTtlAtomicVersionIncrementAndCompareAndDelete() {
        try (GenericContainer<?> server = new GenericContainer<>(
                DockerImageName.parse("mirror.gcr.io/valkey/valkey:8.1-alpine")
                        .asCompatibleSubstituteFor("redis"))
                .withExposedPorts(6379)) {
            server.start();
            RedisClient client = RedisClient.create(
                    "redis://" + server.getHost() + ":" + server.getMappedPort(6379));
            try (var connection = client.connect()) {
                var commands = connection.sync();
                commands.setex("permission:plan", 5, "v1");
                assertThat(commands.get("permission:plan")).isEqualTo("v1");
                assertThat(commands.ttl("permission:plan")).isBetween(1L, 5L);
                assertThat(commands.incr("permission:version")).isEqualTo(1L);
                Long deleted = commands.eval(
                                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                                io.lettuce.core.ScriptOutputType.INTEGER,
                                new String[] {"permission:plan"},
                                "v1");
                assertThat(deleted).isEqualTo(1L);
            } finally {
                client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            }
        }
    }
}
