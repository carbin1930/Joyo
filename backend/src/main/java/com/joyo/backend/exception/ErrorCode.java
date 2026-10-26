package com.joyo.backend.exception;

/**
 * 业务错误码（docs/30-api/rest-api.md 第 5 节错误码表）。
 * 新增错误码必须：落在正确分段 → 先登记 rest-api.md → 提供用户可读中文文案。
 */
public enum ErrorCode {

    /** 参数校验失败 */
    PARAM_INVALID(1001, "参数校验失败"),
    /** 资源不存在（连接会话/queryId 等） */
    NOT_FOUND(1002, "资源不存在"),
    /** X-Joyo-Token 校验失败（Electron Main 与后端不一致或缺失） */
    TOKEN_INVALID(1003, "应用鉴权失败"),
    /** 后端内部错误 */
    INTERNAL_ERROR(1500, "发生内部错误"),

    // ===== 2xxx 连接与会话（rest-api.md 第 5 节） =====
    /** 无法连接到数据库主机 */
    CONNECT_FAILED(2001, "无法连接到主机，请检查地址与网络"),
    /** 认证失败 */
    AUTH_FAILED(2002, "用户名或密码错误"),
    /** 连接超时 */
    CONNECT_TIMEOUT(2003, "连接超时"),
    /** 连接会话已失效 */
    SESSION_INVALID(2004, "连接已断开，请重新连接"),
    /** SSH 隧道建立失败（spec-06），message 区分网络/认证/私钥原因 */
    SSH_TUNNEL_FAILED(2005, "SSH 隧道连接失败"),
    /** 元数据读取失败（含权限不足），rest-api.md 2101 */
    METADATA_FAILED(2101, "元数据读取失败"),

    // ===== 3xxx 查询执行（rest-api.md 第 5 节） =====
    /** SQL 语法/执行错误，message 为 MySQL 原始错误 */
    QUERY_ERROR(3001, "SQL 执行错误"),
    /** 查询超时 */
    QUERY_TIMEOUT(3002, "查询超时"),

    // ===== 4xxx 表数据编辑（rest-api.md 第 5 节，spec-16） =====
    /** 结果不可编辑：非单表 / 无主键 / 结果未包含主键列 / 结果被截断 */
    NOT_EDITABLE(4101, "该结果不可编辑（需单表且包含主键列）"),
    /** 数据变更执行失败，message 为 MySQL 原始错误（一请求一事务，失败整体回滚） */
    MUTATION_FAILED(4102, "数据变更执行失败"),
    /** 主键列不可修改 */
    PRIMARY_KEY_READONLY(4103, "主键列不可修改");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
