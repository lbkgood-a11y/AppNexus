import { expect, it } from "vitest"; import { TENANT_MODULE } from "./index"; it("exports module id", () => expect(TENANT_MODULE).toBe("tenant"));
