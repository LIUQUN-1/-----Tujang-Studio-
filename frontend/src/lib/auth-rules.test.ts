import { describe, expect, it } from "vitest";

import { validateAuthForm, validatePassword, validateUsername } from "@/lib/auth-rules";

describe("validateUsername", () => {
  it("接受 3~24 位字母数字下划线", () => {
    expect(validateUsername("abc")).toBeNull();
    expect(validateUsername("a_b_1")).toBeNull();
    expect(validateUsername("a".repeat(24))).toBeNull();
  });

  it("拒绝太短、太长、含非法字符", () => {
    expect(validateUsername("ab")).toBe("3~24 位字母、数字或下划线");
    expect(validateUsername("a".repeat(25))).toBe("3~24 位字母、数字或下划线");
    expect(validateUsername("a b")).toBe("3~24 位字母、数字或下划线");
    expect(validateUsername("张三abc")).toBe("3~24 位字母、数字或下划线");
    expect(validateUsername("a-b")).toBe("3~24 位字母、数字或下划线");
  });

  it("空白输入单独提示，不提示格式", () => {
    expect(validateUsername("")).toBe("请输入用户名");
    expect(validateUsername("   ")).toBe("请输入用户名");
  });
});

describe("validatePassword", () => {
  it("8 位以上通过", () => {
    expect(validatePassword("12345678")).toBeNull();
  });

  it("少于 8 位拒绝", () => {
    expect(validatePassword("1234567")).toBe("至少 8 位");
    expect(validatePassword("")).toBe("请输入密码");
  });

  it("超过 72 字节拒绝（BCrypt 只看前 72 字节，不能假装后面还有效）", () => {
    expect(validatePassword("a".repeat(72))).toBeNull();
    expect(validatePassword("a".repeat(73))).toBe("最多 72 字节");
    // 中文一个字符 3 字节
    expect(validatePassword("密".repeat(25))).toBe("最多 72 字节");
  });
});

describe("validateAuthForm", () => {
  it("都合法时返回空对象", () => {
    expect(validateAuthForm("alice", "secret123")).toEqual({});
  });

  it("两个字段各自报错，不互相影响", () => {
    expect(validateAuthForm("a", "123")).toEqual({
      username: "3~24 位字母、数字或下划线",
      password: "至少 8 位",
    });
    expect(validateAuthForm("alice", "123")).toEqual({ password: "至少 8 位" });
    expect(validateAuthForm("a", "secret123")).toEqual({ username: "3~24 位字母、数字或下划线" });
  });
});
