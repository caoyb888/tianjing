// 轻量 token 容器，解决 request.ts ↔ stores/auth.ts 循环依赖问题
// 不依赖 Pinia，可在 Axios 拦截器中安全引用
let _accessToken = ''

export const tokenHolder = {
  get: () => _accessToken,
  set: (token: string) => { _accessToken = token },
  clear: () => { _accessToken = '' },
}
