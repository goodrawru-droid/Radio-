package ru.ark.radio

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

private val BG = Color(0xFF101114)
private val PANEL = Color(0xFF1B1D22)
private val ACCENT = Color(0xFF9C7BFF)
private val MUTED = Color(0xFF9A9DA7)

private const val OFFICIAL_MUSIC_CATALOG = "https://zaycev.net/"
private const val OFFICIAL_RADIO_SITE = "https://www.zaycev.fm/"

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, ArkPlaybackService::class.java))).buildAsync()
        controllerFuture?.addListener({ try { controller = controllerFuture?.get() } catch (_: Exception) {} }, mainExecutor)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = PANEL, primary = ACCENT)) {
                RadioScreen(controller)
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
private fun RadioScreen(controller: MediaController?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ark_radio", android.content.Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var selected by remember { mutableStateOf(StationCatalog.all.first()) }
    var playing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Выберите станцию") }
    var trackTitle by remember { mutableStateOf<String?>(null) }
    var trackArtist by remember { mutableStateOf<String?>(null) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var savedPlaylists by remember { mutableStateOf(prefs.getStringSet("saved_playlists", emptySet())?.toSet() ?: emptySet()) }

    LaunchedEffect(favorites) { prefs.edit().putStringSet("favorites", favorites.toSet()).apply() }
    LaunchedEffect(savedPlaylists) { prefs.edit().putStringSet("saved_playlists", savedPlaylists.toSet()).apply() }
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> "Подключение к потоку…"
                    Player.STATE_READY -> if (controller.isPlaying) "В эфире" else "Готово к воспроизведению"
                    Player.STATE_ENDED -> "Эфир завершён"
                    Player.STATE_IDLE -> if (!controller.isPlaying) "Остановлено" else status
                    else -> status
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) status = "В эфире" else if (controller.playbackState == Player.STATE_READY) status = "Пауза"
            }
            override fun onPlayerError(error: PlaybackException) {
                playing = false
                status = "Поток недоступен. Попробуйте другую станцию."
                Toast.makeText(context, "Не удалось воспроизвести ${selected.name}", Toast.LENGTH_LONG).show()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                trackTitle = null
                trackArtist = null
                val title = mediaItem?.mediaMetadata?.title?.toString() ?: return
                StationCatalog.all.firstOrNull { it.name == title }?.let { selected = it }
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title = mediaMetadata.title?.toString()?.trim()
                val artist = mediaMetadata.artist?.toString()?.trim()
                trackTitle = title?.takeIf { it.isNotBlank() && it != selected.name && it != "ARK Radio" }
                trackArtist = artist?.takeIf { it.isNotBlank() && it != "ARK Radio • Zaycev.FM" }
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    fun play(station: Station) {
        selected = station
        trackTitle = null
        trackArtist = null
        val c = controller
        if (c == null) { status = "Плеер подключается. Подождите и попробуйте снова."; return }
        try {
            c.setMediaItem(MediaItem.Builder().setUri(station.url).setMediaMetadata(
                MediaMetadata.Builder().setTitle(station.name).setArtist("ARK Radio • Zaycev.FM").build()
            ).build())
            c.prepare(); c.play(); playing = true; status = "Подключение к потоку…"
        } catch (_: Exception) { playing = false; status = "Не удалось запустить станцию. Попробуйте ещё раз." }
    }
    val categories = listOf("Все", "Избранное") + StationCatalog.all.map { it.category }.distinct()
    val list = StationCatalog.all.filter { station ->
        (category == "Все" || (category == "Избранное" && station.id in favorites) || station.category == category) &&
            (station.name.contains(query, true) || station.category.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(BG).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(44.dp).background(Color(0xFF28105A), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("A", color = ACCENT, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ARK ", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("RADIO", color = ACCENT, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
                Text("Музыка • настроение • ты", color = MUTED, fontSize = 11.sp)
            }
            IconButton(onClick = { showPlaylists = true }) { Icon(Icons.Default.MusicNote, "Плейлисты", tint = ACCENT) }
            IconButton(onClick = { showEqualizer = true }) { Icon(Icons.Default.Equalizer, "Эквалайзер", tint = ACCENT) }
            IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, "О приложении", tint = MUTED) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showPlaylists = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.MusicNote, null, tint = ACCENT)
            Spacer(Modifier.width(8.dp))
            Text("Плейлисты • Рок", color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("Открыть каталог  ›", color = ACCENT)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск станции") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, shape = RoundedCornerShape(17.dp))
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(0.9f)) {
                        Text("СТАНЦИИ", color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories, key = { it }) { cat ->
                                Row(Modifier.fillMaxWidth().background(if (category == cat) Color(0xFF352064) else PANEL, RoundedCornerShape(13.dp)).clickable { category = cat }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (cat == "Избранное") Icons.Default.Favorite else Icons.Default.MusicNote, null, tint = if (category == cat) ACCENT else MUTED)
                                    Spacer(Modifier.width(10.dp)); Text(cat, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                                }
                            }
                            item { Spacer(Modifier.height(4.dp)); Text("КАТАЛОГ • ${list.size}", color = MUTED, fontSize = 11.sp) }
                            items(list, key = { it.id }) { station -> StationRow(station, selected, favorites, { play(station) }, { favorites = if (station.id in favorites) favorites - station.id else favorites + station.id }) }
                        }
                    }
                    Column(Modifier.weight(1.4f)) {
                        PlayerCard(selected, trackTitle, trackArtist, status, playing, favorites.contains(selected.id), { if (favorites.contains(selected.id)) favorites = favorites - selected.id else favorites = favorites + selected.id }, {
                            val c = controller
                            if (c == null) status = "Плеер подключается. Попробуйте ещё раз."
                            else if (c.isPlaying) c.pause() else if (c.mediaItemCount > 0 && c.playbackState != Player.STATE_IDLE) c.play() else play(selected)
                        }, { controller?.stop(); playing = false; status = "Остановлено" }, { showEqualizer = true })
                        Spacer(Modifier.height(10.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = PANEL), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("БЫСТРЫЙ ВЫБОР", color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(StationCatalog.all.take(8), key = { it.id }) { station -> AssistChip(onClick = { play(station) }, label = { Text(station.category) }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }) }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(categories) { cat -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    PlayerCard(selected, trackTitle, trackArtist, status, playing, favorites.contains(selected.id), { if (favorites.contains(selected.id)) favorites = favorites - selected.id else favorites = favorites + selected.id }, {
                        val c = controller
                        if (c == null) status = "Плеер подключается. Попробуйте ещё раз."
                        else if (c.isPlaying) c.pause() else if (c.mediaItemCount > 0 && c.playbackState != Player.STATE_IDLE) c.play() else play(selected)
                    }, { controller?.stop(); playing = false; status = "Остановлено" }, { showEqualizer = true })
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (category == "Избранное") "ИЗБРАННОЕ" else "СТАНЦИИ • ${list.size}", color = MUTED, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Нажми, чтобы слушать", color = MUTED, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                        items(list, key = { it.id }) { station -> StationRow(station, selected, favorites, { play(station) }, { favorites = if (station.id in favorites) favorites - station.id else favorites + station.id }) }
                    }
                }
            }
        }
    }
    if (showPlaylists) RockPlaylistDialog(
        savedPlaylists = savedPlaylists,
        onSave = { name -> savedPlaylists = savedPlaylists + name },
        onRemove = { name -> savedPlaylists = savedPlaylists - name },
        onDismiss = { showPlaylists = false }
    )
    if (showEqualizer) EqualizerDialog(onDismiss = { showEqualizer = false })
    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("ARK Radio") }, text = { Text("Интернет-радио на базе потоков Zaycev.FM. Избранное хранится на устройстве. Доступность каналов зависит от источника и подключения к интернету.") }, confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Понятно") } })
}

/** Prototype catalog: hierarchy is local for now; remote Zaycev catalog/API has not been verified. */
private val rockSubgenres = listOf(
    "Классический рок", "Альтернативный рок", "Хард-рок", "Панк-рок", "Зарубежный металл"
)
private val rockPlaylists = mapOf(
    "Классический рок" to listOf("Золотая классика рока", "Рок-хиты 70–80-х", "Легенды гитарного рока", "Классика на каждый день", "Большие рок-баллады"),
    "Альтернативный рок" to listOf("Alternative Essentials", "Гранж и 90-е", "Инди-рок подборка", "Новая альтернатива", "Мелодичный альтернативный рок"),
    "Хард-рок" to listOf("Hard Rock Anthems", "Гитарные риффы", "Стадионный рок", "Хард-рок классика", "Энергия на максимум"),
    "Панк-рок" to listOf("Punk Rock Classics", "Поп-панк", "Быстро и громко", "Панк-сцена 90-х", "Мелодичный панк"),
    "Зарубежный металл" to listOf("Heavy Metal Classics", "Metal Essentials", "Power Metal", "Thrash Metal", "Symphonic Metal")
)

@Composable
private fun RockPlaylistDialog(
    savedPlaylists: Set<String>,
    onSave: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var subgenre by remember { mutableStateOf<String?>(null) }
    var playlist by remember { mutableStateOf<String?>(null) }
    var showSaved by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Плейлисты", fontWeight = FontWeight.Bold)
                Text(
                    when {
                        showSaved -> "Моя библиотека"
                        playlist != null -> playlist!!
                        subgenre != null -> "Рок  ›  $subgenre"
                        else -> "Жанры  ›  Рок"
                    }, color = ACCENT, fontSize = 12.sp
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showSaved || subgenre != null || playlist != null) {
                        TextButton(onClick = {
                            when {
                                showSaved -> showSaved = false
                                playlist != null -> playlist = null
                                else -> subgenre = null
                            }
                        }) { Text("‹ Назад") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSaved = !showSaved; subgenre = null; playlist = null }) {
                        Text(if (showSaved) "Каталог" else "Мои плейлисты (${savedPlaylists.size})")
                    }
                }
                Spacer(Modifier.height(4.dp))
                when {
                    showSaved -> {
                        if (savedPlaylists.isEmpty()) Text("Здесь появятся плейлисты, которые ты сохранишь.", color = MUTED)
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(savedPlaylists.toList().sorted()) { name ->
                                PlaylistCatalogRow(name, "Сохранено на устройстве", Icons.Default.MusicNote, onClick = { playlist = name; showSaved = false }, trailing = {
                                    TextButton(onClick = { onRemove(name) }) { Text("Убрать") }
                                })
                            }
                        }
                    }
                    playlist != null -> {
                        Text("Подборка: ${playlist}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Встроенный каталог подборок пока не имеет подтверждённого публичного API. Открой официальную страницу, чтобы просмотреть реальные сборники и доступные для скачивания треки.", color = MUTED, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_MUSIC_CATALOG))) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Открыть каталог ZAYCEV.NET") }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onSave(playlist!!) }, enabled = playlist !in savedPlaylists, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Favorite, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (playlist in savedPlaylists) "Уже в моей библиотеке" else "Добавить в мои плейлисты")
                        }
                    }
                    subgenre == "__ROCK__" -> {
                        Text("Выбери поджанр", color = MUTED, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(rockSubgenres) { item ->
                                PlaylistCatalogRow(item, "${rockPlaylists[item]?.size ?: 0} подборок", Icons.Default.PlayArrow, onClick = { subgenre = item })
                            }
                        }
                    }
                    subgenre != null -> {
                        Text("Выбери плейлист", color = MUTED, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(rockPlaylists[subgenre].orEmpty()) { name ->
                                PlaylistCatalogRow(name, subgenre!!, Icons.Default.PlayArrow, onClick = { playlist = name })
                            }
                        }
                    }
                    else -> {
                        Text("Встроенная навигация по року — локальная подборка ARK Radio. Реальные списки и доступные загрузки открываются на официальном сайте.", color = MUTED, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        PlaylistCatalogRow("Рок", "5 поджанров · 25 подборок ARK", Icons.Default.MusicNote, onClick = { subgenre = "__ROCK__" })
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_MUSIC_CATALOG))) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Реальные сборники и скачивание") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun PlaylistCatalogRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().background(PANEL, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(Color(0xFF30204E), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ACCENT)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = MUTED, fontSize = 11.sp)
        }
        if (trailing != null) trailing() else Text("›", color = ACCENT, fontSize = 22.sp)
    }
}

@Composable
private fun StationRow(station: Station, selected: Station, favorites: Set<String>, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (station.id == selected.id) Color(0xFF282039) else PANEL, RoundedCornerShape(14.dp)).clickable(onClick = onPlay).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Color(0xFF30204E), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text(station.category.take(1).uppercase(), color = ACCENT, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = Color.White, fontWeight = if (station.id == selected.id) FontWeight.Bold else FontWeight.Medium, maxLines = 1, fontSize = 14.sp)
            Text(station.category, color = MUTED, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(38.dp)) { Icon(if (station.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное", tint = if (station.id in favorites) ACCENT else MUTED) }
    }
}

@Composable
private fun PlayerCard(station: Station, trackTitle: String?, trackArtist: String?, status: String, playing: Boolean, favorite: Boolean, onFavorite: () -> Unit, onToggle: () -> Unit, onStop: () -> Unit, onEqualizer: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171821))) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (playing) "●  СЕЙЧАС В ЭФИРЕ" else "ARK RADIO • ПЛЕЕР", color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(station.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    Text("СЕЙЧАС ИГРАЕТ", color = MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(trackTitle ?: "Информация о треке недоступна", color = if (trackTitle != null) Color.White else MUTED, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    if (!trackArtist.isNullOrBlank()) Text(trackArtist, color = ACCENT, fontSize = 12.sp, maxLines = 1)
                    Text(station.category, color = MUTED, fontSize = 11.sp)
                }
                IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное", tint = if (favorite) ACCENT else MUTED) }
            }
            Spacer(Modifier.height(4.dp))
            SpectrumVisualizer(playing, Modifier.fillMaxWidth().height(112.dp))
            Text(status, color = if (playing) Color(0xFF7EE3A2) else MUTED, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onStop) { Icon(Icons.Default.Stop, "Стоп") }
                Spacer(Modifier.width(14.dp))
                Button(onClick = onToggle, modifier = Modifier.height(50.dp), shape = RoundedCornerShape(28.dp)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(7.dp)); Text(if (playing) "Пауза" else "Слушать")
                }
                Spacer(Modifier.width(14.dp))
                FilledTonalIconButton(onClick = onEqualizer) { Icon(Icons.Default.Equalizer, "Эквалайзер") }
            }
        }
    }
}

@Composable
private fun SpectrumVisualizer(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(initialValue = 0f, targetValue = (Math.PI * 2).toFloat(), animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1500, easing = LinearEasing)), label = "phase")
    androidx.compose.foundation.Canvas(modifier) {
        val bars = 38
        val gap = size.width * 0.009f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val wave = (kotlin.math.sin(phase + i * 0.43f) + 1f) / 2f
            val envelope = 0.25f + 0.75f * (1f - kotlin.math.abs(i - bars / 2f) / (bars / 2f))
            val h = size.height * (if (active) (0.18f + 0.82f * wave * envelope) else (0.12f + 0.08f * envelope))
            val x = i * (barWidth + gap)
            drawRoundRect(color = if (i % 3 == 0) ACCENT else Color(0xFF6841D9), topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h), size = androidx.compose.ui.geometry.Size(barWidth, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
        }
    }
}


@Composable
private fun EqualizerDialog(onDismiss: () -> Unit) {
    val bandCount = ArkPlaybackService.bandCount().coerceAtMost(5)
    val range = ArkPlaybackService.bandRange()
    val values = remember(bandCount) { mutableStateListOf<Int>().apply { repeat(bandCount) { add(ArkPlaybackService.level(it)) } } }
    val presets = remember { ArkPlaybackService.presetNames() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Эквалайзер") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (ArkPlaybackService.audioSessionId <= 0 || bandCount == 0) {
                    Text("Запусти воспроизведение станции, затем открой эквалайзер ещё раз. Некоторые телефоны не поддерживают системный аудиоэффект.")
                } else {
                    Text("Настройка звука", color = MUTED, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    values.forEachIndexed { index, value ->
                        val freq = ArkPlaybackService.centerFrequencyHz(index)
                        val label = when {
                            freq < 100 -> "${freq} Гц"
                            freq >= 1000 -> "${freq / 1000.0} кГц"
                            else -> "$freq Гц"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.width(62.dp), fontSize = 12.sp)
                            Slider(value = value / 100f, onValueChange = { db ->
                                val level = (db * 100).toInt().coerceIn(range.first, range.second)
                                values[index] = level
                                ArkPlaybackService.setLevel(index, level)
                            }, valueRange = range.first / 100f..range.second / 100f, modifier = Modifier.weight(1f))
                            Text("${if (value > 0) "+" else ""}${"%.1f".format(value / 100f)}", modifier = Modifier.width(44.dp), fontSize = 11.sp)
                        }
                    }
                    if (presets.isNotEmpty()) {
                        Text("Пресеты", fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(presets.indices.toList()) { index ->
                                AssistChip(onClick = {
                                    ArkPlaybackService.applyPreset(index)
                                    for (i in values.indices) values[i] = ArkPlaybackService.level(i)
                                }, label = { Text(presets[index]) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = {
            if (bandCount > 0) TextButton(onClick = {
                ArkPlaybackService.resetBands()
                for (i in values.indices) values[i] = ArkPlaybackService.level(i)
            }) { Text("Сбросить") }
        }
    )
}
