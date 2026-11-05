import { describe, expect, it } from "vitest";
import { createTenantSession, switchTenant } from "./index";

describe("tenant session", () => {
  it("increments context version so stale responses can be rejected", () => {
    const original = createTenantSession();
    const switched = switchTenant(original, "tenant-b");
    expect(switched.contextVersion).toBe(original.contextVersion + 1);
  });
});
