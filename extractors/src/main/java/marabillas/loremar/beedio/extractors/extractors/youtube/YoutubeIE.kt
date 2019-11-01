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

package marabillas.loremar.beedio.extractors.extractors.youtube

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import marabillas.loremar.beedio.extractors.ExtractorException
import marabillas.loremar.beedio.extractors.ExtractorUtils
import marabillas.loremar.beedio.extractors.JSInterpreter
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL

class YoutubeIE : YoutubeBaseInfoExtractor() {

    private val playerCache = hashMapOf<String, (Any) -> Any?>()
    private val _formats = hashMapOf(
            "5" to hashMapOf("ext" to "flv", "width" to 400, "height" to 240, "acodec" to "mp3",
                    "abf" to 64, "vcodec" to "h263"),
            "6" to hashMapOf("ext" to "flv", "width" to 450, "height" to 270, "acodec" to "mp3",
                    "abr" to 64, "vcodec" to "h263"),
            "13" to hashMapOf<String, Any>("ext" to "3gp", "acodec" to "aac", "vcodec" to "mp4v"),
            "17" to hashMapOf("ext" to "3gp", "width" to 176, "height" to 144, "acodec" to "aac",
                    "abr" to 24, "vcodec" to "mp4v"),
            "18" to hashMapOf("ext" to "mp4", "width" to 640, "height" to 360, "acodec" to "aac",
                    "abr" to 96, "vcodec" to "h264"),
            "22" to hashMapOf("ext" to "mp4", "width" to 1280, "height" to 720, "acodec" to "aac",
                    "abr" to 192, "vcodec" to "h264"),
            "34" to hashMapOf("ext" to "flv", "width" to 640, "height" to 360, "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h264"),
            "35" to hashMapOf("ext" to "flv", "width" to 854, "height" to 480, "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h264"),
            // itag 36 videos are either 320x180 (BaW_jenozKc) or 320x240 (__2ABJjxzNo), abr varies as well
            "36" to hashMapOf("ext" to "3gp", "width" to 320, "acodec" to "aac", "vcodec" to "mp4v"),
            "37" to hashMapOf("ext" to "mp4", "width" to 1920, "height" to 1080, "acodec" to "aac",
                    "abr" to 192, "vcodec" to "h264"),
            "38" to hashMapOf("ext" to "mp4", "width" to 4096, "height" to 3072, "acodec" to "aac",
                    "abr" to 192, "vcodec" to "h264"),
            "43" to hashMapOf("ext" to "webm", "width" to 640, "height" to 360, "acodec" to "vorbis",
                    "abr" to 128, "vcodec" to "vp8"),
            "44" to hashMapOf("ext" to "webm", "width" to 854, "height" to 480, "acodec" to "vorbis",
                    "abr" to 128, "vcodec" to "vp8"),
            "45" to hashMapOf("ext" to "webm", "width" to 1280, "height" to 720, "acodec" to "vorbis",
                    "abr" to 192, "vcodec" to "vp8"),
            "46" to hashMapOf("ext" to "webm", "width" to 1920, "height" to 1080, "acodec" to "vorbis",
                    "abr" to 192, "vcodec" to "vp8"),
            "59" to hashMapOf("ext" to "mp4", "width" to 854, "height" to 480, "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h264"),
            "78" to hashMapOf("ext" to "mp4", "width" to 854, "height" to 480, "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h265"),

            // 3D videos
            "82" to hashMapOf("ext" to "mp4", "height" to 360, "format_note" to "3D", "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h264", "preference" to -20),
            "83" to hashMapOf("ext" to "mp4", "height" to 480, "format_note" to "3D", "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h264", "preference" to -20),
            "84" to hashMapOf("ext" to "mp4", "height" to 720, "format_note" to "3D", "acodec" to "aac",
                    "abr" to 192, "vcodec" to "h264", "preference" to -20),
            "85" to hashMapOf("ext" to "mp4", "height" to 1080, "format_note" to "3D", "acodec" to "aac",
                    "abr" to 192, "vcodec" to "h264", "preference" to -20),
            "100" to hashMapOf("ext" to "webm", "height" to 360, "format_note" to "3D", "acodec" to "vorbis",
                    "abr" to 128, "vcodec" to "vp8", "preference" to -20),
            "101" to hashMapOf("ext" to "webm", "height" to 480, "format_note" to "3D", "acodec" to "vorbis",
                    "abr" to 192, "vcodec" to "vp8", "preference" to -20),
            "102" to hashMapOf("ext" to "webm", "height" to 720, "format_note" to "3D", "acodec" to "vorbis",
                    "abr" to 192, "vcodec" to "vp8", "preference" to -20),

            // Apple HTTP Live Streaming
            "91" to hashMapOf("ext" to "mp4", "height" to 144, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 48, "vcodec" to "h264", "preference" to -10),
            "92" to hashMapOf("ext" to "mp4", "height" to 240, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 48, "vcodec" to "h264", "preference" to -10),
            "93" to hashMapOf("ext" to "mp4", "height" to 360, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h264", "preference" to -10),
            "94" to hashMapOf("ext" to "mp4", "height" to 480, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 128, "vcodec" to "h264", "preference" to -10),
            "95" to hashMapOf("ext" to "mp4", "height" to 720, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 256, "vcodec" to "h264", "preference" to -10),
            "96" to hashMapOf("ext" to "mp4", "height" to 1080, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 256, "vcodec" to "h264", "preference" to -10),
            "132" to hashMapOf("ext" to "mp4", "height" to 240, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 48, "vcodec" to "h264", "preference" to -10),
            "151" to hashMapOf("ext" to "mp4", "height" to 72, "format_note" to "HLS", "acodec" to "aac",
                    "abr" to 24, "vcodec" to "h264", "preference" to -10),

            // DASH mp4 video
            "133" to hashMapOf("ext" to "mp4", "height" to 240, "format_note" to "DASH video", "vcodec" to "h264"),
            "134" to hashMapOf("ext" to "mp4", "height" to 360, "format_note" to "DASH video", "vcodec" to "h264"),
            "135" to hashMapOf("ext" to "mp4", "height" to 480, "format_note" to "DASH video", "vcodec" to "h264"),
            "136" to hashMapOf("ext" to "mp4", "height" to 720, "format_note" to "DASH video", "vcodec" to "h264"),
            "137" to hashMapOf("ext" to "mp4", "height" to 1080, "format_note" to "DASH video", "vcodec" to "h264"),
            "138" to hashMapOf<String, Any>("ext" to "mp4", "format_note" to "DASH video", "vcodec" to "h264"), // Height can vary (https://github.com/ytdl-org/youtube-dl/issues/4559)
            "160" to hashMapOf("ext" to "mp4", "height" to 144, "format_note" to "DASH video", "vcodec" to "h264"),
            "212" to hashMapOf("ext" to "mp4", "height" to 480, "format_note" to "DASH video", "vcodec" to "h264"),
            "264" to hashMapOf("ext" to "mp4", "height" to 1440, "format_note" to "DASH video", "vcodec" to "h264"),
            "298" to hashMapOf("ext" to "mp4", "height" to 720, "format_note" to "DASH video", "vcodec" to "h264"),
            "299" to hashMapOf("ext" to "mp4", "height" to 1080, "format_note" to "DASH video", "vcodec" to "h264"),
            "266" to hashMapOf("ext" to "mp4", "height" to 2160, "format_note" to "DASH video", "vcodec" to "h264"),

            // Dash mp4 audio
            "139" to hashMapOf("ext" to "m4a", "format_note" to "DASH audio", "acodec" to "aac", "abr" to 48,
                    "container" to "m4a_dash"),
            "140" to hashMapOf("ext" to "m4a", "format_note" to "DASH audio", "acodec" to "aac", "abr" to 128,
                    "container" to "m4a_dash"),
            "141" to hashMapOf("ext" to "m4a", "format_note" to "DASH audio", "acodec" to "aac", "abf" to 256,
                    "container" to "m4a_dash"),
            "256" to hashMapOf<String, Any>("ext" to "m4a", "format_note" to "DASH audio", "acodec" to "aac",
                    "container" to "m4a_dash"),
            "258" to hashMapOf<String, Any>("ext" to "m4a", "format_note" to "DASH audio", "acodec" to "aac",
                    "container" to "m4a_dash"),
            "325" to hashMapOf<String, Any>("ext" to "m4a", "format_note" to "DASH audio", "acodec" to "dtse",
                    "container" to "m4a_dash"),
            "328" to hashMapOf<String, Any>("ext" to "m4a", "format_note" to "DASH audio", "acodec" to "ec-3",
                    "container" to "m4a_dash"),

            // Dash webm
            "167" to hashMapOf("ext" to "webm", "height" to 360, "width" to 640, "format_note" to "DASH video",
                    "container" to "webm", "vcodec" to "vp8"),
            "168" to hashMapOf("ext" to "webm", "height" to 480, "width" to 854, "format_note" to "DASH video",
                    "container" to "webm", "vcodec" to "vp8"),
            "169" to hashMapOf("ext" to "webm", "height" to 720, "width" to 1280, "format_note" to "DASH video",
                    "container" to "webm", "vcodec" to "vp8"),
            "170" to hashMapOf("ext" to "webm", "height" to 1080, "width" to 1920, "format_note" to "DASH video",
                    "container" to "webm", "vcodec" to "vp8"),
            "218" to hashMapOf("ext" to "webm", "height" to 480, "width" to 854, "format_note" to "DASH video",
                    "container" to "webm", "vcodec" to "vp8"),
            "219" to hashMapOf("ext" to "webm", "height" to 480, "width" to 854, "format_note" to "DASH video",
                    "container" to "webm", "vcodec" to "vp8"),
            "278" to hashMapOf("ext" to "webm", "height" to 144, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "242" to hashMapOf("ext" to "webm", "height" to 240, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "243" to hashMapOf("ext" to "webm", "height" to 360, "format_note" to "DASH video"
                    , "vcodec" to "vp9"),
            "244" to hashMapOf("ext" to "webm", "height" to 480, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "245" to hashMapOf("ext" to "webm", "height" to 480, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "246" to hashMapOf("ext" to "webm", "height" to 480, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "247" to hashMapOf("ext" to "webm", "height" to 720, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "248" to hashMapOf("ext" to "webm", "height" to 1080, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "271" to hashMapOf("ext" to "webm", "height" to 1440, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            // itag 272 videos are either 3840x2160 (e.g. RtoitU2A-3E) or 7680x4320 (sLprVF6d7Ug)
            "272" to hashMapOf("ext" to "webm", "height" to 2160, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "302" to hashMapOf("ext" to "webm", "height" to 720, "format_note" to "DASH video",
                    "vcodec" to "vp9", "fps" to 60),
            "303" to hashMapOf("ext" to "webm", "height" to 1080, "format_note" to "DASH video",
                    "vcodec" to "vp9", "fps" to 60),
            "308" to hashMapOf("ext" to "webm", "height" to 1440, "format_note" to "DASH video",
                    "vcodec" to "vp9", "fps" to 60),
            "313" to hashMapOf("ext" to "webm", "height" to 2160, "format_note" to "DASH video",
                    "vcodec" to "vp9"),
            "315" to hashMapOf("ext" to "webm", "height" to 2160, "format_note" to "DASH video",
                    "vcodec" to "vp9", "fps" to 60),

            // Dash webm audio
            "171" to hashMapOf("ext" to "webm", "acodec" to "vorbis", "format_note" to "DASH audio", "abr" to 128),
            "172" to hashMapOf("ext" to "webm", "acodec" to "vorbis", "format_note" to "DASH audio", "abr" to 256),

            // Dash web audio with opus inside
            "249" to hashMapOf("ext" to "webm", "format_note" to "DASH audio", "acodec" to "opus", "abr" to 50),
            "250" to hashMapOf("ext" to "webm", "format_note" to "DASH audio", "acodec" to "opus", "abr" to 70),
            "251" to hashMapOf("ext" to "webm", "format_note" to "DASH audio", "acodec" to "opus", "abr" to 160),

            // RTMP (unnamed)
            "rtmp" to hashMapOf<String, Any>("protocol" to "rtmp"),

            // av01 video only formats sometimes served with "unknown" codecs
            "394" to hashMapOf<String, Any>("acodec" to "none", "vcodec" to "av01.0.05M.08"),
            "395" to hashMapOf<String, Any>("acodec" to "none", "vcodec" to "av01.0.05M.08"),
            "396" to hashMapOf<String, Any>("acodec" to "none", "vcodec" to "av01.0.05M.08"),
            "397" to hashMapOf<String, Any>("acodec" to "none", "vcodec" to "av01.0.05M.08")
    )

    override fun realExtract(url: String): HashMap<String, List<String>> {
        var urlx = ExtractorUtils.unsmuggleUrl(url)

        val proto = "http" // 'http' if self._downloader.params.get('prefer_insecure', False) else 'https'

        /*val startTime: Long? = null
        val endTime: Long? = null
        Get startTime and endTime by parsing url into its components
        */

        /*Extract original video URL from URL with redirection, like age verification, using next_url parameter*/

        val videoId = urlx.substringAfter("watch?v=").let {
            if (it.endsWith('/'))
                it.substring(0, it.lastIndex)
            else
                it
        }

        urlx = "$proto://www.youtube.com/watch?v=$videoId&gl=US&hl=en&has_verified=1&bpctr=9999999999"
        val videoWebPage = ExtractorUtils.contentOf(urlx)

        val mObj = videoWebPage?.let {
            """swfConfig.*?"(https?:\\/\\/.*?watch.*?-.*?\.swf)"""".toRegex().find(it)
        }

        var playerUrl = mObj
                ?.groups
                ?.get(1)
                ?.value
                ?.let {
                    it.replace("""\\(.)""".toRegex()) { m ->
                        m.groups[1]?.value as CharSequence
                    }
                }

        val dashMpds = mutableListOf<String>()
        val addDashMpd = { videoInfo: HashMap<String, List<String>> ->
            val dashMpd = videoInfo["dashmpd"]
            if (dashMpd != null && !dashMpds.contains(dashMpd[0]))
                dashMpds.add(dashMpd[0])
        }

        val addDashMpdPr = { plResponse: PlayerResponse ->
            plResponse.streamingData?.dashManifestUrl?.let { dashMpd ->
                if (!dashMpds.contains(dashMpd[0]))
                    dashMpds.add(dashMpd[0])
            }
        }

        var isLive: Boolean? = null
        var viewCount: Int? = null

        val extractViewCount: (HashMap<String, List<String>>) -> Int? = { vInfo ->
            vInfo["viewCount"]?.get(0) as Int?
        }

        val extractToken: (HashMap<String, List<String>>) -> Any? = { vInfo ->
            vInfo["account_playback_token"] ?: vInfo["accountPlaybackToken"] ?: vInfo["token"]
        }

        val extractPlayerResponse: (String, String) -> PlayerResponse? = { playerResponseJson, vId ->
            try {
                PlayerResponse.from(playerResponseJson)
            } catch (e: JsonSyntaxException) {
                // TODO()
                null
            }
        }

        var playerResponse: PlayerResponse? = null

        // Get video info
        var videoInfo: HashMap<String, List<String>>? = null
        var embedWebpage: String? = null
        var ageGate: Boolean
        if (videoWebPage?.let { """player-age-gate-content">""".toRegex().find(it) } != null) {
            ageGate = true
            /*# We simulate the access to the video from www.youtube.com/v/{video_id}
            # this can be viewed without login into Youtube*/
            urlx = "$proto://www.youtube.com/embed/$videoId"
            embedWebpage = ExtractorUtils.contentOf(urlx)
            val data = ExtractorUtils.queryStringFrom(
                    hashMapOf(
                            "video_id" to videoId,
                            "eurl" to "https://youtube.googleapis.com/v/$videoId",
                            "sts" to (embedWebpage?.let {
                                """"sts"\s*:\s*(\d+)""".toRegex().find(it)?.groups?.get(0)?.value
                            } ?: "")
                    )
            )
            val videoInfoUrl = "$proto://www.youtube.com/get_video_info?$data"
            val videoInfoWebPage = ExtractorUtils.contentOf(videoInfoUrl)
            videoInfo = videoInfoWebPage?.let { ExtractorUtils.parseQueryString(it) }
            val plResponse = videoInfo?.get("player_response")?.get(0)
            playerResponse = plResponse?.let { extractPlayerResponse(it, videoId) }
            videoInfo?.let { addDashMpd(it) }
            viewCount = videoInfo?.let { extractViewCount(it) }
        } else {
            ageGate = false
            var sts: String? = null
            // Try looking directly into the video webpage
            val ytplayerConfig = videoWebPage?.let { getYtplayerConfig(videoId, it) }
            if (ytplayerConfig != null) {
                ytplayerConfig.args?.let { args ->
                    if (args.urlEncodedFmtStreamMap.isNotEmpty() || args.hlsvp.isNotEmpty()) {
                        videoInfo = hashMapOf(
                                "url_encoded_fmt_stream_map" to args.urlEncodedFmtStreamMap,
                                "hslvp" to args.hlsvp
                        ).apply(addDashMpd)
                    }
                    /*# Rental video is not rented but preview is available (e.g.
                    # https://www.youtube.com/watch?v=yYr8q0y5Jfg,
                    # https://github.com/ytdl-org/youtube-dl/issues/10532)*/
                    if (videoInfo.isNullOrEmpty() && args.ypcVid != null)
                        return urlResult(args.ypcVid, videoId = args.ypcVid)
                    if (args.livestream == "1" || args.livePlayback == 1)
                        isLive = true
                }
                sts = ytplayerConfig.sts
                if (playerResponse == null)
                    playerResponse = ytplayerConfig.playerResponseJson?.let { extractPlayerResponse(it, videoId) }
            }
            if (videoInfo.isNullOrEmpty()) {// TODO self._downloader.params.get('youtube_include_dash_manifest', True)
                playerResponse?.let { addDashMpdPr(it) }
                /*# We also try looking in get_video_info since it may contain different dashmpd
                # URL that points to a DASH manifest with possibly different itag set (some itags
                # are missing from DASH manifest pointed by webpage's dashmpd, some - from DASH
                # manifest pointed by get_video_info's dashmpd).
                # The general idea is to take a union of itags of both DASH manifests (for example
                # video with such 'manifest behavior' see https://github.com/ytdl-org/youtube-dl/issues/6093)*/
                // TODO self.report_video_info_webpage_download(video_id)
                for (el in listOf("embedded", "detailpage", "vevo", "")) {
                    val query = hashMapOf(
                            "video_id" to videoId,
                            "ps" to "default",
                            "eurl" to "",
                            "gl" to "US",
                            "hl" to "en"
                    )
                    if (el.isNotBlank())
                        query["el"] = el
                    if (!sts.isNullOrBlank())
                        query["sts"] = sts
                    val queryString = ExtractorUtils.queryStringFrom(query)
                    val videoInfoWebpage = ExtractorUtils.contentOf(
                            "$proto://www.youtube.com/get_video_info?$queryString")
                    if (videoInfoWebpage.isNullOrBlank()) continue
                    val getVideoInfo = ExtractorUtils.parseQueryString(videoInfoWebpage)
                    if (playerResponse == null) {
                        val plResponse = getVideoInfo["player_response"]?.get(0)
                        playerResponse = plResponse?.let { extractPlayerResponse(it, videoId) }
                    }
                    addDashMpd(getVideoInfo)
                    if (viewCount == null)
                        viewCount = extractViewCount(getVideoInfo)
                    if (videoInfo == null)
                        videoInfo = getVideoInfo
                    val getToken = extractToken(getVideoInfo)
                    if (getToken != null) {
                        /*# Different get_video_info requests may report different results, e.g.
                        # some may report video unavailability, but some may serve it without
                        # any complaint (see https://github.com/ytdl-org/youtube-dl/issues/7362,
                        # the original webpage as well as el=info and el=embedded get_video_info
                        # requests report video unavailability due to geo restriction while
                        # el=detailpage succeeds and returns valid data). This is probably
                        # due to YouTube measures against IP ranges of hosting providers.
                        # Working around by preferring the first succeeded video_info containing
                        # the token if no such video_info yet was found.*/
                        val token = videoInfo?.let { extractToken(it) }
                        if (token == null)
                            videoInfo = getVideoInfo
                        break
                    }
                }
            }
        }

        val extractUnavailableMessage: () -> String? = {
            val messages = mutableListOf<String>()
            for ((tag, kind) in arrayOf(arrayOf("hi", "message"), arrayOf("div", "submessage"))) {
                val pattern = "(?s)<$tag[^>]+id=[\"']unavailable-$kind[\"'][^>]*>(.+?)</$tag>".toRegex()
                videoWebPage
                        ?.let { htmlSearchRegex(pattern, it) }
                        ?.let { messages.add(it) }
            }
            if (messages.isNotEmpty())
                messages.joinToString("\n")
            else
                null
        }

        if (videoInfo == null) {
            var unavailableMessage = extractUnavailableMessage()
            if (unavailableMessage == null)
                unavailableMessage = "Unable to extract video data"
            throw Exception("Youtube said: $unavailableMessage")
        }

        val videoDetails = playerResponse?.videoDetails
        var videoTitle = videoInfo?.get("title") ?: videoDetails?.get("title")
        if (videoTitle == null)
            videoTitle = "_"

        var videoDescription = videoWebPage?.let { ExtractorUtils.getElementById("eow-description", it) }
        var descriptionOriginal = videoDescription
        if (videoDescription != null) {
            val replaceUrl: (m: MatchResult) -> String = {
                val parsedRedirUrl = URL(URL(urlx), it.groups[1]?.value)
                if (
                        """^(?:www\.)?(?:youtube(?:-nocookie)?\.com|youtu\.be)${'$'}""".toRegex()
                                .find(parsedRedirUrl.authority) != null
                        && parsedRedirUrl.path == "/redirect"
                ) {
                    val qs = ExtractorUtils.parseQueryString(parsedRedirUrl.query)
                    val q = qs["q"]
                    if (!q.isNullOrEmpty() && q[0].isNotBlank())
                        q[0]
                }
                parsedRedirUrl.toString()
            }

            videoDescription = """(?x)
                <a\s+
                    (?:[a-zA-Z-]+="[^"]*"\s+)*?
                    (?:title|href)="([^"]+)"\s+
                    (?:[a-zA-Z-]+="[^"]*"\s+)*?
                    class="[^"]*"[^>]*>
                [^<]+\.{3}\s*
                </a>""".toRegex().replace(videoDescription, replaceUrl)
            descriptionOriginal = videoDescription
            videoDescription = ExtractorUtils.cleanHtml(videoDescription)
        } else {
            videoDescription = videoWebPage?.let {
                htmlSearchMeta("description", it) ?: videoDetails?.get("shortDescription")
            }
        }

        /*TODO if not smuggled_data.get('force_singlefeed', False):*/

        if (viewCount == null)
            viewCount = videoInfo?.let { extractViewCount(it) }
        if (viewCount == null && videoDetails != null)
            viewCount = videoDetails["viewCount"]?.toInt()

        if (isLive == null)
            isLive = videoDetails?.get("isLive") == "true"

        // Check for "rental" videos
        videoInfo?.apply {
            if (contains("ypc_video_rental_bar_text") && !contains("author"))
                TODO("Rental videos being not supported needs to be handled")
        }

        val extractFileSize: (String) -> String? = { mediaUrl ->
            """\bclen[=/](\d+)""".toRegex().find(mediaUrl)?.value
        }

        val streamingFormats = playerResponse?.streamingData?.run {
            formats.toMutableList().apply { addAll(adaptiveFormats) }
        } ?: listOf<Map<String, String>>()

        val formats: HashMap<String, String>
        videoInfo?.let { vInfo ->
            val conn = vInfo["conn"]?.get(0)

            if (conn != null && conn.startsWith("rtmp")) {
                // TODO Report rtmp download
                playerUrl?.let {
                    formats = hashMapOf(
                            "format_id" to "_rtmp",
                            "protocol" to "rtmp",
                            "url" to conn,
                            "player_url" to it
                    )
                }
            } else if (isLive != true && (
                            !streamingFormats.isNullOrEmpty()
                                    || !vInfo["url_encoded_fmt_stream_map"]?.get(0).isNullOrEmpty()
                                    || !vInfo["adaptive_fmts"]?.get(0).isNullOrEmpty()
                            )) {
                val encodedUrlMap = "${vInfo["url_encoded_fmt_stream_map"]?.get(0)
                        ?: ""},${vInfo["adaptive_fmts"]?.get(0) ?: ""}"
                if (encodedUrlMap.contains("rtmpe%3Dyes"))
                    TODO("rtmpe downlaods being not supported needs to be handleed")
                val formatsSpec = hashMapOf<String, HashMap<String, *>>()
                val fmtList = vInfo["fmt_list"]?.get(0) ?: ""
                if (fmtList.isNotBlank()) {
                    fmtList.split(",").forEach { fmt ->
                        val spec = fmt.split("/")
                        if (spec.size > 1) {
                            val widthHeight = spec[1].split("x")
                            if (widthHeight.size == 2) {
                                formatsSpec[spec[0]] = hashMapOf(
                                        "resolution" to spec[1],
                                        "width" to widthHeight[0].toDouble(),
                                        "height" to widthHeight[1].toDouble()
                                )
                            }
                        }
                    }
                }
                for (fmt in streamingFormats) {
                    val itag = fmt["it"]
                    if (itag.isNullOrEmpty())
                        continue
                    val quality = fmt["quality"]
                    val qualityLabel = fmt["qualityLabel"] ?: quality
                    formatsSpec[itag] = hashMapOf(
                            "asr" to fmt["audioSampleRate"]?.toDouble(),
                            "filesize" to fmt["contentLength"]?.toDouble(),
                            "format_note" to qualityLabel,
                            "fps" to fmt["fps"]?.toDouble(),
                            // bitrate for itag 43 is always 2147483647
                            "tbr" to (fmt["averageBitrate"]?.toDouble()
                                    ?: (if (itag != "43") fmt["bitrate"] else null)),
                            "width" to fmt["width"]
                    )
                }
                for (fmt in streamingFormats) {
                    if (fmt["drm_families"] != null)
                        continue
                    var urlz = fmt["url"]
                    var cipher: String? = null
                    val urlData: HashMap<String, List<String>>
                    if (urlz == null) {
                        cipher = fmt["cipher"] ?: continue
                        urlData = ExtractorUtils.parseQueryString(cipher)
                        urlz = urlData["url"]?.get(0)
                        if (urlz == null)
                            continue
                        else
                            urlx = urlz
                    } else {
                        urlx = urlz
                        urlData = ExtractorUtils.parseQueryString(URL(urlx).query)
                    }

                    val streamType = urlData["stream_type"]?.get(0)?.toDouble()
                    // Unsupported FORMAT_STREAM_TYPE_OTF
                    if (streamType == 3.0)
                        continue

                    val formatId = fmt["itag"] ?: urlData["itag"]?.get(0) ?: continue

                    if (cipher != null) {
                        if (urlData.contains("s")) { // TODO or self._downloader.params.get('youtube_include_dash_manifest', True):
                            val ASSETS_RE = """""assets":.+?"js":\s*("[^"]+")""".toRegex()
                            val webPage = if (ageGate && embedWebpage != null)
                                embedWebpage
                            else
                                videoWebPage
                            var jsplayerUrlJson = webPage?.let { ASSETS_RE.find(it) }?.value
                            if (jsplayerUrlJson == null && !ageGate) {
                                // We need the embed website after all
                                if (embedWebpage == null) {
                                    val embedUrl = "$proto://www.youtube.com/embed/$videoId"
                                    embedWebpage = ExtractorUtils.contentOf(embedUrl)
                                }
                                jsplayerUrlJson = embedWebpage?.let { ASSETS_RE.find(it) }?.value
                            }

                            playerUrl = JsonParser.parseString(jsplayerUrlJson).asString
                        }

                        if (urlData.contains("sig"))
                            urlx = "$urlx&signature=${urlData["sig"]?.get(0)}"
                        else if (urlData.contains("s")) {
                            val encryptedSig = urlData["s"]?.get(0)

                            /* TODO if self._downloader.params.get('verbose'):
                            *   ............................................*/

                            val signature = encryptedSig?.let { decryptSignature(it, videoId, playerUrl, ageGate) }
                            val sp = urlData["sp"]?.get(0) ?: "signature"
                            urlx += "&$sp=$signature"
                        }
                    }
                    if (!urlx.contains("ratebypass"))
                        urlx += "&ratebypass=yes"

                    val dct = hashMapOf(
                            "format_id" to formatId,
                            "url" to urlx,
                            "player_url" to playerUrl
                    )
                    if (_formats.contains(formatId))
                        TODO()
                }

            } else {
                TODO()
            }
        }

        TODO()

        return hashMapOf()
    }

    fun getYtplayerConfig(videoId: String, webpage: String): YtplayerConfig? {
        val config = ";ytplayer\\.config\\s*=\\s*(\\{.+?});ytplayer".toRegex().find(webpage)
                ?: ";ytplayer\\.config\\s*=\\s*(\\{.+?});".toRegex().find(webpage)
        return if (config != null)
            YtplayerConfig.from(ExtractorUtils.uppercaseEscape(config.value))
        else
            null
    }

    /**
     * Turn the encrypted s field into a working signature.
     */
    fun decryptSignature(s: String, videoId: String, playerUrl: String?, ageGate: Boolean = false): Any? {
        if (playerUrl == null)
            throw ExtractorException("Cannot decrypt signature without player_url")

        var plUrl = playerUrl
        if (plUrl.startsWith("//"))
            plUrl = "https:$playerUrl"
        else {
            val mObj = """https?://""".toRegex().find(plUrl)?.value
            if (mObj != null && !plUrl.startsWith(mObj)) {
                plUrl = URL(URL("https://www.youtube.com"), plUrl).toString()
            }
        }

        try {
            val playerId = "$plUrl $s"
            if (!playerCache.contains(playerId)) {
                extractSignatureFunction(videoId, playerUrl, s)?.let { func ->
                    playerCache[playerId] = func
                }
            }
            val func = playerCache[playerId]
            /*TODO if self._downloader.params.get('youtube_print_sig_code'):
                self._print_sig_code(func, s)*/
            return func?.invoke(s)
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            throw ExtractorException("Signature extraction failed: $sw")
        }
    }

    fun extractSignatureFunction(videId: String, playerUrl: String, exampleSig: String): ((Any) -> Any?)? {
        val idM = """.*?-([a-zA-Z0-9_-]+)(?:/watch_as3|/html5player(?:-new)?|(?:/[a-z]{2,3}_[A-Z]{2})?/base)?\.([a-z]+)${'$'}"""
                .toRegex().find(playerUrl)
        if (idM == null || !playerUrl.startsWith(idM.value))
            throw ExtractorException("Cannot identify player $playerUrl")
        val playerType = idM.groups.last()?.value
        val playerId = idM.groups[1]?.value

        // Read from filesystem cache
        val funcId = "${playerType}_${playerId}_${exampleSig}"

        /* TODO cache_spec = self._downloader.cache.load('youtube-sigfuncs', func_id)
        if cache_spec is not None:
            return lambda s: ''.join(s[i] for i in cache_spec)*/

        /*TODO download_note = (
            'Downloading player %s' % player_url
            if self._downloader.params.get('verbose') else
            'Downloading %s player %s' % (player_type, player_id)
        )*/

        var res: ((Any) -> Any?)? = null
        when (playerType) {
            "js" -> {
                val code = ExtractorUtils.contentOf(playerUrl)
                res = code?.let { parseSigJs(it) }
            }
            "swf" -> TODO("No implementation to handle playerType == swf")
            else -> assert(false) { "Invalid player type $playerType" }
        }

        /*TODO test_string = ''.join(map(compat_chr, range(len(example_sig))))
        cache_res = res(test_string)
        cache_spec = [ord(c) for c in cache_res]

        self._downloader.cache.store('youtube-sigfuncs', func_id, cache_spec)*/

        return res
    }

    fun parseSigJs(jscode: String): (Any) -> Any? {
        val p1 = """\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\s*\(\s*([a-zA-Z0-9$]+)\(""".toRegex()
        val p2 = """\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\s*\(\s*([a-zA-Z0-9$]+)\(""".toRegex()
        val p3 = """([a-zA-Z0-9$]+)\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\)""".toRegex()
        /*TODO Obsolete patterns
        *  .................*/

        val funcname = p1.find(jscode)?.groups?.last()?.value
                ?: p2.find(jscode)?.groups?.last()?.value
                ?: p3.find(jscode)?.groups?.get(1)?.value

        val jsi = JSInterpreter(jscode)
        val initialFunction = funcname?.let { jsi.extractFunction(it) }
        return { s: Any -> initialFunction?.let { f -> f(listOf(s)) } }
    }
}