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
  common: {
    cancel: '取消',
    save: '保存',
  },
  connection: {
    new: '新建连接',
    empty: '暂无连接，点击上方按钮新建',
    copySuffix: '副本',
    dialog: {
      newTitle: '新建连接',
      editTitle: '编辑连接',
    },
    field: {
      name: '名称',
      host: '主机',
      port: '端口',
      username: '用户名',
      password: '密码',
      database: '默认数据库',
    },
    passwordKeepHint: '留空表示不修改',
    show: '显示',
    hide: '隐藏',
    test: '测试连接',
    testing: '测试中…',
    testOk: (version: string, ms: number) => `连接成功（耗时 ${ms} ms，MySQL ${version}）`,
    saved: '连接已保存',
    deleted: '连接已删除',
    menu: {
      edit: '编辑',
      clone: '复制连接',
      delete: '删除',
    },
    confirmDelete: (name: string) => `确定删除连接「${name}」吗？`,
    needPassword: '该连接未保存密码，请先编辑并填写密码',
    passwordInvalid: '保存的密码已失效，请编辑连接重新输入密码',
    connected: (version: string) => `连接成功（MySQL ${version}）`,
    validate: {
      name: '名称不能为空',
      nameLength: '名称不能超过 50 个字符',
      host: '主机不能为空',
      port: '端口范围 1-65535',
      username: '用户名不能为空',
    },
  },
}
