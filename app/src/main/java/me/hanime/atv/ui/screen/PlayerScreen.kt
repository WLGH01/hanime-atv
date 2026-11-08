package me.hanime.atv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import me.hanime.atv.data.HanimeClient
import me.hanime.atv.data.VideoDetail
import me.hanime.atv.data.VideoSource
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.TvFocusable
import me.hanime.atv.ui.theme.Palette
import java.util.Locale

// ---------------------------------------------------------------- 常量

/** 控制层无操作后自动淡出的时间 */
private const val AUTO_HIDE_MS = 5_000L

/** 快进 / 快退步长（也是进度条左右键步长） */
private const val SEEK_STEP_MS = 10_000L

/** 断点续播的最小位置，太小就没必要续播 */
private const val RESUME_MIN_MS = 10_000L

/** 站点 CDN 需要的 Referer，否则可能 403 */
private const val REFERER = "https://hanime1.me/"

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private enum class PlayerMenu { None, Quality, Speed }

// ---------------------------------------------------------------- 工具

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = (ms + 500L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun speedText(speed: Float): String {
    val percent = speed * 100f
    val text = when {
        percent % 100f == 0f -> String.format(Locale.US, "%.1f", speed)
        percent % 10f == 0f -> String.format(Locale.US, "%.2f", speed).dropLast(1)
        else -> String.format(Locale.US, "%.2f", speed)
    }
    return "${text}x"
}

private fun sourceLabel(source: VideoSource?, index: Int): String =
    source?.label?.takeIf { it.isNotBlank() } ?: "线路 ${index + 1}"

// ---------------------------------------------------------------- 入口

/**
 * 播放页：Media3 ExoPlayer 负责解码，画面交给 PlayerView（关闭自带控制器），
 * 控制层完全由 Compose 绘制，遥控器 D-pad 全键可达。
 */
@Composable
fun PlayerScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val video = vm.playingVideo
    if (video == null) {
        PlaybackProblem(
            title = null,
            message = "没有正在播放的视频，可能已经退出了播放页",
            onBack = { vm.goBack() },
            modifier = modifier,
        )
        return
    }
    if (video.sources.isEmpty()) {
        PlaybackProblem(
            title = video.title,
            message = "没有解析到可播放的视频地址，请返回详情页重新解析",
            onBack = { vm.goBack() },
            modifier = modifier,
        )
        return
    }
    PlayerContent(vm = vm, video = video, modifier = modifier)
}

// ---------------------------------------------------------------- 主体

@Composable
private fun PlayerContent(vm: AppViewModel, video: VideoDetail, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sources = remember(video.code) { video.sources }
    val resumeMs = remember(video.code) { vm.resumePosition(video.code) }

    // 播放器与播放状态
    var exoPlayer by remember(video.code) { mutableStateOf<ExoPlayer?>(null) }
    var sourceIndex by remember(video.code) { mutableIntStateOf(0) }
    var speed by remember(video.code) { mutableFloatStateOf(1f) }
    var playWhenReadyFlag by remember(video.code) { mutableStateOf(true) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // 断点续播 / 切源定位
    var pendingSeekMs by remember(video.code) {
        mutableLongStateOf(if (resumeMs > RESUME_MIN_MS) resumeMs else 0L)
    }
    var resumeChecked by remember(video.code) { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    // 控制层状态
    var controlsVisible by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(PlayerMenu.None) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintTick by remember { mutableIntStateOf(0) }
    var rootFocused by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }

    // ------------------------------------------------------------ 小动作

    fun bump() {
        interactionTick += 1
    }

    fun reveal() {
        bump()
        if (!controlsVisible) controlsVisible = true
    }

    fun currentDuration(): Long {
        val p = exoPlayer ?: return 0L
        return if (p.duration > 0L) p.duration else 0L
    }

    fun seekTo(targetMs: Long) {
        val p = exoPlayer ?: return
        val upper = currentDuration().takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = targetMs.coerceIn(0L, upper)
        p.seekTo(target)
        positionMs = target
        bump()
    }

    fun seekBy(deltaMs: Long) {
        val p = exoPlayer ?: return
        val upper = currentDuration().takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (p.currentPosition + deltaMs).coerceIn(0L, upper)
        p.seekTo(target)
        positionMs = target
        hint = if (deltaMs >= 0L) "快进 ${deltaMs / 1000} 秒" else "快退 ${-deltaMs / 1000} 秒"
        hintTick += 1
        bump()
    }

    fun togglePlay() {
        val p = exoPlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
        bump()
    }

    fun switchQuality(index: Int) {
        val p = exoPlayer ?: return
        if (index == sourceIndex) return
        val source = sources.getOrNull(index) ?: return
        pendingSeekMs = p.currentPosition.coerceAtLeast(0L)
        positionMs = pendingSeekMs
        playWhenReadyFlag = p.playWhenReady
        sourceIndex = index
        durationMs = 0L
        playerError = null
        hint = "清晰度：${sourceLabel(source, index)}"
        hintTick += 1
        bump()
    }

    fun changeSpeed(value: Float) {
        speed = value
        exoPlayer?.setPlaybackSpeed(value)
        hint = "倍速：${speedText(value)}"
        hintTick += 1
        bump()
    }

    fun retryPlayback() {
        val p = exoPlayer ?: return
        pendingSeekMs = p.currentPosition.coerceAtLeast(0L)
        playWhenReadyFlag = true
        playerError = null
        reloadTick += 1
        bump()
    }

    fun saveAndExit() {
        val p = exoPlayer
        if (p != null) vm.saveProgress(video, p.currentPosition, p.duration)
        vm.goBack()
    }

    // ------------------------------------------------------------ 网络数据源

    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(HanimeClient.USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to HanimeClient.USER_AGENT,
                    "Referer" to REFERER,
                ),
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
    }
    val mediaSourceFactory = remember { DefaultMediaSourceFactory(dataSourceFactory) }

    // ------------------------------------------------------------ 播放器生命周期

    DisposableEffect(video.code) {
        val instance = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        instance.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        instance.setHandleAudioBecomingNoisy(true)
        exoPlayer = instance
        onDispose {
            vm.saveProgress(video, instance.currentPosition, instance.duration)
            exoPlayer = null
            instance.release()
        }
    }

    DisposableEffect(exoPlayer) {
        val p = exoPlayer
        if (p == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (!playing) {
                        // 暂停即落盘，避免突然退出丢进度
                        vm.saveProgress(video, p.currentPosition, p.duration)
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    when (state) {
                        Player.STATE_READY -> {
                            val duration = p.duration
                            if (duration > 0L) durationMs = duration
                            if (!resumeChecked && sourceIndex == 0) {
                                resumeChecked = true
                                if (resumeMs > RESUME_MIN_MS && duration > 0L) {
                                    if (resumeMs < duration - RESUME_MIN_MS) {
                                        vm.showToast("已从上次播放位置继续播放")
                                    } else {
                                        // 上次已经看到结尾了，从头开始
                                        p.seekTo(0L)
                                    }
                                }
                            }
                        }

                        Player.STATE_ENDED -> {
                            vm.saveProgress(video, p.currentPosition, p.duration)
                            vm.goBack()
                        }

                        else -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    playerError = "播放出错：${error.errorCodeName}"
                }
            }
            p.addListener(listener)
            onDispose { p.removeListener(listener) }
        }
    }

    // 装载媒体源：首次进入 / 切换清晰度 / 重试
    LaunchedEffect(exoPlayer, sourceIndex, reloadTick) {
        val p = exoPlayer ?: return@LaunchedEffect
        val source = sources.getOrNull(sourceIndex) ?: return@LaunchedEffect
        isBuffering = true
        p.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.url)))
        p.setPlaybackSpeed(speed)
        p.prepare()
        if (pendingSeekMs > 0L) p.seekTo(pendingSeekMs)
        p.playWhenReady = playWhenReadyFlag
    }

    // 进度轮询 + 每 5 秒落盘
    LaunchedEffect(exoPlayer) {
        val p = exoPlayer ?: return@LaunchedEffect
        var tick = 0
        while (true) {
            positionMs = p.currentPosition.coerceAtLeast(0L)
            bufferedMs = p.bufferedPosition.coerceAtLeast(0L)
            if (p.duration > 0L) durationMs = p.duration
            if (tick % 10 == 0 && p.duration > 0L) {
                vm.saveProgress(video, p.currentPosition, p.duration)
            }
            tick += 1
            delay(500)
        }
    }

    // ------------------------------------------------------------ 控制层自动隐藏 / 焦点

    LaunchedEffect(controlsVisible, interactionTick, menu, playerError) {
        if (!controlsVisible || menu != PlayerMenu.None || playerError != null) {
            return@LaunchedEffect
        }
        delay(AUTO_HIDE_MS)
        runCatching { rootFocus.requestFocus() }
        controlsVisible = false
    }

    LaunchedEffect(controlsVisible, menu, playerError) {
        if (playerError != null) return@LaunchedEffect
        if (controlsVisible && menu == PlayerMenu.None) {
            delay(90)
            runCatching { seekFocus.requestFocus() }
        } else if (!controlsVisible) {
            runCatching { rootFocus.requestFocus() }
        }
    }

    LaunchedEffect(menu, playerError) {
        if (playerError == null && menu != PlayerMenu.None) {
            delay(70)
            runCatching { menuFocus.requestFocus() }
        }
    }

    LaunchedEffect(hintTick) {
        if (hint != null) {
            delay(1_100)
            hint = null
        }
    }

    // 返回键：先关菜单，否则保存进度并返回
    BackHandler {
        if (menu != PlayerMenu.None) {
            menu = PlayerMenu.None
            bump()
        } else {
            saveAndExit()
        }
    }

    // ------------------------------------------------------------ 根按键

    val onRootKey: (KeyEvent) -> Boolean = { event ->
        val key = event.key
        val isNavKey = key == Key.DirectionCenter || key == Key.Enter ||
            key == Key.DirectionUp || key == Key.DirectionDown ||
            key == Key.DirectionLeft || key == Key.DirectionRight
        when {
            event.type != KeyEventType.KeyDown -> false
            // 控制层隐藏且没有错误时：任意方向键唤出控制层
            !controlsVisible && playerError == null -> when {
                key == Key.DirectionCenter || key == Key.Enter -> {
                    reveal()
                    togglePlay()
                    true
                }

                key == Key.DirectionDown || key == Key.DirectionUp -> {
                    reveal()
                    true
                }

                key == Key.DirectionLeft -> {
                    reveal()
                    seekBy(-SEEK_STEP_MS)
                    true
                }

                key == Key.DirectionRight -> {
                    reveal()
                    seekBy(SEEK_STEP_MS)
                    true
                }

                else -> false
            }

            // 控制层可见但焦点仍然停在根节点（例如请求焦点失败）：做一层兜底
            rootFocused -> when {
                key == Key.DirectionCenter || key == Key.Enter -> {
                    togglePlay()
                    true
                }

                key == Key.DirectionLeft -> {
                    seekBy(-SEEK_STEP_MS)
                    true
                }

                key == Key.DirectionRight -> {
                    seekBy(SEEK_STEP_MS)
                    true
                }

                key == Key.DirectionDown || key == Key.DirectionUp -> {
                    reveal()
                    runCatching { seekFocus.requestFocus() }
                    true
                }

                else -> false
            }

            // 控制层可见：只重置隐藏计时，方向键交给焦点系统
            else -> {
                if (isNavKey) bump()
                false
            }
        }
    }

    // ------------------------------------------------------------ UI

    val currentSource = sources.getOrNull(sourceIndex)
    val qualityLabel = sourceLabel(currentSource, sourceIndex)
    val speedLabel = speedText(speed)
    val title = video.title

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onKeyEvent(onRootKey),
    ) {
        // 视频画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    keepScreenOn = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = exoPlayer },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (playerError == null) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = Palette.Accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp),
                )
            }

            // 快进 / 快退 / 切清晰度的短提示
            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(240)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-120).dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = hint.orEmpty(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 控制层
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(280)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // 顶部渐变遮罩
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xD9000000), Color(0x00000000)),
                                ),
                            ),
                    )
                    // 底部渐变遮罩
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x00000000), Color(0xCC000000), Color(0xF2000000)),
                                ),
                            ),
                    )

                    TopInfo(
                        title = title,
                        quality = qualityLabel,
                        speed = speedLabel,
                        artist = video.artist,
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                    BottomControls(
                        menu = menu,
                        qualityLabel = qualityLabel,
                        speedLabel = speedLabel,
                        sources = sources,
                        sourceIndex = sourceIndex,
                        speed = speed,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        bufferedMs = bufferedMs,
                        durationMs = durationMs,
                        seekFocus = seekFocus,
                        menuFocus = menuFocus,
                        onTogglePlay = { togglePlay() },
                        onSeekDelta = { delta -> seekBy(delta) },
                        onSeekTo = { target -> seekTo(target) },
                        onOpenMenu = { target ->
                            menu = target
                            bump()
                        },
                        onSelectQuality = { index ->
                            switchQuality(index)
                            menu = PlayerMenu.None
                            bump()
                        },
                        onSelectSpeed = { index ->
                            changeSpeed(SPEED_OPTIONS[index])
                            menu = PlayerMenu.None
                            bump()
                        },
                        onInteract = { bump() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        } else {
            val message = playerError ?: ""
            PlayerErrorPanel(
                message = message,
                onRetry = { retryPlayback() },
                onBack = { saveAndExit() },
            )
        }
    }
}

// ---------------------------------------------------------------- 顶部信息

@Composable
private fun TopInfo(
    title: String,
    quality: String,
    speed: String,
    artist: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 40.dp, end = 120.dp, top = 28.dp),
    ) {
        Text(
            text = title,
            color = Palette.TextPrimary,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InfoChip(text = quality, accent = true)
            InfoChip(text = speed)
            if (!artist.isNullOrBlank()) InfoChip(text = artist)
        }
    }
}

@Composable
private fun InfoChip(text: String, accent: Boolean = false) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (accent) Palette.Accent.copy(alpha = 0.22f) else Color(0x4D000000))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = if (accent) Palette.AccentSoft else Palette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------- 底部控制条

@Composable
private fun BottomControls(
    menu: PlayerMenu,
    qualityLabel: String,
    speedLabel: String,
    sources: List<VideoSource>,
    sourceIndex: Int,
    speed: Float,
    isPlaying: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    seekFocus: FocusRequester,
    menuFocus: FocusRequester,
    onTogglePlay: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenMenu: (PlayerMenu) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSpeed: (Int) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp)
            .padding(bottom = 22.dp),
    ) {
        if (menu != PlayerMenu.None) {
            val isQuality = menu == PlayerMenu.Quality
            val options = if (isQuality) {
                sources.mapIndexed { index, source -> sourceLabel(source, index) }
            } else {
                SPEED_OPTIONS.map { speedText(it) }
            }
            val selected = if (isQuality) sourceIndex else SPEED_OPTIONS.indexOf(speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SelectorMenu(
                    title = if (isQuality) "选择清晰度" else "播放倍速",
                    options = options,
                    selected = selected,
                    firstItemFocus = menuFocus,
                    onSelect = { index ->
                        if (isQuality) onSelectQuality(index) else onSelectSpeed(index)
                    },
                    onInteract = onInteract,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, barShape)
                .clip(barShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xE6141420), Color(0xE61C1A2B)),
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            SeekBar(
                positionMs = positionMs,
                bufferedMs = bufferedMs,
                durationMs = durationMs,
                focusRequester = seekFocus,
                onSeekDelta = onSeekDelta,
                onSeekTo = onSeekTo,
                onTogglePlay = onTogglePlay,
                onInteract = onInteract,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlTile(onClick = onTogglePlay, onFocused = onInteract) {
                    PlayGlyph(isPlaying)
                }
                Spacer(Modifier.width(12.dp))
                ControlTile(onClick = { onSeekDelta(-SEEK_STEP_MS) }, onFocused = onInteract) {
                    SkipGlyph(forward = false)
                }
                Spacer(Modifier.width(12.dp))
                ControlTile(onClick = { onSeekDelta(SEEK_STEP_MS) }, onFocused = onInteract) {
                    SkipGlyph(forward = true)
                }
                Spacer(Modifier.weight(1f))
                ControlChip(
                    text = qualityLabel,
                    onClick = { onOpenMenu(PlayerMenu.Quality) },
                    onFocused = onInteract,
                )
                Spacer(Modifier.width(12.dp))
                ControlChip(
                    text = speedLabel,
                    onClick = { onOpenMenu(PlayerMenu.Speed) },
                    onFocused = onInteract,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 进度条

@Composable
private fun SeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    focusRequester: FocusRequester,
    onSeekDelta: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onInteract: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    val total = durationMs.coerceAtLeast(1L)
    val fraction = (dragging ?: (positionMs.toFloat() / total)).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / total).coerceIn(0f, 1f)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatTime(positionMs),
                color = Palette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = " / ",
                color = Palette.TextTertiary,
                fontSize = 13.sp,
            )
            Text(
                text = formatTime(durationMs),
                color = Palette.TextSecondary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onInteract()
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeekDelta(-SEEK_STEP_MS)
                            true
                        }

                        Key.DirectionRight -> {
                            onSeekDelta(SEEK_STEP_MS)
                            true
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            onTogglePlay()
                            true
                        }

                        else -> false
                    }
                }
                .pointerInput(total) {
                    detectTapGestures(
                        onTap = { offset ->
                            onSeekTo((offset.x / size.width.toFloat() * total).toLong())
                            onInteract()
                        },
                    )
                }
                .pointerInput(total) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onInteract()
                        },
                        onDragEnd = {
                            dragging?.let { value -> onSeekTo((value * total).toLong()) }
                            dragging = null
                            onInteract()
                        },
                        onDragCancel = { dragging = null },
                        onHorizontalDrag = { change, _ ->
                            dragging = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            val trackHeight = if (focused) 6.dp.toPx() else 4.dp.toPx()
            val centerY = size.height / 2f
            val topLeftY = centerY - trackHeight / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(0f, topLeftY),
                size = Size(size.width, trackHeight),
                cornerRadius = corner,
            )
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = Color(0x4DFFFFFF),
                    topLeft = Offset(0f, topLeftY),
                    size = Size(size.width * bufferedFraction, trackHeight),
                    cornerRadius = corner,
                )
            }
            if (fraction > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Palette.Accent, Palette.Violet)),
                    topLeft = Offset(0f, topLeftY),
                    size = Size((size.width * fraction).coerceAtLeast(trackHeight), trackHeight),
                    cornerRadius = corner,
                )
            }
            if (focused) {
                val thumbX = size.width * fraction
                drawCircle(
                    color = Palette.Accent.copy(alpha = 0.35f),
                    radius = 10.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 小控件

@Composable
private fun ControlTile(
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    TvFocusable(
        onClick = onClick,
        modifier = modifier.size(46.dp),
        shape = shape,
        focusedScale = 1.08f,
        onFocused = { if (it) onFocused() },
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (focused) Palette.Accent.copy(alpha = 0.95f) else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun PlayGlyph(isPlaying: Boolean) {
    if (isPlaying) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    } else {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "播放",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SkipGlyph(forward: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (forward) {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "10",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ControlChip(
    text: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        focusedScale = 1.06f,
        onFocused = { if (it) onFocused() },
    ) { focused ->
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (focused) Palette.Accent.copy(alpha = 0.28f) else Color(0x26FFFFFF))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = if (focused) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = if (focused) Palette.Accent else Palette.TextTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 选择菜单

@Composable
private fun SelectorMenu(
    title: String,
    options: List<String>,
    selected: Int,
    firstItemFocus: FocusRequester,
    onSelect: (Int) -> Unit,
    onInteract: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .width(260.dp)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(Color(0xF2141421))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Palette.TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        options.forEachIndexed { index, label ->
            TvFocusable(
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
                focusRequester = if (index == 0) firstItemFocus else null,
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1f,
                onFocused = { if (it) onInteract() },
            ) { focused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                index == selected -> Palette.Accent.copy(alpha = 0.18f)
                                focused -> Color(0x1FFFFFFF)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = if (index == selected) Palette.AccentSoft else Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.weight(1f))
                    if (index == selected) {
                        Text(
                            text = "✓",
                            color = Palette.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (index != options.lastIndex) Spacer(Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------- 错误界面

@Composable
private fun PlayerErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(message) {
        delay(80)
        runCatching { retryFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "播放失败",
                color = Palette.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.55f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "可以尝试切换清晰度，或返回详情页重新解析",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    text = "重试",
                    onClick = onRetry,
                    primary = true,
                    focusRequester = retryFocus,
                )
                TvButton(text = "返回", onClick = onBack)
            }
        }
    }
}

@Composable
private fun PlaybackProblem(
    title: String?,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { backFocus.requestFocus() }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = "无法播放",
                color = Palette.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!title.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    color = Palette.TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = Palette.TextTertiary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            TvButton(
                text = "返回",
                onClick = onBack,
                primary = true,
                focusRequester = backFocus,
            )
        }
    }
}
