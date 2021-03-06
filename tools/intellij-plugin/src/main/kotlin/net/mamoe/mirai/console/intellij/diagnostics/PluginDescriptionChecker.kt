/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.intellij.diagnostics

import com.intellij.psi.PsiElement
import net.mamoe.mirai.console.compiler.common.diagnostics.MiraiConsoleErrors
import net.mamoe.mirai.console.compiler.common.resolve.ResolveContextKind
import net.mamoe.mirai.console.compiler.common.resolve.resolveContextKind
import net.mamoe.mirai.console.intellij.resolve.resolveAllCalls
import net.mamoe.mirai.console.intellij.resolve.resolveStringConstantValue
import net.mamoe.mirai.console.intellij.resolve.valueParametersWithArguments
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import java.util.*

/**
 * Checks:
 * - plugin id
 * - plugin name
 */
class PluginDescriptionChecker : DeclarationChecker {
    companion object {
        private val ID_REGEX: Regex = Regex("""([a-zA-Z]+(?:\.[a-zA-Z0-9]+)*)\.([a-zA-Z]+(?:-[a-zA-Z0-9]+)*)""")
        private val FORBIDDEN_ID_NAMES: Array<String> = arrayOf("main", "console", "plugin", "config", "data")

        private const val syntax = """类似于 "net.mamoe.mirai.example-plugin", 其中 "net.mamoe.mirai" 为 groupId, "example-plugin" 为插件名. """

        /**
         * https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
         */
        private val SEMANTIC_VERSIONING_REGEX =
            Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?${'$'}""")

        fun checkPluginId(inspectionTarget: PsiElement, value: String): Diagnostic? {
            if (value.isBlank()) return MiraiConsoleErrors.ILLEGAL_PLUGIN_DESCRIPTION.on(inspectionTarget, "插件 Id 不能为空. \n插件 Id$syntax")
            if (value.none { it == '.' }) return MiraiConsoleErrors.ILLEGAL_PLUGIN_DESCRIPTION.on(inspectionTarget,
                "插件 Id '$value' 无效. 插件 Id 必须同时包含 groupId 和插件名称. $syntax")

            val lowercaseId = value.toLowerCase()

            if (ID_REGEX.matchEntire(value) == null) {
                return MiraiConsoleErrors.ILLEGAL_PLUGIN_DESCRIPTION.on(inspectionTarget, "插件 Id 无效. 正确的插件 Id 应该满足正则表达式 '${ID_REGEX.pattern}', \n$syntax")
            }

            FORBIDDEN_ID_NAMES.firstOrNull { it == lowercaseId }?.let { illegal ->
                return MiraiConsoleErrors.ILLEGAL_PLUGIN_DESCRIPTION.on(inspectionTarget, "'$illegal' 不允许作为插件 Id. 确保插件 Id 不完全是这个名称.")
            }
            return null
        }

        fun checkPluginName(inspectionTarget: PsiElement, value: String): Diagnostic? {
            if (value.isBlank()) return MiraiConsoleErrors.ILLEGAL_PLUGIN_DESCRIPTION.on(inspectionTarget, "插件名不能为空.")
            val lowercaseName = value.toLowerCase()
            FORBIDDEN_ID_NAMES.firstOrNull { it == lowercaseName }?.let { illegal ->
                return MiraiConsoleErrors.ILLEGAL_PLUGIN_DESCRIPTION.on(inspectionTarget, "'$illegal' 不允许作为插件名. 确保插件名不完全是这个名称.")
            }
            return null
        }

        fun checkPluginVersion(inspectionTarget: PsiElement, value: String): Diagnostic? {
            if (!SEMANTIC_VERSIONING_REGEX.matches(value)) {
                return MiraiConsoleErrors.ILLEGAL_PLUGIN_DESCRIPTION.on(inspectionTarget, "版本号无效: '$value'. \nhttps://semver.org/lang/zh-CN/")
            }
            return null
        }
    }

    private val checkersMap: EnumMap<ResolveContextKind, (declaration: PsiElement, value: String) -> Diagnostic?> =
        EnumMap<ResolveContextKind, (declaration: PsiElement, value: String) -> Diagnostic?>(ResolveContextKind::class.java).apply {
            put(ResolveContextKind.PLUGIN_NAME, ::checkPluginName)
            put(ResolveContextKind.PLUGIN_ID, ::checkPluginId)
            put(ResolveContextKind.PLUGIN_VERSION, ::checkPluginVersion)
        }

    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext,
    ) {
        declaration.resolveAllCalls(context.bindingContext)
            .flatMap { call ->
                call.valueParametersWithArguments().asSequence()
            }
            .mapNotNull { (p, a) ->
                p.resolveContextKind?.takeIf { it in checkersMap }?.let { it to a }
            }
            .mapNotNull { (kind, argument) ->
                argument.resolveStringConstantValue(context.bindingContext)?.let { const ->
                    Triple(kind, argument, const)
                }
            }
            .forEach { (parameterContextKind, argument, resolvedConstant) ->
                for ((kind, fn) in checkersMap) {
                    if (parameterContextKind == kind) fn(argument.asElement(), resolvedConstant)?.let { context.report(it) }
                }
            }
        return
        /*
        when (declaration) {
            is KtClassOrObject -> {
                // check super type constructor
                val superTypeCallEntry = declaration.findChild<KtSuperTypeList>()?.findChild<KtSuperTypeCallEntry>() ?: return
                // val constructorCall = superTypeCallEntry.findChildren<KtConstructorCalleeExpression>()?.resolveToCall() ?: return
                val valueArgumentList = superTypeCallEntry.findChild<KtValueArgumentList>() ?: return
                valueArgumentList.arguments.asSequence().mapNotNull(KtValueArgument::getArgumentExpression).forEach {
                    for (child in it.allChildrenWithSelf) {
                        if (child is LambdaArgument) {
                            child.getLambdaExpression()?.bodyExpression?.statements?.forEach { statement ->
                                if (statement is KtCallExpression) check(statement, context)
                            }
                        }
                        if (child is KtCallExpression) {
                            check(child, context)
                        }
                    }
                }
            }
            else -> {
                declaration.children.flatMap {
                    when (it) {
                        is KtCallExpression -> listOf(it)
                        is KtLambdaExpression -> it.bodyExpression?.statements.orEmpty()
                        else -> emptyList()
                    }
                }.forEach { element ->
                    if (element is KtDeclaration) {
                        val desc = element.descriptor ?: return@forEach
                        check(element, desc, context)
                    }
                }
            }
        }*/
    }
}