// 所有用户可见文案集中于此（coding-frontend.md 第 6 节），v0.1 不引入多语言框架
export const zhCN = {
  app: {
    name: 'Joyo',
    slogan: '轻快的 MySQL 桌面客户端',
  },
  backend: {
    checking: '正在连接后端服务…',
    ok: (version: string) => `后端服务正常（v${version}）`,
    unavailable: '后端服务异常',
    retry: '重试',
  },
}
