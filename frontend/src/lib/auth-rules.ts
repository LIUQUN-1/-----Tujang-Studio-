/**
 * 登录/注册的前端校验规则。
 *
 * <p>和 Java 端的规则**必须一致**，但前端这份只负责即时反馈：
 * 真正的边界在服务端（`AuthService`），这里放行不代表服务端会放行。
 * 两个地方都写是因为"等提交才知道用户名不合法"体验太差。
 */

/** 3~24 位字母、数字或下划线。 */
const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,24}$/;

const PASSWORD_MIN = 8;
/** BCrypt 只看前 72 字节，超长部分会被静默丢弃，所以直接拒绝。 */
const PASSWORD_MAX_BYTES = 72;

export function validateUsername(value: string): string | null {
  if (!value.trim()) {
    return "请输入用户名";
  }
  if (!USERNAME_PATTERN.test(value.trim())) {
    return "3~24 位字母、数字或下划线";
  }
  return null;
}

export function validatePassword(value: string): string | null {
  if (!value) {
    return "请输入密码";
  }
  if (value.length < PASSWORD_MIN) {
    return `至少 ${PASSWORD_MIN} 位`;
  }
  if (new TextEncoder().encode(value).length > PASSWORD_MAX_BYTES) {
    return `最多 ${PASSWORD_MAX_BYTES} 字节`;
  }
  return null;
}

/** 返回字段级错误；空对象表示通过。 */
export function validateAuthForm(username: string, password: string): {
  username?: string;
  password?: string;
} {
  const errors: { username?: string; password?: string } = {};
  const usernameError = validateUsername(username);
  const passwordError = validatePassword(password);
  if (usernameError) {
    errors.username = usernameError;
  }
  if (passwordError) {
    errors.password = passwordError;
  }
  return errors;
}
