package me.hanime.atv.data

/** 首页/搜索/相关推荐里的卡片 */
data class HanimeCard(
    val code: String,
    val title: String,
    val cover: String,
    val duration: String? = null,
    val views: String? = null,
    val uploader: String? = null,
    val timeAgo: String? = null,
)

data class HomeRow(
    val title: String,
    val items: List<HanimeCard>,
)

data class HomePage(
    val banner: HanimeCard?,
    val rows: List<HomeRow>,
    val user: UserInfo? = null,
    val csrf: String? = null,
)

data class UserInfo(
    val id: String,
    val name: String,
    val avatar: String? = null,
)

data class SearchPage(
    val items: List<HanimeCard>,
    val page: Int,
    val hasNext: Boolean,
)

data class VideoSource(
    val label: String,
    val url: String,
    val type: String? = null,
)

data class VideoDetail(
    val code: String,
    val title: String,
    val cover: String,
    val views: String? = null,
    val date: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val artist: String? = null,
    val artistAvatar: String? = null,
    val sources: List<VideoSource> = emptyList(),
    val related: List<HanimeCard> = emptyList(),
    val likeText: String? = null,
    val csrf: String? = null,
    val userId: String? = null,
    val saved: Boolean = false,
) {
    val bestSource: VideoSource? get() = sources.firstOrNull()
}

data class HistoryItem(
    val code: String,
    val title: String,
    val cover: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

data class FavoritePage(
    val items: List<HanimeCard>,
    val page: Int,
    val hasNext: Boolean,
)
