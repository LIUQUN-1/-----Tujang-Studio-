import { describe, expect, it } from "vitest";

import { formatBytes, formatMimeTypes } from "./format";

describe("formatBytes", () => {
  it("小于 1KB 显示字节", () => {
    expect(formatBytes(512)).toBe("512 B");
  });

  it("KB 级四舍五入到整数", () => {
    expect(formatBytes(2048)).toBe("2 KB");
  });

  it("MB 级整数不补小数，非整数保留一位", () => {
    expect(formatBytes(5242880)).toBe("5 MB");
    expect(formatBytes(1572864)).toBe("1.5 MB");
  });

  it("非法输入显示占位符，不抛异常", () => {
    expect(formatBytes(-1)).toBe("—");
    expect(formatBytes(Number.NaN)).toBe("—");
  });
});

describe("formatMimeTypes", () => {
  it("常见类型转成短标签", () => {
    expect(formatMimeTypes(["image/jpeg", "image/png"])).toBe("JPG / PNG");
  });

  it("未知类型原样展示", () => {
    expect(formatMimeTypes(["image/heic"])).toBe("image/heic");
  });

  it("空数组表示不限", () => {
    expect(formatMimeTypes([])).toBe("不限");
  });
});
