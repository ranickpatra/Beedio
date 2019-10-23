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

import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigInteger
import java.net.URLDecoder
import java.net.URLEncoder

object ExtractorUtils {

    fun unsmuggleUrl(smugUrl: String): String {
        if (!smugUrl.contains("#__youtubedl_smuggle"))
            return smugUrl
        else
            TODO("No implementation for smuggled url")
    }

    fun contentOf(url: String): String? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().body.toString()
        } catch (e: IOException) {
            null
        }
    }

    fun queryStringFrom(map: HashMap<String, String>): String {
        return map.map { (k, v) -> "${k.encodeUtf8()}=${v.encodeUtf8()}" }.joinToString("&")
    }

    private fun String.encodeUtf8(): String = URLEncoder.encode(this, "UTF-8")

    fun parseQueryString(queryString: String): HashMap<String, List<String>> {
        val map = hashMapOf<String, List<String>>()
        queryString.split("&").forEach {
            val key = it.substringBefore("=")
            val values = it.substringAfter("=").decodeUtf8()
            val valuesList = values.split(",")
            map[key] = valuesList
        }
        return map
    }

    private fun String.decodeUtf8(): String = URLDecoder.decode(this, "UTF-8")

    fun uppercaseEscape(s: String): String {
        return s.replace("""\\U[0-9a-fA-F]{8}""".toRegex()) {
            val unicode = it.value.substringAfter("\\U")
            String(BigInteger(unicode, 16).toByteArray())
        }
    }

    fun jsonElementToStringList(element: JsonElement): List<String> {
        val list = mutableListOf<String>()
        if (element.isJsonArray) {
            element.asJsonArray.forEach { list.add(it.asString) }
        } else {
            list.add(element.asString)
        }
        return list
    }

    /**
     * Clean an html snippet into a readable string
     */
    fun cleanHtml(html: String): String {
        // Newline vs <br />
        var cleaned = html.replace("\n", " ")
        cleaned = cleaned.replace("""(?u)\s*<\s*br\s*/?\s*>\s*""".toRegex(), "\n")
        cleaned = cleaned.replace("""(?u)<\s*/\s*p\s*>\s*<\s*p[^>]*>""".toRegex(), "\n")
        // Strip html tags
        cleaned = cleaned.replace("<.*?>".toRegex(), "")
        // Replace html entities
        cleaned = unescapeHtml(cleaned)
        return cleaned.trim()
    }

    fun unescapeHtml(s: String): String {
        return s.replace("&([^&;]+;)".toRegex()) { m ->
            m.groups[1]?.value?.let { n -> htmlEntityTransform(n) } as CharSequence
        }
    }

    // Transform and html entity to character
    fun htmlEntityTransform(s: String): String {
        val entity = s.substring(0, s.lastIndex)

        // Known non-numeric HTML entity
        val code = HtmlEntities.name2Code[entity]
        if (code != null)
            return code

        //HTML5 allows entities without a semicolon. For example,
        //'&Eacuteric' should be decoded as 'Éric'.
        val html5Entity = HtmlEntities.html5Entities[s]
        if (html5Entity != null)
            return html5Entity

        val mobj = """#(x[0-9a-fA-F]+|[0-9]+)""".toRegex().find(entity)
        if (mobj != null && entity.startsWith(mobj.value)) {
            mobj.groups[1]?.value?.let {
                var numstr = it
                val base: Int
                if (numstr.startsWith('x')) {
                    base = 16
                    numstr = "0$numstr"
                } else
                    base = 10
                // See https://github.com/ytdl-org/youtube-dl/issues/7518
                return Integer.parseInt(numstr, base).toString()
            }
        }

        // Unknown entity in name, return its literal representation
        return "&$entity"
    }

    /**
     * Return the content of the tag with the specified ID in the passed HTML document
     */
    fun getElementById(id: String, html: String): String? {
        return getElementByAttribute("id", id, html)
    }

    /**
     * Return the content of the first tag with the specified class in the passed HTML document
     */
    fun getElementByClass(className: String, html: String): String? {
        val retval = getElementsByClass(className, html)
        return if (retval.isNotEmpty()) retval[0] else null
    }

    fun getElementByAttribute(attribute: String, value: String, html: String, escapeValue: Boolean = true): String? {
        val retval = getElementsByAttribute(attribute, value, html, escapeValue)
        return if (retval.isNotEmpty()) retval[0] else null
    }

    /**
     * Return the content of all tags with the specified class in the passed HTML document as a list
     */
    fun getElementsByClass(className: String, html: String): List<String> {
        return getElementsByAttribute(
                "class", escape("""[^\'"]*\b$className\b[^\'"]*"""),
                html, escapeValue = false
        )
    }

    /**
     * Return the content of the tag with the specified attribute in the passed HTML document
     */
    fun getElementsByAttribute(attribute: String, value: String, html: String, escapeValue: Boolean = true)
            : List<String> {
        val attrValue = if (escapeValue) escape(value) else value
        val retList = mutableListOf<String>()
        """(?xs)
        <([a-zA-Z0-9:._-]+)
         (?:\s+[a-zA-Z0-9:._-]+(?:=[a-zA-Z0-9:._-]*|="[^"]*"|='[^']*'|))*?
         \s+${escape(attribute)}=['"]?$attrValue['"]?
         (?:\s+[a-zA-Z0-9:._-]+(?:=[a-zA-Z0-9:._-]*|="[^"]*"|='[^']*'|))*?
        \s*>
        \\(.*?)
        </\1>
        """.toRegex().findAll(html).forEach { m ->
            m.groups.last()?.value?.also { content ->
                var res = content
                if (res.startsWith('"') || res.startsWith("'"))
                    res = res.substring(1, res.lastIndex)

                retList.add(unescapeHtml(res))
            }
        }

        return retList
    }

    /**
     * Escape all the characters in pattern except ASCII letters, numbers and '_'.
     */
    fun escape(pattern: String): String {
        return pattern.replace("[^A-Za-z0-9_]".toRegex()) {
            if (it.value == "\u0000")
                "\\000"
            else
                "\\$it"
        }
    }

    fun removeQuotes(s: String?): String? {
        if (s.isNullOrEmpty() || s.length < 2)
            return s
        for (quote in listOf('"', "'")) {
            if (s.first() == quote && s.last() == quote)
                return s.substring(1, s.lastIndex)
        }
        return s
    }
}