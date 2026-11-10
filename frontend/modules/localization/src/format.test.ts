import { expect, it } from "vitest";
import { formatInstant, formatMoney } from "./index";

it("preserves decimal precision beyond floating point and original currency", () => {
  expect(formatMoney("9007199254740993.123456789012345678", "CNY", "zh")).toBe("CNY 9,007,199,254,740,993.123456789012345678");
  expect(formatMoney("-0.10", "USD", "en")).toBe("USD -0.10");
  expect(formatMoney("1e5", "CNY", "zh")).toBe("—");
  expect(formatMoney("100", "invalid", "en")).toBe("—");
});
it("time-zone formatting never shifts a business date without instant evidence", () => {
  expect(formatInstant("2026-11-10", "zh", "Asia/Shanghai")).toBe("—");
  expect(formatInstant("invalid", "en", "UTC")).toBe("—");
  const instant = "2026-11-10T00:00:00Z";
  expect(formatInstant(instant, "en", "Invalid/Zone")).toBe(formatInstant(instant, "en", "UTC"));
  expect(formatInstant(instant, "zh", "Asia/Shanghai")).not.toBe(formatInstant(instant, "zh", "UTC"));
});
