package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.InboundSiteTelemetrySource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** 读取可信采集程序在私有目录原子落盘的车次快照。 */
public final class DirectoryInboundSiteTelemetry implements InboundSiteTelemetrySource {
    private static final int MAX_BYTES=262144;
    private final Path directory;
    private final boolean simulated;
    private final JsonMapper json=JsonMapper.builder().build();

    /** @param directory 专用绝对目录，空字符串关闭接入 @param simulated 是否显示模拟标识 */
    public DirectoryInboundSiteTelemetry(String directory,boolean simulated) {
        this.directory=directory.isBlank()?null:Path.of(directory).normalize();
        if(this.directory!=null&&!this.directory.isAbsolute())throw new IllegalArgumentException("现场采集目录必须为绝对路径");
        this.simulated=simulated;
    }
    /** {@inheritDoc} */
    @Override public boolean simulated(){return simulated;}

    /** {@inheritDoc} */
    @Override public Optional<Reading> latest(UUID tenant,UUID task,boolean allowCapture) {
        if(directory==null)return Optional.empty();
        Path parent=directory.resolve(tenant.toString());
        Path file=parent.resolve(task+".json");
        if(Files.isSymbolicLink(parent)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return Optional.empty();
        try(var stream=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){
            if(!parent.toRealPath().equals(directory.toRealPath().resolve(tenant.toString())))return Optional.empty();
            byte[] body=stream.readNBytes(MAX_BYTES+1);
            if(body.length>MAX_BYTES)return Optional.empty();
            var node=json.readTree(body);
            if(!tenant.toString().equals(node.path("tenantId").asText())
                    || !task.toString().equals(node.path("taskId").asText()))return Optional.empty();
            String station=node.path("station").asText(),kg=node.path("kg").asText();
            if(station.isBlank()||station.length()>64||!kg.matches("[0-9]{1,14}(\\.[0-9]{1,4})?")
                    || !node.path("stable").isBoolean())return Optional.empty();
            var timestamp=Instant.parse(node.path("observedAt").asText());
            return Optional.of(new Reading(station,kg,node.path("stable").asBoolean(),timestamp,
                    allowCapture?png(node.path("capturePng").asText()):null));
        }catch(IOException|RuntimeException failure){return Optional.empty();}
    }

    private static String png(String data) {
        if(data.isBlank())return null;
        try{
            byte[] bytes=Base64.getDecoder().decode(data);
            byte[] signature={(byte)137,80,78,71,13,10,26,10};
            if(bytes.length<24 || !java.util.Arrays.equals(bytes,0,8,signature,0,8))return null;
            var header=java.nio.ByteBuffer.wrap(bytes,16,8);
            int w=header.getInt(),h=header.getInt();
            if(w<1||h<1||w>1600||h>1200)return null;
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes);
        }catch(IllegalArgumentException invalid){return null;}
    }
}
