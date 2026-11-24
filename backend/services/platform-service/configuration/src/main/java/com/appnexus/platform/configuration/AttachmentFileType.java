package com.appnexus.platform.configuration;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** 发布期附件类型目录，限定管理员可配置的格式和大小。内容检测由上传用例执行。 */
public enum AttachmentFileType {
    JPEG("image/jpeg", 10, "jpg", "jpeg"),
    PNG("image/png", 10, "png"),
    PDF("application/pdf", 20, "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", 20, "docx"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 20, "xlsx"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", 20, "pptx"),
    TXT("text/plain", 10, "txt"),
    CSV("text/csv", 10, "csv");

    private final String mediaType;
    private final long hardLimitBytes;
    private final Set<String> extensions;

    AttachmentFileType(String mediaType, long hardLimitMiB, String... extensions) {
        this.mediaType = mediaType;
        this.hardLimitBytes = hardLimitMiB * 1_048_576;
        this.extensions = Set.of(extensions);
    }

    /** @return 与数据库策略记录对应的稳定小写键 */
    public String typeKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return 内容验证通过后用于记录的标准媒体类型 */
    public String mediaType() {
        return mediaType;
    }

    /** @return 允许配置的最大原始文件字节数 */
    public long hardLimitBytes() {
        return hardLimitBytes;
    }

    /** @return 允许使用的扩展名，不含前导点 */
    public Set<String> extensions() {
        return extensions;
    }

    /**
     * 校验管理员设置的上限，避免配置突破当前格式的安全边界。
     * @param maximumBytes 原始文件字节上限，至少为 1，不得超过格式硬上限
     * @throws IllegalArgumentException 上限超出可配置范围
     */
    public void validateLimit(long maximumBytes) {
        if (maximumBytes < 1 || maximumBytes > hardLimitBytes) {
            throw new IllegalArgumentException("附件大小上限超出允许范围");
        }
    }

    /**
     * 由展示文件名选择内容检测器，扩展名本身不证明文件内容可信。
     * @param fileName 原始展示文件名，最多 255 个 Unicode 码点，不得包含路径或控制字符
     * @return 文件扩展名对应的类型
     * @throws IllegalArgumentException 文件名非法或格式不在目录中
     */
    public static AttachmentFileType fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.equals(fileName.strip())
                || fileName.codePointCount(0, fileName.length()) > 255
                || fileName.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT
                    || Character.getType(c) == Character.SURROGATE
                    || c == '/' || c == 92)) {
            throw new IllegalArgumentException("附件名称不合法");
        }
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 1 || extensionStart == fileName.length() - 1) {
            throw new IllegalArgumentException("附件名称缺少扩展名");
        }
        String extension = fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.extensions.contains(extension))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("不支持该附件格式"));
    }
}
