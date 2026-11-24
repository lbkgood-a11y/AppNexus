package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.configuration.AttachmentFileType;
import com.appnexus.platform.configuration.port.AttachmentInspection;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 调用部署侧私网 clamd INSTREAM 安全扫描，拒绝未明确返回 OK 的内容。 */
@Component
final class AttachmentScanClient implements AttachmentInspection {
    private final String host;
    private final int port;

    AttachmentScanClient(Environment environment) {
        host = environment.getProperty("app.attachment.scan.host", "");
        port = environment.getProperty("app.attachment.scan.port", Integer.class, 3310);
    }

    /** {@inheritDoc} */
    @Override
    public void verify(AttachmentFileType type, byte[] content) {
        try {
            AttachmentContent.verify(type, content);
        } catch (IOException failure) {
            throw new IllegalArgumentException("附件内容无法解析", failure);
        }
        try {
            scan(content);
        } catch (IOException failure) {
            throw new IllegalStateException("附件安全扫描暂不可用", failure);
        }
    }

    /**
     * 扫描已受应用大小上限限制的文件。服务不可用、超限或异常响应时拒绝上传。
     * @param content 原始附件内容，最多 20 MiB
     * @throws IOException 扫描服务不可用且文件未能验证
     * @throws IllegalArgumentException 扫描明确检出恶意内容
     */
    private void scan(byte[] content) throws IOException {
        if (host.isBlank()) throw new IOException("附件扫描未配置");
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(10000);
            // Socket 读超时不限制写阻塞，额外截止时间覆盖扫描节点停止消费的故障。
            java.util.concurrent.CompletableFuture.delayedExecutor(20, java.util.concurrent.TimeUnit.SECONDS)
                    .execute(() -> {
                        if (!socket.isClosed()) {
                            try {
                                socket.close();
                            } catch (IOException ignored) {
                                // 请求线程仍会执行自身关闭流程。
                            }
                        }
                    });
            var out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int start = 0; start < content.length; start += 8192) {
                int length = Math.min(8192, content.length - start);
                out.writeInt(length);
                out.write(content, start, length);
            }
            out.writeInt(0);
            out.flush();
            var in = socket.getInputStream();
            var reply = new StringBuilder();
            for (int i = 0; i < 256; i++) {
                int next = in.read();
                if (next == 0) {
                    String response = reply.toString();
                    if (response.equals("stream: OK")) return;
                    if (response.startsWith("stream: ") && response.endsWith(" FOUND")) {
                        throw new IllegalArgumentException("附件未通过安全检查");
                    }
                    break;
                }
                if (next < 0) break;
                reply.append((char) next);
            }
            throw new IOException("附件扫描响应无效");
        }
    }
}
