package com.tencent.kuiklybase.math

/**
 * 公式尺寸估算与 Native 绘制失败时的纯文本回退，不是正式排版引擎。
 *
 * 各端真正出字形的是：Android JLaTeXMath、iOS iosMath、HarmonyOS Canvas。
 * 本对象只把常见命令压成可读 Unicode，供公共层首帧尺寸估算和纯文本语义使用。
 * 未知命令保留源文本；解析失败返回 null；不抛异常。
 */
object SimpleMathRenderer {

    fun tryLayout(latex: String, display: Boolean = false): String? {
        if (latex.isBlank()) return ""
        return parseMathLayout(latex, display).flatten().ifBlank { null }
    }
}

internal class LatexLayoutParser(
    private val src: String,
    private val display: Boolean,
) {
    private var i = 0

    fun parseLayout(): MathNode = parseExpr(Stop.None)

    private enum class Stop { None, Brace, Bracket, Right }

    private fun parseExpr(stop: Stop): MathNode {
        val parts = mutableListOf<MathNode>()
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                parts += MathNode.Text(text.toString())
                text.clear()
            }
        }
        while (i < src.length) {
            skipWs()
            if (i >= src.length) break
            if (shouldStop(stop)) break
            if (src[i] == '^' || src[i] == '_') {
                appendNode(parseScripts(MathNode.Text("")), parts, text)
                continue
            }
            val atom = parseAtom() ?: break
            appendNode(parseScripts(atom), parts, text)
        }
        flushText()
        return when (parts.size) {
            0 -> MathNode.Text("")
            1 -> parts[0]
            else -> MathNode.Row(parts)
        }
    }

    private fun shouldStop(stop: Stop): Boolean {
        if (i >= src.length) return true
        return when (src[i]) {
            '}' -> true
            '&' -> true
            ']' -> stop == Stop.Bracket
            else -> stop == Stop.Right && startsWith("\\right")
        }
    }

    private fun appendNode(node: MathNode, parts: MutableList<MathNode>, text: StringBuilder) {
        if (node is MathNode.Text) {
            text.append(node.value)
        } else {
            if (text.isNotEmpty()) {
                parts += MathNode.Text(text.toString())
                text.clear()
            }
            parts += node
        }
    }

    private fun parseAtom(): MathNode? {
        skipWs()
        if (i >= src.length) return null
        return when (src[i]) {
            '{' -> parseGroup()
            '}' -> null
            '&' -> null
            ']' -> null
            '\\' -> parseCommand()
            else -> MathNode.Text(src[i++].toString())
        }
    }

    private fun parseScripts(base: MathNode): MathNode {
        var sub: MathNode? = null
        var sup: MathNode? = null
        while (i < src.length) {
            skipWs()
            if (i >= src.length) break
            when (src[i]) {
                '_' -> {
                    i++
                    sub = mergeScript(sub, parseGroupOrAtom())
                }
                '^' -> {
                    i++
                    sup = mergeScript(sup, parseGroupOrAtom())
                }
                else -> break
            }
        }
        val op = base as? MathNode.BigOp
        if (sub == null && sup == null) {
            return if (op != null) MathNode.Text(op.symbol) else base
        }
        return if (op != null && display && op.limits) {
            MathNode.Operator(op.symbol, under = sub, over = sup)
        } else {
            MathNode.Scripted(if (op != null) MathNode.Text(op.symbol) else base, sub, sup)
        }
    }

    private fun mergeScript(existing: MathNode?, extra: MathNode): MathNode =
        if (existing == null) extra else MathNode.Row(listOf(existing, extra))

    private fun parseGroup(): MathNode {
        if (i >= src.length || src[i] != '{') return MathNode.Text("")
        i++
        val inner = parseExpr(Stop.Brace)
        if (i < src.length && src[i] == '}') i++
        return inner
    }

    private fun parseCommand(): MathNode {
        if (i >= src.length || src[i] != '\\') return MathNode.Text("")
        i++
        if (i >= src.length) return MathNode.Text("\\")
        if (src[i] == '\\' && i + 1 < src.length && src[i + 1].isLetter()) {
            i++
        }
        val first = src[i]
        if (!first.isLetter()) {
            i++
            val token = when (first) {
                '\\' -> if (display) "\n" else " "
                ',', ':', ';', '!' -> " "
                '{', '}', '_', '&', '%', '#', '$' -> first.toString()
                '|' -> "∥"
                else -> first.toString()
            }
            return MathNode.Text(token)
        }
        val name = buildString {
            while (i < src.length && src[i].isLetter()) {
                append(src[i])
                i++
            }
        }
        if (i < src.length && src[i] == '*') i++
        skipWs()
        return layoutCommand(name)
    }

    private fun layoutCommand(name: String): MathNode {
        BIG_OPS[name]?.let { (symbol, limits) -> return MathNode.BigOp(symbol, limits) }
        SYMBOLS[name]?.let { return MathNode.Text(it) }
        return when (name) {
            "frac", "dfrac", "tfrac", "cfrac" ->
                MathNode.Frac(parseGroupOrAtom(), parseGroupOrAtom(), bar = true)
            "binom", "dbinom", "tbinom" -> MathNode.Delimited(
                "(",
                MathNode.Frac(parseGroupOrAtom(), parseGroupOrAtom(), bar = false),
                ")",
            )
            "sqrt" -> {
                val index = if (i < src.length && src[i] == '[') {
                    i++
                    val raw = parseExpr(Stop.Bracket)
                    if (i < src.length && src[i] == ']') i++
                    raw
                } else {
                    null
                }
                MathNode.Sqrt(index, parseGroupOrAtom())
            }
            "overline" -> MathNode.Accent(MathAccent.Overline, parseGroupOrAtom())
            "underline" -> MathNode.Accent(MathAccent.Underline, parseGroupOrAtom())
            "hat" -> MathNode.Accent(MathAccent.Hat, parseGroupOrAtom())
            "bar" -> MathNode.Accent(MathAccent.Bar, parseGroupOrAtom())
            "vec" -> MathNode.Accent(MathAccent.Vec, parseGroupOrAtom())
            "tilde" -> MathNode.Accent(MathAccent.Tilde, parseGroupOrAtom())
            "dot" -> MathNode.Accent(MathAccent.Dot, parseGroupOrAtom())
            "ddot" -> MathNode.Accent(MathAccent.Ddot, parseGroupOrAtom())
            "begin" -> parseEnvironment()
            "end" -> {
                parseGroup()
                MathNode.Text("")
            }
            "left" -> parseLeftRight()
            "right" -> MathNode.Text(parseFence())
            "text", "textrm", "mbox" -> MathNode.Text(parseRawGroup())
            "mathrm", "operatorname", "mathbf", "mathit", "mathsf", "mathtt", "textbf" ->
                parseGroupOrAtom()
            "mathbb" -> MathNode.Text(parseRawGroup().map { it.toMathBb() }.joinToString(""))
            "mathcal", "mathscr" -> MathNode.Text(parseRawGroup().map { it.toMathCal() }.joinToString(""))
            "mathfrak" -> MathNode.Text(parseRawGroup())
            "not" -> MathNode.Text(negate(parseGroupOrAtom().flatten()))
            "quad", "qquad" -> MathNode.Text("  ")
            "sin", "cos", "tan", "cot", "sec", "csc",
            "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh",
            "log", "ln", "lg", "exp", "det", "dim", "ker", "deg", "gcd", "hom", "arg",
            -> MathNode.Text(name)
            else -> MathNode.Text("\\$name")
        }
    }

    private fun parseLeftRight(): MathNode {
        val left = parseFence()
        val body = parseExpr(Stop.Right)
        skipWs()
        val right = if (startsWith("\\right")) {
            i += 6
            parseFence()
        } else {
            ""
        }
        return MathNode.Delimited(left, body, right)
    }

    private fun parseFence(): String {
        skipWs()
        if (i >= src.length) return ""
        if (src[i] == '.') {
            i++
            return ""
        }
        if (src[i] == '\\') {
            i++
            if (i >= src.length) return "\\"
            if (!src[i].isLetter()) {
                return src[i++].toString()
            }
            val name = buildString {
                while (i < src.length && src[i].isLetter()) {
                    append(src[i])
                    i++
                }
            }
            if (i < src.length && src[i] == '*') i++
            return FENCES[name] ?: SYMBOLS[name] ?: name
        }
        return src[i++].toString()
    }

    private fun parseEnvironment(): MathNode {
        val env = parseGroup().flatten().trim()
        val body = StringBuilder()
        while (i < src.length) {
            skipWs()
            if (startsWith("\\end")) {
                i += 4
                val endName = parseGroup().flatten().trim()
                if (endName == env || endName.isEmpty()) break
                body.append("\\end{$endName}")
                continue
            }
            body.append(src[i])
            i++
        }
        var raw = body.toString()
        if (env == "array") {
            raw = stripLeadingGroup(raw)
        }
        val rows = raw
            .split("\\\\")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { row ->
                row.split("&").map { cell ->
                    LatexLayoutParser(cell.trim(), display = false).parseLayout()
                }
            }
        return if (rows.isEmpty()) MathNode.Text("") else MathNode.Matrix(env, rows)
    }

    private fun stripLeadingGroup(raw: String): String {
        val src = raw.trimStart()
        if (!src.startsWith("{")) return src
        var depth = 0
        src.forEachIndexed { index, ch ->
            when (ch) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(index + 1)
                }
            }
        }
        return src
    }

    private fun parseGroupOrAtom(): MathNode {
        skipWs()
        if (i >= src.length) return MathNode.Text("")
        return if (src[i] == '{') parseGroup() else parseAtom() ?: MathNode.Text("")
    }

    private fun parseRawGroup(): String {
        skipWs()
        if (i >= src.length) return ""
        if (src[i] != '{') return (parseAtom() ?: MathNode.Text("")).flatten()
        i++
        val start = i
        var depth = 1
        while (i < src.length && depth > 0) {
            when (src[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth > 0) i++
        }
        val value = src.substring(start, i)
        if (i < src.length && src[i] == '}') i++
        return value
    }

    private fun startsWith(token: String): Boolean {
        skipWs()
        return src.startsWith(token, startIndex = i)
    }

    private fun skipWs() {
        while (i < src.length && src[i].isWhitespace()) i++
    }
}

private fun negate(value: String): String = when (value) {
    "=" -> "≠"
    "∈" -> "∉"
    "⊂" -> "⊄"
    "∃" -> "∄"
    else -> "¬$value"
}

private fun Char.toMathBb(): Char = when (this) {
    'R' -> 'ℝ'
    'N' -> 'ℕ'
    'Z' -> 'ℤ'
    'Q' -> 'ℚ'
    'C' -> 'ℂ'
    'P' -> 'ℙ'
    'H' -> 'ℍ'
    else -> this
}

private fun Char.toMathCal(): Char = when (this) {
    'L' -> 'ℒ'
    'F' -> 'ℱ'
    'H' -> 'ℋ'
    'M' -> 'ℳ'
    'R' -> 'ℛ'
    'B' -> 'ℬ'
    'E' -> 'ℰ'
    'I' -> 'ℐ'
    else -> this
}

private val BIG_OPS = mapOf(
    "sum" to ("∑" to true),
    "prod" to ("∏" to true),
    "coprod" to ("∐" to true),
    "bigcup" to ("⋃" to true),
    "bigcap" to ("⋂" to true),
    "bigvee" to ("⋁" to true),
    "bigwedge" to ("⋀" to true),
    "lim" to ("lim" to true),
    "liminf" to ("lim inf" to true),
    "limsup" to ("lim sup" to true),
    "max" to ("max" to true),
    "min" to ("min" to true),
    "sup" to ("sup" to true),
    "inf" to ("inf" to true),
    "Pr" to ("Pr" to true),
    "int" to ("∫" to false),
    "iint" to ("∬" to false),
    "iiint" to ("∭" to false),
    "oint" to ("∮" to false),
)

private val FENCES = mapOf(
    "lvert" to "|",
    "rvert" to "|",
    "lVert" to "∥",
    "rVert" to "∥",
    "langle" to "⟨",
    "rangle" to "⟩",
    "lfloor" to "⌊",
    "rfloor" to "⌋",
    "lceil" to "⌈",
    "rceil" to "⌉",
    "lbrace" to "{",
    "rbrace" to "}",
    "vert" to "|",
    "Vert" to "∥",
)

private val SYMBOLS = mapOf(
    "alpha" to "α",
    "beta" to "β",
    "gamma" to "γ",
    "delta" to "δ",
    "epsilon" to "ε",
    "varepsilon" to "ε",
    "zeta" to "ζ",
    "eta" to "η",
    "theta" to "θ",
    "vartheta" to "ϑ",
    "iota" to "ι",
    "kappa" to "κ",
    "lambda" to "λ",
    "mu" to "μ",
    "nu" to "ν",
    "xi" to "ξ",
    "pi" to "π",
    "varpi" to "ϖ",
    "rho" to "ρ",
    "varrho" to "ϱ",
    "sigma" to "σ",
    "varsigma" to "ς",
    "tau" to "τ",
    "upsilon" to "υ",
    "phi" to "φ",
    "varphi" to "ϕ",
    "chi" to "χ",
    "psi" to "ψ",
    "omega" to "ω",
    "Gamma" to "Γ",
    "Delta" to "Δ",
    "Theta" to "Θ",
    "Lambda" to "Λ",
    "Xi" to "Ξ",
    "Pi" to "Π",
    "Sigma" to "Σ",
    "Upsilon" to "Υ",
    "Phi" to "Φ",
    "Psi" to "Ψ",
    "Omega" to "Ω",
    "times" to "×",
    "cdot" to "·",
    "pm" to "±",
    "mp" to "∓",
    "div" to "÷",
    "leq" to "≤",
    "le" to "≤",
    "geq" to "≥",
    "ge" to "≥",
    "neq" to "≠",
    "ne" to "≠",
    "approx" to "≈",
    "equiv" to "≡",
    "simeq" to "≃",
    "cong" to "≅",
    "sim" to "∼",
    "propto" to "∝",
    "infty" to "∞",
    "infin" to "∞",
    "partial" to "∂",
    "rightarrow" to "→",
    "leftarrow" to "←",
    "leftrightarrow" to "↔",
    "Rightarrow" to "⇒",
    "Leftarrow" to "⇐",
    "Leftrightarrow" to "⇔",
    "implies" to "⇒",
    "iff" to "⇔",
    "to" to "→",
    "mapsto" to "↦",
    "cdots" to "⋯",
    "ldots" to "…",
    "vdots" to "⋮",
    "ddots" to "⋱",
    "dots" to "…",
    "forall" to "∀",
    "exists" to "∃",
    "in" to "∈",
    "notin" to "∉",
    "ni" to "∋",
    "subset" to "⊂",
    "supset" to "⊃",
    "subseteq" to "⊆",
    "supseteq" to "⊇",
    "cdotp" to "·",
    "ast" to "*",
    "star" to "⋆",
    "circ" to "∘",
    "bullet" to "•",
    "neg" to "¬",
    "lnot" to "¬",
    "wedge" to "∧",
    "vee" to "∨",
    "cap" to "∩",
    "cup" to "∪",
    "ell" to "ℓ",
    "hbar" to "ℏ",
    "Re" to "ℜ",
    "Im" to "ℑ",
    "nabla" to "∇",
    "emptyset" to "∅",
    "varnothing" to "∅",
    "aleph" to "ℵ",
    "wp" to "℘",
    "angle" to "∠",
    "triangle" to "△",
    "square" to "□",
    "mid" to "∣",
    "parallel" to "∥",
    "perp" to "⊥",
    "ll" to "≪",
    "gg" to "≫",
    "prec" to "≺",
    "succ" to "≻",
    "oplus" to "⊕",
    "otimes" to "⊗",
    "ominus" to "⊖",
    "oslash" to "⊘",
    "odot" to "⊙",
    "dagger" to "†",
    "ddagger" to "‡",
    "degree" to "°",
    "colon" to ":",
    "backslash" to "\\",
)
