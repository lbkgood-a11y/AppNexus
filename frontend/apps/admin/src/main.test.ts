import { describe, expect, it } from "vitest";
import { ORGANIZATION_RESOURCE } from "@appnexus/organization";
import { createTenantSession } from "@appnexus/security";

describe("admin assembly", () => {
  it("uses only workspace public entries", () => {
    expect(ORGANIZATION_RESOURCE).toBe("org.unit");
    expect(createTenantSession().contextVersion).toBe(0);
  });
});
