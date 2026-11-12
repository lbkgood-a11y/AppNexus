import { test } from "node:test";
import assert from "node:assert/strict";
import { chromium } from "playwright-core";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { renderPdf, type ClaimedJob } from "./render.js";
import { outboundFixture } from "./outbound.fixture.js";

/** 浏览器集成验证显式使用部署指定的 Chromium；未设置时不声称完成渲染验收。 */
test("真实 Chromium 生成 A4 PDF 并清理渲染上下文", { skip: !process.env.PRINT_TEST_CHROMIUM_PATH }, async () => {
  const browser = await chromium.launch({ executablePath: process.env.PRINT_TEST_CHROMIUM_PATH, headless: true });
  const directory = await mkdtemp(join(tmpdir(), "appnexus-render-test-"));
  try {
    const job: ClaimedJob = {
      id: "test", copies: 1, data_snapshot: {}, system_name: "unused-test-queue",
      template_json: { panels: [{ index: 0, width: 210, height: 297, paperType: "A4", printElements: [] }] }
    };
    const file = join(directory, "test.pdf");
    await renderPdf(browser, job, file);
    const pdf = await readFile(file);
    assert.equal(pdf.subarray(0, 5).toString(), "%PDF-");
    // 检查实际 PDF 页面尺寸，不能以 DOM 中的纸张宽度替代输出验收。
    const mediaBox = pdf.toString("latin1").match(/\/MediaBox\s*\[\s*0\s+0\s+([\d.]+)\s+([\d.]+)\s*\]/);
    assert.ok(mediaBox, "PDF 必须包含页面尺寸");
    assert.ok(Math.abs(Number(mediaBox[1]) - 595.28) < 2, `A4 宽度异常：${mediaBox[1]}`);
    assert.ok(Math.abs(Number(mediaBox[2]) - 841.89) < 2, `A4 高度异常：${mediaBox[2]}`);
    const outbound=outboundFixture();
    const outboundFile=join(directory,"outbound.pdf");
    await renderPdf(browser,{...job,template_json:outbound.template,data_snapshot:outbound.data},outboundFile);
    const outboundPdf=await readFile(outboundFile);
    assert.ok(outboundPdf.length>pdf.length+1000,"出仓单不能退化为空白页面");
    assert.equal(browser.contexts().length, 0);
    await assert.rejects(renderPdf(browser, job, join(directory, "timeout.pdf"), 1), /PRINT_RENDER_TIMEOUT/);
    assert.equal(browser.contexts().length, 0);
  } finally {
    await browser.close();
    await rm(directory, { recursive: true, force: true });
  }
});
