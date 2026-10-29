import { NextResponse, type NextRequest } from "next/server";

export const USER_COOKIE = "tujang_uid";

/**
 * 身份引导：浏览器还没有 `tujang_uid` 时，在这里生成一个并同时写进
 * 「本次请求的 Cookie」与「响应 Cookie」。
 *
 * <p>为什么要在中间件做：服务端组件（历史页、模板详情）要拿身份去后端取数据，
 * 但服务端渲染期间不能设置 Cookie。中间件先把它塞进请求头，
 * SSR 的 fetch 就能带上；浏览器之后也会自动带上同一个值，两边不会各建一个用户。
 *
 * <p>后端看到合法 UUID 但库里没有时，会**采用这个 id** 建行（而不是另建一个），
 * 这也是为了不产生"没人用的空用户"。
 *
 * <p>注意：中间件只负责**页面**请求。`/api/**` 被排除在 matcher 之外，
 * 没有 Cookie 的 API 客户端由 Java 自己建用户并发 Cookie——原因见文件末尾。
 */
export function middleware(request: NextRequest) {
  if (request.cookies.get(USER_COOKIE)?.value) {
    return NextResponse.next();
  }

  const id = crypto.randomUUID();
  request.cookies.set(USER_COOKIE, id);

  const response = NextResponse.next({ request });
  response.cookies.set({
    name: USER_COOKIE,
    value: id,
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 30,
  });
  return response;
}

export const config = {
  // 静态资源、图片不参与引导；**/api 与 /files 也排除**，原因见下面这段。
  //
  // 为什么把 /api/** 排除在外：rewrite 出去的响应来自 Java，Next 不会把中间件
  // 在 NextResponse 上设的 Set-Cookie 合并进这个代理响应（实测：页面响应有
  // set-cookie，/api/me 没有）。于是"中间件发 id + 后端采用它"的组合会让
  // 纯 API 客户端（curl、脚本）永远拿不到 Cookie，每次请求都被当成新用户。
  //
  // 现在的分工更简单：
  //   页面请求 → 中间件发 id（SSR 需要它在本次请求里）
  //   API 请求 → 没有 Cookie 就由 Java 自己建用户并 Set-Cookie，
  //              上游的 Set-Cookie 会原样透传（实测有效）
  matcher: [
    "/((?!api|files|_next/static|_next/image|favicon.ico|.*\\.(?:png|jpe?g|svg|ico|webp)$).*)",
  ],
};
