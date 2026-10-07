import { describe, expect, it } from "vitest";

import { isAdminUser } from "./auth";

describe("auth role helpers", () => {
  it("recognizes administrator roles case-insensitively", () => {
    expect(isAdminUser({ role: "ADMIN" })).toBe(true);
    expect(isAdminUser({ role: "admin" })).toBe(true);
  });

  it("does not grant admin access to ordinary or missing roles", () => {
    expect(isAdminUser({ role: "USER" })).toBe(false);
    expect(isAdminUser(null)).toBe(false);
    expect(isAdminUser()).toBe(false);
  });
});
