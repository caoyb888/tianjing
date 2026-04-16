// 轻量 token 容器，解决 request.ts ↔ stores/auth.ts 循环依赖问题
// 不依赖 Pinia，可在 Axios 拦截器中安全引用
// 使用 sessionStorage 持久化：tab 关闭自动清除，不跨标签页共享，比 localStorage 更安全
const SESSION_KEY = 'tj_access_token'

export const tokenHolder = {
  get: () => sessionStorage.getItem(SESSION_KEY) ?? '',
  set: (token: string) => { sessionStorage.setItem(SESSION_KEY, token) },
  clear: () => { sessionStorage.removeItem(SESSION_KEY) },
}
