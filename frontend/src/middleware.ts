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
  // 静态资源与图片不参与引导
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|jpe?g|svg|ico|webp)$).*)"],
};
