package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.configuration.AttachmentFileType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSName;

/** 受限格式识别。在扫描前拒绝伪装格式、主动内容及超限文档。 */
final class AttachmentContent {
    private static final Set<String> PDF_ACTIVE_KEYS = Set.of(
            "JS", "JavaScript", "OpenAction", "AA", "Launch", "EmbeddedFiles", "RichMedia", "XFA", "SubmitForm");
    private static final Set<String> PDF_ACTIVE_ACTIONS = Set.of(
            "JavaScript", "Launch", "SubmitForm", "ImportData", "GoToR", "GoToE", "Rendition", "RichMediaExecute");
    private static final int MAX_ARCHIVE_BYTES = 100 * 1_048_576;

    private AttachmentContent() {}

    /** @param content 已通过原始字节上限检查的内容 */
    static void verify(AttachmentFileType type, byte[] content) throws IOException {
        switch (type) {
            case JPEG, PNG -> verifyImage(type, content);
            case PDF -> verifyPdf(content);
            case DOCX, XLSX, PPTX -> verifyOffice(type, content);
            case TXT, CSV -> {
                var decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content));
                if (decoded.chars().anyMatch(c -> Character.isISOControl(c)
                        && c != 9 && c != 10 && c != 13)) reject();
            }
        }
    }

    private static void verifyImage(AttachmentFileType type, byte[] content) throws IOException {
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) reject();
            var reader = readers.next();
            try {
                if (!type.name().equalsIgnoreCase(reader.getFormatName())) reject();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 16_000_000) reject();
                if (reader.read(0) == null) reject();
            } finally {
                reader.dispose();
            }
        }
    }

    private static void verifyPdf(byte[] content) throws IOException {
        if (content.length < 5 || !new String(content, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) reject();
        try (var pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 1000) reject();
            var document = pdf.getDocument();
            var seen = java.util.Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
            if (document.getXrefTable().size() > 100_000) reject();
            for (var key : document.getXrefTable().keySet()) {
                verifyPdfObjects(document.getObjectFromPool(key), seen);
            }
        }
    }

    private static void verifyPdfObjects(COSBase root, Set<COSBase> seen) {
        var queue = new ArrayDeque<COSBase>();
        if (root != null) queue.add(root);
        while (!queue.isEmpty()) {
            var object = queue.removeLast();
            if (!seen.add(object)) continue;
            if (seen.size() > 200_000) reject();
            if (object instanceof COSObject reference && reference.getObject() != null) {
                queue.add(reference.getObject());
            } else if (object instanceof COSDictionary dictionary) {
                if (dictionary.keySet().stream().map(COSName::getName).anyMatch(PDF_ACTIVE_KEYS::contains)
                        || PDF_ACTIVE_ACTIONS.contains(dictionary.getNameAsString(COSName.S, ""))) reject();
                for (var child : dictionary.getValues()) {
                    if (child != null) queue.add(child);
                }
            } else if (object instanceof COSArray array) {
                for (var child : array) {
                    if (child != null) queue.add(child);
                }
            }
        }
    }

    private static void verifyOffice(AttachmentFileType type, byte[] content) throws IOException {
        String main = switch (type) {
            case DOCX -> "word/document.xml";
            case XLSX -> "xl/workbook.xml";
            case PPTX -> "ppt/presentation.xml";
            default -> throw new IllegalArgumentException("无对应办公文档类型");
        };
        String mainType = type.mediaType().replaceFirst("document$", "document.main+xml")
                .replaceFirst("sheet$", "sheet.main+xml").replaceFirst("presentation$", "presentation.main+xml");
        String packageType = "";
        var names = new HashSet<String>();
        int total = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!names.add(lower) || names.size() > 2048 || name.startsWith("/") || name.contains("..")
                        || name.indexOf(92) >= 0 || lower.contains("vbaproject") || lower.contains("/embeddings/")
                        || lower.contains("/activex/") || lower.contains("/externallinks/")) reject();
                boolean inspect = name.equals("[Content_Types].xml") || lower.endsWith(".rels");
                var xml = inspect ? new java.io.ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                for (int read; (read = zip.read(buffer)) != -1;) {
                    total += read;
                    if (total > MAX_ARCHIVE_BYTES) reject();
                    if (xml != null) {
                        if (xml.size() + read > 1_048_576) reject();
                        xml.write(buffer, 0, read);
                    }
                }
                if (xml != null) {
                    if (name.equals("[Content_Types].xml")) {
                        packageType = verifyPackageManifest(xml.toByteArray(), main);
                    } else {
                        verifyRelationships(xml.toByteArray());
                    }
                }
            }
        }
        if (!names.contains(main) || !mainType.equals(packageType) || !names.contains("_rels/.rels")) reject();
    }

    private static String verifyPackageManifest(byte[] content, String main) throws IOException {
        var root = parseXml(content);
        var nodes = root.getElementsByTagNameNS("*", "Override");
        String type = "";
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = (org.w3c.dom.Element) nodes.item(i);
            String contentType = node.getAttribute("ContentType");
            verifyOfficeMediaType(contentType);
            if (node.getAttribute("PartName").equals("/" + main)) type = contentType;
        }
        var defaults = root.getElementsByTagNameNS("*", "Default");
        for (int i = 0; i < defaults.getLength(); i++) {
            verifyOfficeMediaType(((org.w3c.dom.Element) defaults.item(i)).getAttribute("ContentType"));
        }
        return type;
    }

    private static void verifyOfficeMediaType(String mediaType) {
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        if (normalized.contains("macroenabled") || normalized.contains("vbaproject")
                || normalized.contains("activex") || normalized.endsWith("oleobject")) reject();
    }

    private static void verifyRelationships(byte[] content) throws IOException {
        var nodes = parseXml(content).getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = (org.w3c.dom.Element) nodes.item(i);
            if ("External".equalsIgnoreCase(node.getAttribute("TargetMode"))) reject();
        }
    }

    private static org.w3c.dom.Element parseXml(byte[] content) throws IOException {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content)).getDocumentElement();
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException error) {
            throw new IOException("办公文档元数据无效", error);
        }
    }

    private static void reject() {
        throw new IllegalArgumentException("附件内容不符合声明格式或安全策略");
    }
}
