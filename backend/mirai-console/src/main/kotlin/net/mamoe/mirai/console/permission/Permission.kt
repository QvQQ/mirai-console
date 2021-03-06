/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.permission

import net.mamoe.mirai.console.command.BuiltInCommands
import net.mamoe.mirai.console.command.Command

/**
 * 一个权限.
 *
 * 由 [PermissionService] 实现不同, [Permission] 可能会有多种实例. 但一个权限总是拥有确定的 [id].
 *
 * **注意**: 请不要手动实现这个接口. 总是从 [PermissionService.register] 获得实例.
 *
 * ### 获取 [Permission]
 *
 * #### 根权限
 * [RootPermission] 是所有权限的父权限.
 *
 * #### 指令的权限
 * 每个指令都拥有一个 [Command.permission].
 *
 * [BuiltInCommands.parentPermission] 为所有内建指令的权限.
 *
 * #### 手动申请权限
 * [PermissionService.register]
 */
public interface Permission {
    /**
     * 唯一识别 ID. 所有权限的 [id] 都互不相同.
     *
     * @see PermissionService.get 由 [id] 获取已注册的 [Permission]
     * @see PermissionId
     */
    public val id: PermissionId

    /**
     * 描述信息. 描述信息在注册权限时强制提供.
     */
    public val description: String

    /**
     * 父权限.
     *
     * [RootPermission] 的 parent 为自身
     */
    public val parent: Permission

    public companion object {
        /**
         * 根权限. 是所有权限的父权限.
         *
         * 供 Java 用户使用.
         *
         * @see RootPermission 推荐 Kotlin 用户使用.
         */
        @JvmStatic
        public fun getRootPermission(): Permission = PermissionService.INSTANCE.rootPermission

        /**
         * 递归获取 [Permission.parent], `permission.parent.parent`, permission.parent.parent` ... 直到 [Permission.parent] 为它自己.
         */
        @get:JvmStatic
        public val Permission.parentsWithSelf: Sequence<Permission>
            get() = generateSequence(this) { p ->
                p.parent.takeIf { parent -> parent != p }
            }
    }
}

/**
 * 根权限. 是所有权限的父权限. 权限 ID 为 "*:*"
 */
@get:JvmSynthetic
public val RootPermission: Permission
    get() = PermissionService.INSTANCE.rootPermission