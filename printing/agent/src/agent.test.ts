import { test } from "node:test";
import assert from "node:assert/strict";
import { readConfig } from "./config.js";
import { QueueFailure, queueCommand, runQueue } from "./queue.js";
import { parsePrinters } from "./printers.js";

/** 仅测试用环境；不使用真实身份、设备或平台地址。 */
function environment(): NodeJS.ProcessEnv {
  return {
    PRINT_API_URL: "https://platform.invalid/",
    PRINT_TOKEN_URL: "https://identity.invalid/oauth2/token",
    PRINT_SITE_CODE: "office", PRINT_AGENT_ID: "agent-test",
    PRINT_CLIENT_ID: "test-client", PRINT_CLIENT_SECRET: "test-placeholder",
    PRINT_CHROMIUM_PATH: process.execPath, PRINT_SUMATRA_PATH: process.execPath
  };
}

test("启动配置保留默认值且不在导入时读取真实凭据", () => {
  const config = readConfig(environment());
  assert.equal(config.api, "https://platform.invalid");
  assert.equal(config.pollMs, 3000);
  assert.equal(config.tenantVersion, "1");
});

test("拒绝嵌入凭据、查询参数和非 HTTP 协议", () => {
  for (const name of ["PRINT_API_URL", "PRINT_TOKEN_URL"]) {
    for (const value of ["https://user:private@platform.invalid", "https://platform.invalid/?token=private", "file:///tmp/test", "https://platform.invalid/#fragment"]) {
      assert.throws(() => readConfig({ ...environment(), [name]: value }), { message: `Invalid ${name}` });
    }
  }
});

test("支持本机 HTTP 平台和 Token 地址",()=>{const value=readConfig({...environment(),PRINT_API_URL:"http://localhost:8080",PRINT_TOKEN_URL:"http://localhost:8080/oauth2/token"});assert.equal(value.api,"http://localhost:8080")});
test("拒绝非数字、小数或越界轮询参数以及无效租户版本", () => {
  for (const value of ["NaN", "Infinity", "1000.5", "999", "60001", ""]) {
    assert.throws(() => readConfig({ ...environment(), PRINT_POLL_INTERVAL_MS: value }));
  }
  for (const value of ["0", "-1", "1.5", "9007199254740992", ""]) {
    assert.throws(() => readConfig({ ...environment(), PRINT_TENANT_CONTEXT_VERSION: value }));
  }
});

test("Windows 启动前要求打印程序绝对路径", () => {
  const env = environment();
  delete env.PRINT_SUMATRA_PATH;
  assert.throws(() => readConfig(env, "win32"), { message: "Invalid PRINT_SUMATRA_PATH" });
  assert.throws(() => readConfig({ ...environment(), PRINT_CHROMIUM_PATH: "chromium" }));
});

test("队列参数以独立参数传入，不由 shell 求值", async () => {
  await runQueue(process.execPath, ["-e", "if(process.argv[1] !== 'printer; exit 7')process.exit(9)", "printer; exit 7"]);
});

test("不存在的打印程序确定未提交", async () => {
  await assert.rejects(runQueue(`${process.execPath}.missing`, []), error => error instanceof QueueFailure && !error.uncertain);
});

test("队列程序异常退出不能保证没有出纸", async () => {
  await assert.rejects(runQueue(process.execPath, ["-e", "process.exit(2)"]), error => error instanceof QueueFailure && error.uncertain);
});

test("队列程序超时终止并返回结果未知", async () => {
  await assert.rejects(runQueue(process.execPath, ["-e", "setInterval(()=>{},1000)"], 300), error => error instanceof QueueFailure && error.uncertain);
});

test("Windows、Linux 和 macOS 均使用客户端明确选择的队列", () => {
  for (const platform of ["linux", "darwin"] as const) {
    assert.deepEqual(queueCommand(platform, "/tmp/outbound.pdf", "warehouse-printer", 2), {
      command: "lp", args: ["-d", "warehouse-printer", "-n", "2", "--", "/tmp/outbound.pdf"]
    });
  }
  assert.deepEqual(queueCommand("win32", "C:\\outbound.pdf", "出仓打印机", 2, "C:\\SumatraPDF.exe"), {
    command: "C:\\SumatraPDF.exe", args: ["-silent", "-print-to", "出仓打印机", "-print-settings", "2x", "C:\\outbound.pdf"]
  });
  assert.throws(() => queueCommand("linux", "/tmp/test.pdf", "", 1));
  assert.throws(() => queueCommand("linux", "/tmp/test.pdf", "printer", 0));
});

test("客户端队列发现保留中文和空格并解析 CUPS 输出", () => {
  assert.deepEqual(parsePrinters('["出仓 打印机","PDF","PDF"]',"win32"), [{name:"PDF"},{name:"出仓 打印机"}]);
  for(const platform of ["linux","darwin"] as const) {
    assert.deepEqual(parsePrinters("printer warehouse is idle. enabled since today\nprinter label disabled since today\n",platform),[{name:"label"},{name:"warehouse"}]);
  }
  assert.deepEqual(parsePrinters("[]","win32"),[]);
  assert.throws(()=>parsePrinters('[{"Name":"unexpected"}]',"win32"));
});
