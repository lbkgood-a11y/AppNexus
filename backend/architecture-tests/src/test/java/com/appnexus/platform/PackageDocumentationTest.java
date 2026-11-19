package com.appnexus.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 扫描仓库自有 Java 源包，禁止新增包遗漏中文职责与依赖说明；不把文档覆盖等同于分层完成。 */
class PackageDocumentationTest {
  /**
   * 正式及测试源包均须维护 package-info；扫描源文件而非生成的类，覆盖未装配包。
   * @throws IOException 源码或说明文件不可读取时直接失败，不能静默跳过
   */
  @Test void everySourcePackageDocumentsResponsibilitiesAndBoundaries() throws IOException {
    Path root = backendRoot();
    var directories = new java.util.TreeSet<Path>();
    try (var paths = Files.walk(root)) {
      paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
          .filter(path -> {
            String relative = root.relativize(path).toString().replace('\\', '/');
            return !relative.contains("/target/") && (relative.contains("/src/main/java/")
                || relative.contains("/src/test/java/"));
          }).forEach(path -> directories.add(path.getParent()));
    }
    assertFalse(directories.isEmpty(), "未命中 backend 源码，禁止空扫描通过");
    var failures = new ArrayList<String>();
    for (Path directory : directories) {
      Path description = directory.resolve("package-info.java");
      if (!Files.isRegularFile(description)) { failures.add(root.relativize(directory) + " 缺少 package-info.java"); continue; }
      String content = Files.readString(description);
      if (!Pattern.compile("/\\*\\*.*?职责.*?边界.*?\\*/\\s*package\\s+[\\w.]+;", Pattern.DOTALL)
          .matcher(content).find()) failures.add(root.relativize(description) + " 缺少中文职责或边界 Javadoc");
    }
    assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
  }

  private Path backendRoot() {
    Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (cursor != null) {
      if (Files.isDirectory(cursor.resolve("services")) && Files.isDirectory(cursor.resolve("contracts"))
          && Files.isRegularFile(cursor.resolve("pom.xml"))) return cursor;
      if (Files.isDirectory(cursor.resolve("backend/services"))) return cursor.resolve("backend");
      cursor = cursor.getParent();
    }
    throw new IllegalStateException("找不到 backend 源码根目录");
  }
}
