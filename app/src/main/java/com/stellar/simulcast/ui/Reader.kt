package com.stellar.simulcast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stellar.simulcast.data.Chapter
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.Manga
import com.stellar.simulcast.data.MangaDex
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.attempt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ReaderData(val manga: Manga, val chapters: List<Chapter>)

/**
 * One chapter per number. Keeps the group you started with when it has that chapter,
 * then your language, then the newest upload.
 */
private fun readingOrder(all: List<Chapter>, start: Chapter?): List<Chapter> {
    val usable = all.filter { it.externalUrl == null && it.pages > 0 }
    return usable.groupBy { it.number ?: "id:${it.id}" }.values.map { list ->
        list.firstOrNull { it.id == start?.id }
            ?: list.firstOrNull { start?.groupId != null && it.groupId == start.groupId }
            ?: list.firstOrNull { it.language == start?.language }
            ?: list.maxBy { it.publishAt }
    }.sortedWith(compareBy<Chapter> { it.num }.thenBy { it.publishAt })
}

@Composable
fun ReaderScreen(mangaId: String, startChapterId: String, startPage: Int, onClose: () -> Unit) {
    val state by produceState<Load<ReaderData>>(Load.Loading, mangaId) {
        value = attempt { ReaderData(MangaDex.manga(mangaId), MangaDex.chapters(mangaId)) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        when (val s = state) {
            Load.Loading -> LoadingView()
            is Load.Error -> Column(Modifier.fillMaxSize()) {
                IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close", tint = Space.Text)
                }
                ErrorView(s.message) { onClose() }
            }
            is Load.Ready -> {
                val start = s.data.chapters.firstOrNull { it.id == startChapterId }
                val order = remember(s.data) { readingOrder(s.data.chapters, start) }
                val startIndex = order.indexOfFirst { it.id == startChapterId }.coerceAtLeast(0)
                if (order.isEmpty()) {
                    ErrorView("This chapter can't be read in the app. It may be hosted on an official site.") { onClose() }
                } else {
                    Reader(s.data.manga, order, startIndex, if (start != null) startPage else 0, onClose)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Reader(manga: Manga, order: List<Chapter>, startIndex: Int, startPage: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val loader = remember { MangaDex.readerImageLoader(ctx) }
    val prefs = Settings.manga
    var index by remember { mutableIntStateOf(startIndex) }
    var firstPage by remember { mutableIntStateOf(startPage) }
    var showUi by remember { mutableStateOf(true) }
    val chapter = order[index]
    val hasNext = index < order.lastIndex
    val hasPrev = index > 0

    var retry by remember { mutableIntStateOf(0) }
    val pages by produceState<Load<List<String>>>(Load.Loading, chapter.id, prefs.dataSaver, retry) {
        value = Load.Loading
        value = attempt { MangaDex.pages(chapter.id).urls(prefs.dataSaver) }
    }

    fun goTo(i: Int, page: Int = 0) {
        index = i.coerceIn(0, order.lastIndex)
        firstPage = page
    }

    var currentPage by remember(chapter.id) { mutableIntStateOf(firstPage) }

    // Save your place (only if reading tracking is on), a moment after you stop on a page.
    LaunchedEffect(chapter.id, currentPage) {
        delay(600)
        MangaLibrary.saveMark(manga.id, chapter, currentPage)
    }

    Box(Modifier.fillMaxSize()) {
        when (val p = pages) {
            Load.Loading -> LoadingView()
            is Load.Error -> ErrorView(p.message) { retry++ }
            is Load.Ready -> {
                val urls = p.data
                // Get the next chapter's first pages ready near the end of this one.
                LaunchedEffect(chapter.id, currentPage >= urls.size - 3) {
                    if (hasNext && currentPage >= urls.size - 3) prefetch(ctx, loader, order[index + 1], prefs.dataSaver)
                }
                key(chapter.id, prefs.webtoon) {
                    if (prefs.webtoon) {
                        WebtoonView(urls, loader, firstPage.coerceIn(0, urls.size), { currentPage = it }, { showUi = !showUi }) {
                            EndCard(chapter, if (hasNext) order[index + 1] else null) { goTo(index + 1) }
                        }
                    } else {
                        PagedView(urls, loader, firstPage.coerceIn(0, urls.size), prefs.rtl, { currentPage = it }, { showUi = !showUi }) {
                            EndCard(chapter, if (hasNext) order[index + 1] else null) { goTo(index + 1) }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xD905060F)).statusBarsPadding().padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close", tint = Space.Text) }
                Column(Modifier.weight(1f)) {
                    Text(manga.title, color = Space.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(chapterLine(chapter), "by ${chapter.group ?: "unknown group"}", "via MangaDex").joinToString(" · "),
                        color = Space.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { Settings.updateManga(prefs.copy(webtoon = !prefs.webtoon)) }) {
                    Icon(
                        if (prefs.webtoon) Icons.Rounded.ViewCarousel else Icons.Rounded.ViewDay,
                        contentDescription = if (prefs.webtoon) "Page mode" else "Scroll mode", tint = Space.Cyan,
                    )
                }
                if (!prefs.webtoon) {
                    IconButton(onClick = { Settings.updateManga(prefs.copy(rtl = !prefs.rtl)) }) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = if (prefs.rtl) "Left to right" else "Right to left",
                            tint = if (prefs.rtl) Space.Magenta else Space.Cyan)
                    }
                }
                IconButton(onClick = { Settings.updateManga(prefs.copy(dataSaver = !prefs.dataSaver)) }) {
                    Icon(Icons.Rounded.DataSaverOn, contentDescription = "Data saver", tint = if (prefs.dataSaver) Space.Magenta else Space.Muted)
                }
            }
        }

        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xD905060F)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { goTo(index - 1) }, enabled = hasPrev) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous chapter", tint = if (hasPrev) Space.Cyan else Space.Muted.copy(alpha = 0.3f))
                }
                val total = (pages as? Load.Ready)?.data?.size ?: 0
                Text(
                    if (total > 0) "${(currentPage + 1).coerceAtMost(total)} / $total" else "",
                    color = Space.Text, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { goTo(index + 1) }, enabled = hasNext) {
                    Icon(Icons.Rounded.SkipNext, "Next chapter", tint = if (hasNext) Space.Cyan else Space.Muted.copy(alpha = 0.3f))
                }
            }
        }
    }
}

private fun prefetch(ctx: android.content.Context, loader: ImageLoader, next: Chapter, saver: Boolean) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val urls = runCatching { MangaDex.pages(next.id).urls(saver) }.getOrNull() ?: return@launch
        urls.take(3).forEach { loader.enqueue(ImageRequest.Builder(ctx).data(it).build()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedView(
    urls: List<String>,
    loader: ImageLoader,
    startPage: Int,
    rtl: Boolean,
    onPage: (Int) -> Unit,
    onCenterTap: () -> Unit,
    end: @Composable () -> Unit,
) {
    val pager = rememberPagerState(initialPage = startPage) { urls.size + 1 }
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onPage(it.coerceAtMost(urls.size - 1)) }
    }

    fun step(delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, urls.size)
        scope.launch { pager.animateScrollToPage(target) }
    }

    HorizontalPager(
        state = pager,
        reverseLayout = rtl,
        userScrollEnabled = !zoomed,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        if (page == urls.size) {
            end()
        } else {
            ZoomablePage(
                url = urls[page],
                loader = loader,
                isCurrent = pager.currentPage == page,
                onZoom = { zoomed = it },
                // Reading right to left, "next" is on the left.
                onLeft = { step(if (rtl) 1 else -1) },
                onRight = { step(if (rtl) -1 else 1) },
                onCenter = onCenterTap,
            )
        }
    }
}

@Composable
private fun ZoomablePage(
    url: String,
    loader: ImageLoader,
    isCurrent: Boolean,
    onZoom: (Boolean) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { _ ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        onZoom(scale > 1f)
                    },
                    onTap = { pos ->
                        val w = size.width.toFloat()
                        when {
                            pos.x < w / 3f -> onLeft()
                            pos.x > w * 2f / 3f -> onRight()
                            else -> onCenter()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Pinch to zoom; once zoomed, drag pans. A single finger at normal size is left for page swipes.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + event.calculatePan()
                            onZoom(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Space.Cyan.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
        AsyncImage(
            model = url,
            imageLoader = loader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun WebtoonView(
    urls: List<String>,
    loader: ImageLoader,
    startPage: Int,
    onPage: (Int) -> Unit,
    onTap: () -> Unit,
    end: @Composable () -> Unit,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.collect { onPage(it.coerceAtMost(urls.size - 1)) }
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentPadding = PaddingValues(vertical = 0.dp),
    ) {
        itemsIndexed(urls, key = { i, _ -> i }) { _, url ->
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 320.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Space.Cyan.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                AsyncImage(
                    model = url, imageLoader = loader, contentDescription = null,
                    contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Box(Modifier.fillMaxWidth().height(420.dp)) { end() } }
    }
}

@Composable
private fun EndCard(chapter: Chapter, next: Chapter?, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Space.Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("END OF ${chapter.label.uppercase()}", color = Space.Cyan, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Translated by ${chapter.group ?: "an unknown group"}. Support them and MangaDex if you enjoyed it.",
            color = Space.Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (next != null) {
            OutlinedButton(onClick = onNext) { Text("Next: ${chapterLine(next)}", color = Space.Cyan) }
        } else {
            Text("You're caught up.", color = Space.Text)
        }
    }
}
