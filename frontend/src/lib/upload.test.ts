import { describe, expect, it } from "vitest";

import { detectMime, validateFile } from "./upload";

const postcard = {
  id: "demo-postcard",
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5 * 1024 * 1024,
};

const jpegOnly = {
  id: "pet-sticker",
  acceptedMimeTypes: ["image/jpeg"],
  maxFileSizeBytes: 5 * 1024 * 1024,
};

function makeFile(name: string, type: string, size: number): File {
  // 用定长 ArrayBuffer 造一个"大小正确"的假文件即可，校验只看 size/type/name
  return new File([new Uint8Array(Math.max(size, 0))], name, { type });
}

describe("detectMime", () => {
  it("优先采用浏览器给的 type", () => {
    expect(detectMime(makeFile("a.bin", "image/png", 10))).toBe("image/png");
  });

  it("type 缺失时退回扩展名", () => {
    expect(detectMime(makeFile("photo.JPG", "", 10))).toBe("image/jpeg");
    expect(detectMime(makeFile("photo.png", "application/octet-stream", 10))).toBe("image/png");
  });

  it("都认不出时返回兜底类型", () => {
    expect(detectMime(makeFile("a.xyz", "", 10))).toBe("application/octet-stream");
  });
});

describe("validateFile", () => {
  it("合法文件通过", () => {
    expect(validateFile(makeFile("ok.jpg", "image/jpeg", 1024), postcard, 1)).toBeNull();
  });

  it("类型不符合模板时，文案里带位置和允许的类型", () => {
    const message = validateFile(makeFile("a.png", "image/png", 1024), jpegOnly, 2);

    expect(message).toBe("第 2 张需为 JPG，当前为 PNG");
  });

  it("超过大小上限时，文案里给出两边的大小", () => {
    const message = validateFile(makeFile("big.jpg", "image/jpeg", 12 * 1024 * 1024), postcard, 1);

    expect(message).toBe("第 1 张 12 MB，超过上限 5 MB");
  });

  it("空文件单独报错，不落到类型/大小分支", () => {
    expect(validateFile(makeFile("empty.png", "image/png", 0), postcard, 3)).toBe(
      "第 3 张文件为空，请重新选择",
    );
  });

  it("模板不限类型时（空数组）不做类型校验", () => {
    const anyType = { ...postcard, acceptedMimeTypes: [] };
    expect(validateFile(makeFile("a.webp", "image/webp", 10), anyType, 1)).toBeNull();
  });
});
