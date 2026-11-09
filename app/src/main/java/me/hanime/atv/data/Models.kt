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
    val rating: String? = null,
    /**
     * 封面是竖图（2:3 海报）。
     * 站点的类型/标签结果页（`div.search-videos`）用的是 `image/cover/xxx.jpg`
     * 竖版海报，首页和搜索页则是 `image/thumbnail/xxxl.jpg` 横版缩略图；
     * 用同一套 16:9 卡片会把竖图截得只剩中间一条，所以单独标记。
     */
    val portrait: Boolean = false,
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

// ---------------------------------------------------------------- 筛选

/**
 * 站点搜索页的排序方式，value 直接作为 `?sort=` 参数（站点使用中文值）。
 */
enum class SortOption(val label: String, val value: String) {
    LatestRelease("最新上市", "最新上市"),
    LatestUpload("最新上傳", "最新上傳"),
    DailyRank("本日排行", "本日排行"),
    WeekRank("本週排行", "本週排行"),
    MonthRank("本月排行", "本月排行"),
    Views("觀看次數", "觀看次數"),
    LikeRatio("讚好比例", "讚好比例"),
    Longest("時長最長", "時長最長"),
    Watching("他們在看", "他們在看"),
}

/**
 * 与站点 `/search` 一一对应的筛选条件：
 * - query  → `?query=`
 * - genre  → `?genre=`
 * - tags   → `?tags[]=`（可多选）
 * - sort   → `?sort=`
 */
data class SearchFilter(
    val query: String = "",
    val genre: String? = null,
    val tags: List<String> = emptyList(),
    val sort: SortOption = SortOption.LatestUpload,
) {
    val isDefault: Boolean
        get() = query.isBlank() && genre.isNullOrBlank() && tags.isEmpty() && sort == SortOption.LatestUpload

    val activeCount: Int
        get() = (if (!genre.isNullOrBlank()) 1 else 0) +
            tags.size +
            (if (sort != SortOption.LatestUpload) 1 else 0) +
            (if (query.isNotBlank()) 1 else 0)

    val summary: String
        get() = buildList {
            if (query.isNotBlank()) add("关键词：$query")
            genre?.takeIf { it.isNotBlank() }?.let { add(it) }
            tags.forEach { add("#$it") }
            if (sort != SortOption.LatestUpload) add(sort.label)
        }.joinToString(" · ")
}

/** 站点顶部的影片类型（`?genre=`） */
object Genres {
    val all: List<String> = listOf(
        "裏番", "泡麵番", "Motion Anime", "3DCG", "2.5D",
        "2D動畫", "AI生成", "MMD", "Cosplay", "新番預告",
    )
}

/** 站点的常用内容标签（`?tags[]=`），按站点筛选面板的分类组织 */
object TagCatalog {
    val groups: List<Pair<String, List<String>>> = listOf(
        "影片屬性" to listOf(
            "無碼", "AI解碼", "中文字幕", "中文配音", "同人作品", "斷面圖", "ASMR", "1080p", "60FPS",
        ),
        "人物關係" to listOf(
            "近親", "姐", "妹", "母", "女兒", "師生", "情侶", "青梅竹馬", "同事",
        ),
        "角色設定" to listOf(
            "JK", "處女", "御姐", "熟女", "人妻", "女教師", "護士", "OL", "女警", "大小姐",
            "偶像", "女僕", "巫女", "魔女", "修女", "公主", "女忍者", "女戰士", "女騎士", "魔法少女",
            "精靈", "魅魔", "獸娘", "碧池", "痴女", "雌小鬼", "傲嬌", "病嬌", "正太", "偽娘",
        ),
        "外貌身材" to listOf(
            "短髮", "馬尾", "雙馬尾", "巨乳", "貧乳", "黑皮膚", "曬痕", "眼鏡娘", "獸耳",
            "水手服", "睡衣", "泳裝", "死庫水", "和服", "兔女郎", "圍裙", "絲襪", "吊襪帶", "迷你裙",
            "性感內衣", "緊身衣", "高跟鞋", "旗袍", "口罩", "刺青", "淫紋",
        ),
        "情境場所" to listOf(
            "校園", "教室", "圖書館", "保健室", "游泳池", "愛情賓館", "醫院", "辦公室", "浴室",
            "公共廁所", "戶外野戰", "電車", "健身房", "沙灘", "溫泉", "夜店", "監獄", "教堂",
        ),
        "故事劇情" to listOf(
            "純愛", "戀愛喜劇", "後宮", "NTR", "精神控制", "藥物", "痴漢", "獵奇", "BDSM",
            "綑綁", "調教", "肉便器", "性奴隸", "強制", "輪姦", "凌辱", "逆強制", "榨精",
            "母女丼", "姐妹丼", "出軌", "攝影", "睡眠姦", "百合", "時間停止", "異世界", "怪獸", "世界末日",
        ),
        "性交體位" to listOf(
            "手交", "指交", "乳交", "肛交", "腳交", "3P", "群交", "口交", "深喉嚨",
            "口爆", "吞精", "自慰", "69", "內射", "外射", "顏射", "潮吹", "懷孕", "噴奶",
            "騎乘位", "背後位", "側面位", "玩具", "跳蛋", "觸手", "接吻", "舌吻", "POV",
        ),
    )

    val popular: List<String> = listOf(
        "中文字幕", "無碼", "巨乳", "NTR", "純愛", "JK", "人妻", "後宮",
        "青梅竹馬", "校園", "癡漢", "調教", "觸手", "百合", "3P", "絲襪",
    )
}

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

// ---------------------------------------------------------------- 订阅作者

/** 站点 `/subscriptions` 页顶部列出的已订阅作者 */
data class SubscriptionArtist(
    val name: String,
    val avatar: String? = null,
    /** 站点给出的统计文字，例如 “12 部影片” */
    val countText: String? = null,
)

/** 订阅页：作者列表 + 他们的最新作品（分页） */
data class SubscriptionsPage(
    val artists: List<SubscriptionArtist> = emptyList(),
    val items: List<HanimeCard> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    /** 会话失效时站点会重定向到登录页，界面据此提示重新登录 */
    val needsLogin: Boolean = false,
)

// ---------------------------------------------------------------- 登录 / 更新

sealed interface LoginResult {
    data class Success(val user: UserInfo) : LoginResult
    data class Failure(val message: String) : LoginResult
}

/** 手机扫码登录的配对状态 */
data class PhoneLoginState(
    val active: Boolean = false,
    val url: String? = null,
    val status: String = "正在启动局域网服务…",
    val error: String? = null,
    val phoneConnected: Boolean = false,
    val finished: Boolean = false,
)

data class UpdateInfo(
    /** 去掉 v 前缀的版本号，例如 1.1.0 */
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
    val publishedAt: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float, val bytes: Long) : UpdateState
    data class Ready(val info: UpdateInfo, val file: java.io.File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
