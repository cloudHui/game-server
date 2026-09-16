package com.cloud.hub.common.enums;

/**
 * 业务操作类型（参照 RuoYi 设计）
 */
public enum BusinessType {
    /** 其它 */
    OTHER,

    /** 新增 */
    INSERT,

    /** 修改 */
    UPDATE,

    /** 删除 / 作废 */
    DELETE,

    /** 授权 / 权限 */
    GRANT,

    /** 导出 */
    EXPORT,

    /** 导入 */
    IMPORT,

    /** 强退 / 踢出 */
    FORCE,

    /** 清空数据 */
    CLEAN,

    /** 查询 */
    QUERY,

    /** 执行命令 */
    EXECUTE
}
