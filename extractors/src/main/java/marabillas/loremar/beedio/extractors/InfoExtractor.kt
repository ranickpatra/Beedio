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

import marabillas.loremar.beedio.extractors.ExtractorUtils.parseCodecs
import marabillas.loremar.beedio.extractors.ExtractorUtils.parseM3u8Attributes
import java.net.URL

abstract class InfoExtractor {

    fun initialize() {
        // TODO
    }

    fun extract(url: String): Map<String, Any?> {
        initialize()
        return realExtract(url)
    }

    protected abstract fun realExtract(url: String): Map<String, Any?>

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

    /**
     * Regex search a string and return the first matching group value
     */
    fun searchRegex(pattern: Regex, string: String, group: Int? = null): String? {
        val mobj = pattern.find(string)
        if (mobj != null) {
            if (group == null) {
                for (i in 1..mobj.groupValues.lastIndex) {
                    if (mobj.groupValues[i].isNotEmpty())
                        return mobj.groupValues[i]
                }
            } else {
                return mobj.groupValues[group]
            }
        }
        return null
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
        return searchRegex(pattern, s)?.let {
            ExtractorUtils.cleanHtml(it).trim()
        }
    }

    fun metaRegex(prop: String): Regex = """(?isx)<meta
                    (?=[^>]+(?:itemprop|name|property|id|http-equiv)=(["']?)${ExtractorUtils.escape(prop)}\1)
                    [^>]+?content=(["'])(.*?)\2""".toRegex()

    fun extractM3u8Formats(m3u8Url: String, videoId: String, ext: String? = null, entryProtocol: String = "m3u8",
                           preference: String? = null, m3u8Id: String? = null, not: String? = null, errnote: String? = null,
                           fatal: Boolean = true, live: Boolean = false)
            : List<HashMap<String, Any?>> {
        val res = ExtractorUtils.extractResponseFrom(m3u8Url) ?: return listOf()

        val m3u8Doc = res.body.toString()
        val urlh = res.request
        val m3u8Urlx = urlh.url.toString()

        return parseM3u8Formats(m3u8Doc, m3u8Urlx,
                ext = ext, entryProtocol = entryProtocol, preference = preference, m3u8Id = m3u8Id,
                live = live)
    }

    fun parseM3u8Formats(m3u8Doc: String, m3u8Url: String, ext: String? = null, entryProtocol: String? = "m3u8",
                         preference: String? = null, m3u8Id: String? = null, live: Boolean = false)
            : List<HashMap<String, Any?>> {
        if (m3u8Doc.contains("#EXT-X-FAXS-CM:"))
            return listOf()
        if ("""#EXT-X-SESSION-KEY:.*?URI="skd://""".toRegex().find(m3u8Doc) != null)
            return listOf()

        val formats = mutableListOf<HashMap<String, Any?>>()

        val formatUrl: (u: String) -> String = {
            val mObj = """^https?://""".toRegex().find(it)
            if (mObj != null && it.startsWith(mObj.value))
                it
            else
                URL(URL(m3u8Url), it).toString()
        }

        /*# References:
        # 1. https://tools.ietf.org/html/draft-pantos-http-live-streaming-21
        # 2. https://github.com/ytdl-org/youtube-dl/issues/12211
        # 3. https://github.com/ytdl-org/youtube-dl/issues/18923

        # We should try extracting formats only from master playlists [1, 4.3.4],
        # i.e. playlists that describe available qualities. On the other hand
        # media playlists [1, 4.3.3] should be returned as is since they contain
        # just the media without qualities renditions.
        # Fortunately, master playlist can be easily distinguished from media
        # playlist based on particular tags availability. As of [1, 4.3.3, 4.3.4]
        # master playlist tags MUST NOT appear in a media playist and vice versa.
        # As of [1, 4.3.3.1] #EXT-X-TARGETDURATION tag is REQUIRED for every
        # media playlist and MUST NOT appear in master playlist thus we can
        # clearly detect media playlist with this criterion.*/

        if (m3u8Doc.contains("#EXT-X-TARGETDURATION"))
            return listOf(hashMapOf(
                    "url" to m3u8Url as Any?,
                    "format_id" to m3u8Id as Any?,
                    "ext" to ext as Any?,
                    "protocol" to entryProtocol as Any?,
                    "preference" to preference as Any?
            ))

        val groups = hashMapOf<String, MutableList<HashMap<String, Any?>>>()
        var lastStreamInf = hashMapOf<String, Any?>()

        fun extracMedia(xMediaLine: String) {
            val media = parseM3u8Attributes(xMediaLine)
            // As per [1, 4.3.4.1] TYPE, GROUP-ID and NAME are REQUIRED
            val mediaType = media["TYPE"]
            val groupId = media["GROUP-ID"]
            val name = media["NAME"]
            if (mediaType == null && groupId == null && name == null)
                return
            if (groupId is String) {
                groups.getOrPut(groupId, { mutableListOf() }).add(media)
            }
            if (mediaType != "VIDEO" && mediaType != "AUDIO")
                return
            val mediaUrl = media["URI"]
            if (mediaUrl != null) {
                val formatId = mutableListOf<Any?>()
                for (v in listOf(m3u8Id, groupId, name)) {
                    if (v != null)
                        formatId.add(v)
                }
                val f = hashMapOf<String, Any?>().apply {
                    put("format_id", formatId.joinToString("-"))
                    if (mediaUrl is String)
                        put("url", formatUrl(mediaUrl))
                    put("manifest_url", m3u8Url)
                    put("language", media["LANGUAGE"])
                    put("ext", ext)
                    put("protocol", entryProtocol)
                    put("preference", preference)
                }
                if (mediaType == "AUDIO")
                    f["vcodec"] = null
                formats.add(f)
            }
        }

        fun buildStreamName(): String? {
            /*# Despite specification does not mention NAME attribute for
            # EXT-X-STREAM-INF tag it still sometimes may be present (see [1]
            # or vidio test in TestInfoExtractor.test_parse_m3u8_formats)
            # 1. http://www.vidio.com/watch/165683-dj_ambred-booyah-live-2015*/
            var streamName = lastStreamInf["NAME"]
            if (streamName is String && streamName.isNotBlank())
                return streamName
            /*# If there is no NAME in EXT-X-STREAM-INF it will be obtained
            # from corresponding rendition group*/
            val streamGroupId = lastStreamInf["VIDEO"]
            if (streamGroupId == null || (streamGroupId is String && streamGroupId.isBlank()))
                return null
            val streamGroup = groups[streamGroupId]
            if (streamGroup.isNullOrEmpty() && streamGroupId is String)
                return streamGroupId
            val rendition = streamGroup?.get(0)
            streamName = rendition?.get("NAME")
            return when {
                streamName is String -> streamName
                streamGroupId is String -> streamGroupId
                else -> null
            }
        }

        /*# parse EXT-X-MEDIA tags before EXT-X-STREAM-INF in order to have the
        # chance to detect video only formats when EXT-X-STREAM-INF tags
        # precede EXT-X-MEDIA tags in HLS manifest such as [3].*/
        for (line in m3u8Doc.split("""[\n\r]+""".toRegex()))
            if (line.startsWith("#EXT-X-MEDIA:"))
                extracMedia(line)

        for (line in m3u8Doc.split("""[\n\r]+""".toRegex())) {
            if (line.startsWith("#EXT-X-STREAM-INF:"))
                lastStreamInf = parseM3u8Attributes(line)
            else if (line.startsWith("#") || line.isBlank())
                continue
            else {
                val tbr = (lastStreamInf["AVERAGE-BANDWIDTH"] ?: lastStreamInf["BANDWIDTH"])?.let {
                    if (it is String) it.toFloat() / 1000f else null
                }
                val formatId = mutableListOf<String>()
                if (!m3u8Id.isNullOrBlank())
                    formatId.add(m3u8Id)
                val streamName = buildStreamName()
                /*# Bandwidth of live streams may differ over time thus making
                # format_id unpredictable. So it's better to keep provided
                # format_id intact.*/
                if (!live)
                    formatId.add(
                            if (!streamName.isNullOrBlank())
                                streamName
                            else
                                "${tbr ?: formats.count()}"
                    )
                val manifestUrl = formatUrl(line.trim())
                val f = hashMapOf(
                        "format_id" to formatId.joinToString("-"),
                        "url" to manifestUrl,
                        "manifest_url" to m3u8Url,
                        "tbr" to tbr,
                        "ext" to ext,
                        "protocol" to entryProtocol,
                        "preference" to preference
                ).apply {
                    val fps = lastStreamInf["FRAME-RATE"]
                    if (fps is String)
                        put("fps", fps.toFloat())
                }
                val resolution = lastStreamInf["RESOLUTION"]
                if (resolution is String && resolution.isNotBlank()) {
                    val mobj = """(\d+)[xX](\d+)""".toRegex().find(resolution)
                    if (mobj != null) {
                        f["width"] = mobj.groups[1]?.value?.toInt()
                        f["height"] = mobj.groups[2]?.value?.toInt()
                    }
                }
                // Unified Streaming Platform
                val mobj = """audio.*?(?:%3D|=)(\d+)(?:-video.*?(?:%3D|=)(\d+))?""".toRegex()
                        .find(f["url"].toString())
                if (mobj != null) {
                    val abr = mobj.groups[1]?.value?.toFloat()?.div(1000f)
                    val vbr = mobj.groups[2]?.value?.toFloat()?.div(1000f)
                    f.putAll(hashMapOf("vbr" to vbr, "abr" to abr))
                }
                val codecs = parseCodecs(lastStreamInf["CODECS"] as String?)
                f.putAll(codecs)
                val audioGroupId = lastStreamInf["AUDIO"]
                /*# As per [1, 4.3.4.1.1] any EXT-X-STREAM-INF tag which
                # references a rendition group MUST have a CODECS attribute.
                # However, this is not always respected, for example, [2]
                # contains EXT-X-STREAM-INF tag which references AUDIO
                # rendition group but does not have CODECS and despite
                # referencing an audio group it represents a complete
                # (with audio and video) format. So, for such cases we will
                # ignore references to rendition groups and treat them
                # as complete formats.*/
                val vcodec = f["vcodec"]
                if (audioGroupId is String && audioGroupId.isNotBlank() && !codecs.isNullOrEmpty()
                        && vcodec is String && vcodec.isNotBlank() && vcodec != "none") {
                    val audioGroup = groups[audioGroupId]
                    if (!audioGroup.isNullOrEmpty() && audioGroup[0]["URI"] != null) {
                        /*# TODO: update acodec for audio only formats with
                        # the same GROUP-ID*/
                        f["acodec"] = null
                    }
                    formats.add(f)
                    lastStreamInf = hashMapOf()
                }
            }
        }
        return formats
    }
}