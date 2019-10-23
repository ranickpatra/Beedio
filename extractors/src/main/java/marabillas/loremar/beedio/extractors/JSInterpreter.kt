/*
 * Beedio is an Android app for downloading videos
 * Copyright (C) 2019 Loremar Marabillas
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package marabillas.loremar.beedio.extractors

import androidx.core.text.isDigitsOnly
import com.google.gson.Gson
import com.google.gson.JsonParser

class JSInterpreter(private val code: String, private val objects: HashMap<String, Any> = hashMapOf()) {

    private val OPERATORS = hashMapOf(
            "|" to Any::or,
            "^" to Any::xor,
            "&" to Any::and,
            ">>" to Any::shr,
            "<<" to Any::shl,
            "-" to Any::minus,
            "+" to Any::plus,
            "%" to Any::rem,
            "/" to Any::div,
            "*" to Any::times
    )
    private val ASSIGN_OPERATORS = hashMapOf<String, (Any, Any) -> Any>().also {
        for ((op, opfunc) in OPERATORS)
            it["$op="] = opfunc
    }.apply { put("=") { cur, right -> right } }
    private val NAME_RE = """[a-zA-Z_${'$'}][a-zA-Z_${'$'}0-9]*"""
    private val functions = hashMapOf<String, (List<Any>) -> Any?>()

    fun extractFunction(funcname: String): (List<Any>) -> Any? {
        val s = ExtractorUtils.escape(funcname)
        val funcM = """(?x)
                (?:function\s+$s|[{;,]\s*$s\s*=\s*function|var\s+$s\s*=\s*function)\s*
                \(([^)]*)\)\s*
                \{([^}]+)}""".toRegex().find(code)
                ?: throw ExtractorException("Could not find JS function $funcname")
        val argnames = funcM.groups[2]?.value?.split(",")

        return argnames?.let { args ->
            funcM.groups[3]?.value?.let { code ->
                buildFunction(args, code)
            }
        }
                ?: throw ExtractorException("Could not fun JS function $funcname")
    }

    fun buildFunction(argnames: List<String>, code: String): (List<Any>) -> Any? {
        return { args ->
            val localVars = (argnames zip args).toMap().toMutableMap()
            var result: Any? = null
            for (stmt in code.split(";")) {
                val (res, abort) = interpretStatement(stmt, localVars)
                result = res
                if (abort)
                    break
            }
            result
        }
    }

    fun interpretStatement(stmt: String, localVars: MutableMap<String, Any>, allowRecursion: Int = 100): Result {
        if (allowRecursion < 0)
            throw ExtractorException("Recursion limit reached")

        var shouldAbort = false
        val stmtx = stmt.trimStart()
        val stmtM = """var\s""".toRegex().find(stmtx)?.value
        val expr: String
        if (stmtM != null && stmtx.startsWith(stmtM))
            expr = stmtx.substring(stmtM.length)
        else {
            val returnM = """return(?:\s+|${'$'})""".toRegex().find(stmtx)?.value
            if (returnM != null && stmtx.startsWith(returnM)) {
                expr = stmtx.substring(returnM.length)
                shouldAbort = true
            } else {
                // Try interpreting it as an expression
                expr = stmtx
            }
        }

        val v = interpretExpression(expr, localVars, allowRecursion)
        return Result(v, shouldAbort)
    }

    fun interpretExpression(expr: String, localVars: MutableMap<String, Any>, allowRecursion: Int): Any? {
        var exprx = expr.trim()
        if (exprx.isEmpty())
            return null

        val gson = Gson()
        if (exprx.startsWith("(")) {
            var parensCount = 0
            val mObjs = """[()]""".toRegex().findAll(exprx)
            if (mObjs.none())
                throw ExtractorException("Premature end of parens in $exprx")
            for (m in mObjs) {
                if (m.value == "(")
                    parensCount++
                else {
                    parensCount--
                    if (parensCount == 0) {
                        val subExpr = exprx.substring(1, m.range.first)
                        val subResult = interpretExpression(subExpr, localVars, allowRecursion)
                        val remainingExpr = exprx.substring(m.range.last, exprx.length).trim()
                        if (remainingExpr.isEmpty())
                            return subResult
                        else
                            exprx = "${gson.toJson(subResult)}$remainingExpr"
                        break
                    }
                }
            }
        }

        for ((op, opfunc) in ASSIGN_OPERATORS) {
            val s1 = NAME_RE
            val s2 = ExtractorUtils.escape(op)
            val m = """(?x)
                    ($s1)(?:\[([^]]+?)])?
                    \s*$s2
                    (.*)$""".toRegex().find(exprx)
                    ?: continue

            val rightVal = m.groups.last()?.value?.let {
                interpretExpression(it, localVars, allowRecursion - 1)
            }

            if (m.groups.count() == 5) {
                val out = m.groups[1]?.value
                val lvar = localVars[m.groups[1]?.value]
                val idx = m.groups[3]?.value?.let { interpretExpression(it, localVars, allowRecursion) }
                if (idx is Int && rightVal != null) {
                    val cur: Any?
                    if (lvar is String && out != null) {
                        cur = lvar[idx]
                        val value = opfunc(cur, rightVal)
                        if (value is Char)
                            localVars[out] = lvar.toCharArray().also { it[idx] = value }.toString()
                        return value
                    } else if (lvar is List<*> && out != null) {
                        cur = lvar[idx]
                        val value = cur?.let { opfunc(it, rightVal) }
                        lvar.toMutableList()[idx] = value
                        localVars[out] = lvar
                        return value
                    }
                }
            } else {
                val out = m.groups[1]?.value
                val cur = localVars[out]
                if (cur != null && rightVal != null && out != null) {
                    val value = opfunc(cur, rightVal)
                    localVars[out] = value
                    return value
                }
            }
        }

        if (exprx.isDigitsOnly())
            return expr.toInt()

        val varM = """(?!if|return|true|false)($NAME_RE)${'$'}""".toRegex().find(exprx)
        if (varM != null && exprx.startsWith(varM.value))
            return localVars[varM.groups.last()?.value]

        try {
            return JsonParser.parseString(exprx)
        } catch (e: Exception) {
        }

        var m = """($NAME_RE)\[(.+)]${'$'}""".toRegex().find(exprx)

        if (m != null && exprx.startsWith(m.value)) {
            val value = localVars[m.groups[1]?.value]
            val idx = m.groups.last()?.value?.let { interpretExpression(it, localVars, allowRecursion - 1) }
            if (value is String && idx is Int)
                return value[idx]
            else if (value is List<*> && idx is Int)
                return value[idx]
        }

        m = """($NAME_RE)(?:\.([^(]+)|\[([^]]+)])\s*(?:\(+([^()]*)\))?${'$'}"""
                .toRegex().find(exprx)
        if (m != null && exprx.startsWith(m.value)) {
            val variable = m.groups[1]?.value
            val member = ExtractorUtils.removeQuotes(m.groups[3]?.value) ?: m.groups[4]?.value
            val argStr = if (m.groups.count() == 7)
                m.groups[6]?.value
            else
                null

            val obj: Any?
            if (localVars.contains(variable))
                obj = localVars[variable]
            else {
                if (variable != null && !objects.contains(variable)) {
                    objects[variable] = extractObject(variable)
                }
                obj = objects[variable]
            }

            if (argStr.isNullOrEmpty()) {
                // Member access
                if (member == "length")
                    return when (obj) {
                        is String -> obj.length
                        is Map<*, *> -> obj.size
                        is List<*> -> obj.size
                        else -> {
                        }
                    }
                if (obj is Map<*, *>) return obj[member]
            }

            assert(exprx.endsWith(")"))
            // Function call
            val argvals = if (argStr == "")
                listOf<Any>()
            else
                mutableListOf<Any>().apply {
                    if (argStr != null) {
                        for (v in argStr.split(","))
                            interpretExpression(v, localVars, allowRecursion)?.let { add(it) }
                    }
                }

            if (member == "split") {
                assert(argvals[0] == "" && argvals[1] == ",")
                if (obj is List<*>) return mutableListOf<Any?>().addAll(obj)
            }
            if (member == "join") {
                assert(argvals.size == 1)
                val delim = argvals[0]
                if (delim is String && obj is List<*>) return obj.joinToString(delim)
            }
            if (member == "reverse") {
                assert(argvals.isEmpty())
                if (obj is List<*>) return obj.reversed()
            }
            if (member == "slice") {
                assert(argvals.size == 1)
                val sliceArg = argvals[0]
                if (obj is List<*> && sliceArg is Int) return obj.slice(sliceArg..obj.size)
            }
            if (member == "splice") {
                assert(obj is MutableList<*>)
                val (index, howMany) = argvals
                val res = mutableListOf<Any?>()
                if (index is Int && howMany is Int && obj is MutableList<*>) {
                    for (i in index until (if ((index + howMany) < obj.size) (index + howMany) else obj.size)) {
                        res.add(obj[index])
                        obj.removeAt(index)
                    }
                    return res
                }
            }

            if (obj is Map<*, *>) {
                val func = obj[member]
                if (func is Function<*>) {
                    @Suppress("UNCHECKED_CAST")
                    return (func as (List<Any>) -> Any?)(argvals)
                }
            }
        }

        for ((op, opfunc) in OPERATORS) {
            m = """(.+?)${ExtractorUtils.escape(op)}(.+)""".toRegex().find(exprx)
            if (m == null)
                continue
            val rm = m
            rm.groups[1]?.value?.let { rx ->
                val (x, abortX) = interpretStatement(rx, localVars, allowRecursion - 1)
                if (abortX)
                    throw ExtractorException("Premature left-side return of $op in $exprx")
                rm.groups[2]?.value?.let { ry ->
                    val (y, abortY) = interpretStatement(ry, localVars, allowRecursion - 1)
                    if (abortY)
                        throw ExtractorException("Premature right-side of $op in $exprx")
                    return x?.let { resX ->
                        y?.let { resY ->
                            opfunc(resX, resY)
                        }
                    }
                }
            }
        }

        m = """^(${NAME_RE})\(([a-zA-Z0-9_${'$'},]*)\)${'$'}""".toRegex().find(exprx)
        if (m != null && exprx.startsWith(m.value)) {
            val fname = m.groups[1]?.value
            val argvals = if ((m.groups.last()?.value?.length ?: 0) > 0) {
                mutableListOf<Any>().apply {
                    m.groups.last()?.value?.split(",")?.forEach { v ->
                        if (v.isDigitsOnly())
                            add(v.toInt())
                        else
                            localVars[v]?.let { add(it) }
                    }
                }
            } else
                listOf<Any>()
            if (!fname.isNullOrBlank() && !functions.contains(fname))
                functions[fname] = extractFunction(fname)
            return functions[fname]?.let { it(argvals) }
        }

        throw ExtractorException("Unsupported JS Expression $expr")
    }

    fun extractObject(objname: String): HashMap<String, (List<Any>) -> Any?> {
        val FUNC_NAME_RE = """(?:[a-zA-Z${'$'}0-9]+|"[a-zA-Z${'$'}0-9]+"|'[a-zA-Z${'$'}0-9]+')""".toRegex()
        val obj = hashMapOf<String, (List<Any>) -> Any?>()
        val objM = """(?x)
                (?<!this\.)${ExtractorUtils.escape(objname)}\s*=\s*{\s*
                    (($FUNC_NAME_RE\s*:\s*function\s*\(.*?\)\s*{.*?}(?:,\s*)?)*)
                }\s*;""".toRegex().find(code)
        val fields = objM?.groups?.get(2)?.value
        // Currently, it only supports function definitions
        val fieldsM = fields?.let {
            """(?x)
                ($FUNC_NAME_RE)\s*:\s*function\s*\(([a-z,]+)\){([^}]+)}"""
                    .toRegex().findAll(it)
        }
        if (fieldsM != null) {
            for (f in fieldsM) {
                val argnames = f.groups[2]?.value?.split(",")
                val key = ExtractorUtils.removeQuotes(f.groups[1]?.value)
                val func = argnames?.let { argn ->
                    f.groups.last()?.value?.let { code ->
                        buildFunction(argn, code)
                    }
                }
                if (key != null && func != null)
                    obj[key] = func
            }
        }
        return obj
    }

    data class Result(val result: Any?, val abort: Boolean)
}