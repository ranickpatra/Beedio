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

abstract class InfoExtractor {

    fun initialize() {
        // TODO
    }

    fun extract(url: String) {
        initialize()
        realExtract(url)
    }

    protected abstract fun realExtract(url: String): HashMap<String, List<String>>

    fun urlResult(url: String, ie: String? = null, videoId: String? = null, videoTitle: String? = null)
            : HashMap<String, List<String>> {
        /*Returns a URL that points to a page that should be processed*/
        // TODO ie should be the class used for getting the info
        return hashMapOf(
                "_type" to listOf("url"),
                "url" to listOf(url)
        ).apply {
            ie?.let { put("ie_key", listOf(it)) }
            videoId?.let { put("id", listOf(it)) }
            videoTitle?.let { put("title", listOf(it)) }
        }
    }

    fun htmlSearchMeta(name: String, html: String, displayName: String? = null, fatal: Boolean = false)
            : String? {
        return htmlSearchMeta(listOf(name), html, displayName, fatal)
    }

    fun htmlSearchMeta(name: List<String>, html: String, displayName: String? = null, fatal: Boolean = false)
            : String? {
        var dispName = displayName
        if (dispName == null)
            dispName = name[0]
        return htmlSearchRegex(metaRegex(name[0]), html)
    }

    fun htmlSearchRegex(pattern: Regex, s: String): String? {
        return pattern.find(s)?.value?.let {
            ExtractorUtils.cleanHtml(it)
        }
    }

    fun metaRegex(prop: String): Regex = """(?isx)<meta
                    (?=[^>]+(?:itemprop|name|property|id|http-equiv)=(["']?)${ExtractorUtils.escape(prop)}\1)
                    [^>]+?content=(["'])(.*?)\2""".toRegex()
}