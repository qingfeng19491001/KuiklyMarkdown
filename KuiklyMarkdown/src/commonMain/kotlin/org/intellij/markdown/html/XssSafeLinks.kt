package org.intellij.markdown.html

import org.intellij.markdown.ast.ASTNode

private val UNSAFE_LINK_REGEX = Regex(
    "^(vbscript|javascript|file|data|intent|content|package|android-app):",
    RegexOption.IGNORE_CASE,
)
private val ALLOWED_DATA_LINK_REGEX = Regex("^data:image/(gif|png|jpeg|webp);", RegexOption.IGNORE_CASE)

fun makeXssSafeDestination(s: CharSequence): CharSequence {
    return s.takeIf {
        if (UNSAFE_LINK_REGEX.containsMatchIn(s.trim()))
            ALLOWED_DATA_LINK_REGEX.containsMatchIn(s.trim())
        else
            true
    } ?: "#"
}

/**
 * Demo / 宿主打开 markdown 链接时只允许 http(s) 与 mailto，避免 intent/file/js 等 scheme。
 */
fun isSafeBrowsableUrl(url: String): Boolean {
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf(':')
    if (schemeEnd <= 0) return false
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    return scheme == "http" || scheme == "https" || scheme == "mailto"
}

fun sanitizeMarkdownLinkUrl(url: String?): String? {
    if (url.isNullOrBlank()) return url
    val trimmed = url.trim()
    if (UNSAFE_LINK_REGEX.containsMatchIn(trimmed) &&
        !ALLOWED_DATA_LINK_REGEX.containsMatchIn(trimmed)
    ) {
        return null
    }
    return trimmed
}

fun LinkGeneratingProvider.makeXssSafe(useSafeLinks: Boolean = true): LinkGeneratingProvider {
    if (!useSafeLinks) return this

    return object : LinkGeneratingProvider(baseURI, resolveAnchors) {
        override fun renderLink(
            visitor: HtmlGenerator.HtmlGeneratingVisitor,
            text: String,
            node: ASTNode,
            info: RenderInfo
        ) {
            this@makeXssSafe.renderLink(visitor, text, node, info)
        }

        override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
            return this@makeXssSafe.getRenderInfo(text, node)?.let {
                it.copy(destination = makeXssSafeDestination(it.destination))
            }
        }
    }
}
