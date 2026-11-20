/**
 * 枚举全部生产 Java 数据访问和 Web 入口，提供人工逐调用链复核的起点。
 * 文本线索不能证明权限已接通；任何条目缺少验证证据时不得据此宣称全局通过。
 * 在仓库根目录运行 node scripts/audit-data-access.mjs；输出固定到 docs/validation，不读取凭据或连接数据库。
 */
import { readdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = fileURLToPath(new URL("../", import.meta.url));
/**
 * 屏蔽Java注释并保留行号；字符串、字符和文本块内的注释符号不作为注释处理。
 * @param {string} source 原始Java源码
 * @returns {string} 与原源码等长的代码，用于避免将Javadoc里的JdbcTemplate说明误计为实际引用
 */
function javaCode(source) {
  const result = source.split("");
  let quote = null;
  for (let index = 0; index < source.length; index++) {
    if (quote) {
      if (quote === '"""' && source.startsWith('"""', index)) { quote = null; index += 2; }
      else if (quote !== '"""' && source[index] === "\\") index++;
      else if (quote !== '"""' && source[index] === quote) quote = null;
      continue;
    }
    if (source.startsWith('"""', index)) { quote = '"""'; index += 2; continue; }
    if (source[index] === '"' || source[index] === "'") { quote = source[index]; continue; }
    if (source.startsWith("//", index) || source.startsWith("/*", index)) {
      const line = source.startsWith("//", index);
      let end = line ? source.indexOf("\n", index + 2) : source.indexOf("*/", index + 2);
      end = end < 0 ? source.length : end + (line ? 0 : 2);
      for (let cursor = index; cursor < end; cursor++) if (source[cursor] !== "\n" && source[cursor] !== "\r") result[cursor] = " ";
      index = end - 1;
    }
  }
  return result.join("");
}
/**
 * 遍历真实源码，跳过所有 Maven 产物和依赖目录。
 * @param {string} directory 仓库内部目录绝对路径
 * @returns {Promise<string[]>} 自有正式 Java 源文件
 */
async function sources(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (entry.name.startsWith("target") || entry.name === "node_modules" || entry.name === ".git") continue;
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await sources(absolute));
    else if (entry.name.endsWith(".java") && absolute.replaceAll("\\", "/").includes("/src/main/java/")) files.push(absolute);
  }
  return files;
}

const files = [];
const webEntrypoints = [];
const legacyOperationBindings = [];
for (const absolute of (await sources(path.join(root, "backend"))).sort()) {
  const source = javaCode(await readFile(absolute, "utf8"));
  const relative = path.relative(root, absolute).replaceAll("\\", "/");
  // 独立Controller操作声明仍未接入唯一Provider；跨行匹配，不把只写了操作注解视为完整对象声明。
  for (const match of source.matchAll(/@(?:[\w.]+\.)?OperationBinding\s*\(([^)]*)\)/gs)) {
    const annotation = match[1];
    const objectType = annotation.match(/\bobjectType\s*=\s*"([^"]+)"/)?.[1];
    if (!objectType || /\breference\s*=\s*true\b/.test(annotation)) continue;
    legacyOperationBindings.push({ path: relative,
      line: source.slice(0, match.index).split("\n").length,
      objectType, operationId: annotation.match(/\bid\s*=\s*"([^"]+)"/)?.[1] ?? "",
      status: "未验证：Controller独立声明仍须迁入唯一Provider并核验实际执行，不能靠登记关闭缺口" });
  }
  if (/JdbcTemplate|@Mapper\b|java\.sql\.Connection|getConnection\(/.test(source)) {
    files.push({
      path: relative,
      jdbcTemplate: /\b(?:NamedParameter)?JdbcTemplate\b/.test(source),
      mapper: /@Mapper\b/.test(source),
      historicalMigration: relative.includes("/db/migration/"),
      accessPlanning: /\bAccessPlanning\b|planning\.plan\(/.test(source),
      rowExecution: /\bRowAccessControl\b/.test(source),
      fieldProtection: /\bFieldProtection\b/.test(source),
      functionalAuthorization: /\bAccessAuthorization\b|@EndpointOperation\b/.test(source),
      status: "未验证：文本线索，须沿真实入口、上层用例和事务核对"
    });
  }
  // 从入口反向枚举，包含完全未声明 Provider 的旧 Controller；不只扫描已有数据权限目录。
  if (/@(?:RestController|Controller)\b/.test(source)) {
    const endpoints = source.split(/\r?\n/).flatMap((line, index) =>
      /@(Get|Post|Put|Delete|Patch|Request)Mapping\b/.test(line)
        ? [{ line: index + 1, declaration: line.trim() }] : []);
    webEntrypoints.push({ path: relative, endpoints,
      status: "未验证：逐接口关联唯一对象、操作、管理域、准入、行范围、字段、执行器与正反向证据；方法注解可跨行，不能按单行认定缺失" });
  }
}
const result = {
  scope: "全部自有生产 Java；历史迁移及底层连接单列，不构成豁免；入口含未登记旧 Controller",
  summary: { databaseFiles: files.length, jdbcTemplateFiles: files.filter(file => file.jdbcTemplate).length,
    mapperFiles: files.filter(file => file.mapper).length, controllerFiles: webEntrypoints.length,
    legacyOperationBindings: legacyOperationBindings.length },
  files, webEntrypoints, legacyOperationBindings
};
await writeFile(path.join(root, "docs/validation/全局数据权限与MyBatis迁移清单.json"), JSON.stringify(result, null, 2) + "\n", "utf8");
console.log(JSON.stringify(result.summary));
// 严格完成检查没有存量白名单；未迁移代码及独立操作声明仍存在时明确失败，不自动承认历史豁免。
if (process.argv.includes("--strict") && (result.summary.jdbcTemplateFiles || legacyOperationBindings.length)) {
  console.error("未完成全仓迁移或唯一声明接入；查看生成清单逐条整改。静态清单不替代真实越权验证。");
  process.exitCode = 1;
}
