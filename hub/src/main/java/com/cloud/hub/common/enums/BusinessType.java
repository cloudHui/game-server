package com.cloud.hub.common.enums;

/**
 * 业务操作类型枚举。
 * <p>
 * 用于审计日志记录分类，标明当前管理员或系统操作的具体业务动作类别。
 * </p>
 *
 * @author cloud
 */
public enum BusinessType {
    /** 其它未归类操作 */
    OTHER,

    /** 新增实体数据 */
    INSERT,

    /** 修改/更新已有数据 */
    UPDATE,

    /** 删除 / 作废数据 */
    DELETE,

    /** 授权 / 分配角色权限 */
    GRANT,

    /** 导出报表或数据文件 */
    EXPORT,

    /** 批量导入外部数据 */
    IMPORT,

    /** 强退玩家或踢出房间会话 */
    FORCE,

    /** 清空缓存或重置数据 */
    CLEAN,

    /** 查询检索明细 */
    QUERY,

    /** 执行维护脚本或管理命令 */
    EXECUTE
}

