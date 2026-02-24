package com.react.agentdemo.model;

/**
 * 关系类型枚举
 * 用于槽位匹配工具中的逻辑关系控制
 */
public enum RelationType {
    /** 或关系：任一条件满足即可 */
    or,
    /** 与关系：所有条件都必须满足 */
    and,
    /** 非关系：条件必须不满足 */
    not
}
