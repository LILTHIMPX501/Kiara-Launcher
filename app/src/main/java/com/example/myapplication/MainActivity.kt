
package com.example.myapplication

import android.content.Intent
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.Normalizer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.exp

data class LauncherApp(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
) {
    // Calculs effectues une seule fois par application.
    val normalizedName: String = normalizeName(name)
    val iconBitmap: ImageBitmap by lazy(LazyThreadSafetyMode.NONE) {
        drawableToImageBitmap(icon)
    }
}

fun normalizeName(value: String): String {
    return Normalizer.normalize(
        value,
        Normalizer.Form.NFD
    ).replace("\\p{M}+".toRegex(), "")
        .uppercase()
}


// V4.8.3 - Archive portable, aucun acces direct aux dossiers d'autres applications.
private const val KIARA_BACKUP_MAX_BYTES = 20 * 1024 * 1024
private val KIARA_BOOLEAN_KEYS = listOf("dark_theme", "background_enabled", "show_clock", "compact_rows")
private val KIARA_INT_KEYS = listOf("favorite_columns", "alphabet_size", "wave_intensity", "favorite_icon_size")

private fun exportKiaraBackup(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences("libre_launcher", Context.MODE_PRIVATE)
    val settings = JSONObject().apply {
        put("format", "kiara-backup")
        put("version", 1)
        val values = JSONObject()
        KIARA_BOOLEAN_KEYS.forEach { key -> if (prefs.contains(key)) values.put(key, prefs.getBoolean(key, false)) }
        KIARA_INT_KEYS.forEach { key -> if (prefs.contains(key)) values.put(key, prefs.getInt(key, 0)) }
        if (prefs.contains("background_dim")) values.put("background_dim", prefs.getFloat("background_dim", 0.45f).toDouble())
        values.put("favorite_order", prefs.getString("favorite_order", "") ?: "")
        values.put("favorites", JSONArray((prefs.getStringSet("favorites", emptySet()) ?: emptySet()).sorted()))
        put("settings", values)
    }
    val background = File(context.filesDir, "kiara_background.jpg")
    val output = context.contentResolver.openOutputStream(uri) ?: error("Impossible d'ouvrir le fichier de sauvegarde")
    output.use { stream ->
        ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(settings.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (background.isFile && background.length() <= KIARA_BACKUP_MAX_BYTES) {
                zip.putNextEntry(ZipEntry("background.jpg"))
                background.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

private fun importKiaraBackup(context: Context, uri: Uri) {
    var jsonBytes: ByteArray? = null
    var imageBytes: ByteArray? = null
    val input = context.contentResolver.openInputStream(uri) ?: error("Fichier inaccessible")
    input.use { stream ->
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "settings.json" || entry.name == "background.jpg") {
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var count = 0
                    while (true) {
                        val read = zip.read(chunk)
                        if (read < 0) break
                        count += read
                        require(count <= KIARA_BACKUP_MAX_BYTES) { "Archive trop volumineuse" }
                        buffer.write(chunk, 0, read)
                    }
                    if (entry.name == "settings.json") jsonBytes = buffer.toByteArray()
                    else imageBytes = buffer.toByteArray()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
    val root = JSONObject((jsonBytes ?: error("settings.json absent")).toString(Charsets.UTF_8))
    require(root.getString("format") == "kiara-backup" && root.getInt("version") == 1) { "Format de sauvegarde incompatible" }
    val values = root.getJSONObject("settings")
    val favoriteArray = values.getJSONArray("favorites")
    require(favoriteArray.length() <= 2000) { "Trop de favoris" }
    val favoriteSet = (0 until favoriteArray.length()).map { favoriteArray.getString(it) }.toSet()
    val order = values.optString("favorite_order", "")
    require(order.length <= 100000) { "Ordre des favoris invalide" }
    val columns = values.optInt("favorite_columns", 4)
    val alphabetSize = values.optInt("alphabet_size", 16)
    val wave = values.optInt("wave_intensity", 1)
    val iconSize = values.optInt("favorite_icon_size", 1)
    val dim = values.optDouble("background_dim", 0.45).toFloat()
    require(columns in 3..4 && alphabetSize in listOf(13,16,19) && wave in 0..2 && iconSize in 0..2 && dim.isFinite() && dim in 0f..0.8f) { "Reglages invalides" }
    val image = imageBytes
    if (image != null) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        require(bounds.outWidth in 1..16000 && bounds.outHeight in 1..16000) { "Image de fond invalide" }
    }
    val prefs = context.getSharedPreferences("libre_launcher", Context.MODE_PRIVATE)
    // Ecrire l'image avant les preferences pour ne pas perdre les reglages en cas d'erreur d'E/S.
    val background = File(context.filesDir, "kiara_background.jpg")
    if (image != null) {
        val temp = File(context.filesDir, "kiara_background_import.tmp")
        temp.writeBytes(image)
        if (!temp.renameTo(background)) {
            temp.copyTo(background, overwrite = true)
            temp.delete()
        }
    } else if (background.exists()) {
        require(background.delete()) { "Impossible de supprimer l'ancien fond" }
    }
    val editor = prefs.edit()
    KIARA_BOOLEAN_KEYS.forEach { key ->
        if (values.has(key)) editor.putBoolean(key, values.getBoolean(key)) else editor.remove(key)
    }
    editor.putInt("favorite_columns", columns)
    editor.putInt("alphabet_size", alphabetSize)
    editor.putInt("wave_intensity", wave)
    editor.putInt("favorite_icon_size", iconSize)
    editor.putFloat("background_dim", dim)
    editor.putString("favorite_order", order)
    editor.putStringSet("favorites", favoriteSet)
    require(editor.commit()) { "Impossible d'enregistrer les preferences" }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apps = packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER),
                0
            )

            .map {
                LauncherApp(
                    name = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(packageManager)
                )
            }

            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .sortedBy { it.normalizedName }

        val preferences = getSharedPreferences(
            "libre_launcher",
            MODE_PRIVATE
        )

        setContent {
            var darkTheme by remember {
                mutableStateOf(
                    preferences.getBoolean("dark_theme", true)
                )
            }

            var search by remember {
                mutableStateOf("")
            }

            var showSettings by remember { mutableStateOf(false) }
            val backgroundFile = remember { File(filesDir, "kiara_background.jpg") }
            var backgroundVersion by remember { mutableIntStateOf(0) }
            var backgroundEnabled by remember {
                mutableStateOf(preferences.getBoolean("background_enabled", true))
            }
            var backgroundDim by remember {
                mutableFloatStateOf(preferences.getFloat("background_dim", 0.45f))
            }
            val backgroundBitmap = remember(backgroundVersion, backgroundEnabled) {
                if (backgroundEnabled && backgroundFile.exists()) {
                    try {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(backgroundFile.absolutePath, options)
                        val sample = (maxOf(options.outWidth, options.outHeight) / 1600)
                            .coerceAtLeast(1)
                        val decoded = BitmapFactory.decodeFile(
                            backgroundFile.absolutePath,
                            BitmapFactory.Options().apply { inSampleSize = sample }
                        )
                        decoded?.asImageBitmap()
                    } catch (_: Exception) { null }
                } else null
            }
            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(backgroundFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        backgroundEnabled = true
                        preferences.edit().putBoolean("background_enabled", true).apply()
                        backgroundVersion++
                    } catch (_: Exception) { /* Conserver le fond actuel en cas d'erreur */ }
                }
            }
            var backupMessage by remember { mutableStateOf<String?>(null) }
            var confirmImport by remember { mutableStateOf(false) }
            val exportBackup = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri != null) {
                    backupMessage = try {
                        exportKiaraBackup(this@MainActivity, uri)
                        "Sauvegarde exportee avec succes. Conserve ce fichier en lieu sur."
                    } catch (e: Exception) { "Echec de l'export : ${e.message ?: "erreur inconnue"}" }
                }
            }
            val importBackup = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        importKiaraBackup(this@MainActivity, uri)
                        // Recharge tous les etats Compose depuis les SharedPreferences.
                        recreate()
                    } catch (e: Exception) {
                        backupMessage = "Echec de l'import : ${e.message ?: "fichier invalide"}"
                    }
                }
            }
            var showClock by remember {
                mutableStateOf(preferences.getBoolean("show_clock", true))
            }
            var compactRows by remember {
                mutableStateOf(preferences.getBoolean("compact_rows", true))
            }
            var favoriteColumns by remember {
                mutableIntStateOf(preferences.getInt("favorite_columns", 4))
            }
            // V4.7 : personnalisation persistante, valeurs par défaut identiques à V4.6.
            var alphabetSize by remember {
                mutableIntStateOf(preferences.getInt("alphabet_size", 16).let {
                    if (it in listOf(13, 16, 19)) it else 16
                })
            }
            var waveIntensity by remember {
                mutableIntStateOf(preferences.getInt("wave_intensity", 1).coerceIn(0, 2))
            }
            var favoriteIconSize by remember {
                mutableIntStateOf(preferences.getInt("favorite_icon_size", 1).coerceIn(0, 2))
            }
            var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    currentTime = System.currentTimeMillis()
                    delay(15_000L)
                }
            }


            var favorites by remember {
                mutableStateOf(
                    preferences.getStringSet(
                        "favorites",
                        emptySet()
                    )?.toSet() ?: emptySet()
                )
            }

            var favoriteOrder by remember {
                mutableStateOf(
                    preferences.getString("favorite_order", "")
                        ?.split("|")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                )
            }
            var selectedFavorite by remember { mutableStateOf<String?>(null) }
            var showAppList by remember { mutableStateOf(false) }
            var searchFocused by remember { mutableStateOf(false) }
            var searchExpanded by remember { mutableStateOf(false) }
            val searchFocusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            fun closeSearch() {
                searchExpanded = false
                searchFocused = false
                search = ""
                showAppList = false
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            BackHandler(enabled = searchExpanded && !showSettings) { closeSearch() }
            LaunchedEffect(searchExpanded) {
                if (searchExpanded) {
                    delay(280L)
                    searchFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
            var listInteraction by remember { mutableIntStateOf(0) }
            fun revealApps() {
                showAppList = true
                listInteraction++
            }
            LaunchedEffect(showAppList, listInteraction, search, searchFocused, searchExpanded) {
                if (showAppList && !searchExpanded && !searchFocused) {
                    delay(3000L)
                    showAppList = false
                }
            }

            fun saveFavoriteOrder(order: List<String>) {
                favoriteOrder = order
                preferences.edit()
                    .putString("favorite_order", order.joinToString("|"))
                    .apply()
            }

            fun moveFavorite(app: LauncherApp, delta: Int) {
                val current = favorites.sortedWith(
                    compareBy<String> { pkg ->
                        val position = favoriteOrder.indexOf(pkg)
                        if (position == -1) Int.MAX_VALUE else position
                    }.thenBy { pkg ->
                        apps.firstOrNull { it.packageName == pkg }?.name ?: pkg
                    }
                ).toMutableList()
                val from = current.indexOf(app.packageName)
                val to = from + delta
                if (from >= 0 && to in current.indices) {
                    val moved = current.removeAt(from)
                    current.add(to, moved)
                    saveFavoriteOrder(current)
                }
            }

            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val alphabet = ('A'..'Z').toList()

            fun toggleFavorite(app: LauncherApp) {
                val updated = favorites.toMutableSet()

                if (app.packageName in updated) {
                    updated.remove(app.packageName)
                } else {
                    updated.add(app.packageName)
                }

                favorites = updated.toSet()
                if (app.packageName !in updated) {
                    selectedFavorite = null
                    saveFavoriteOrder(favoriteOrder.filterNot { it == app.packageName })
                } else {
                    val existing = favoriteOrder.filter { it in updated }
                    saveFavoriteOrder(existing + app.packageName)
                }

                preferences.edit()
                    .putStringSet("favorites", updated)
                    .apply()
            }

            fun openApp(app: LauncherApp) {
                val intent = packageManager
                    .getLaunchIntentForPackage(app.packageName)

                if (intent != null) {
                    startActivity(intent)
                }
            }

            // Les listes ne sont recalculees que lorsque leurs dependances changent.
            val normalizedQuery = remember(search) { normalizeName(search.trim()) }
            val filteredApps = remember(apps, normalizedQuery) {
                if (normalizedQuery.isEmpty()) apps
                else apps.filter { it.normalizedName.contains(normalizedQuery) }
            }

            val favoriteApps = remember(filteredApps, favorites, favoriteOrder) {
                val orderPositions = favoriteOrder.withIndex().associate { it.value to it.index }
                filteredApps.filter { it.packageName in favorites }
                    .sortedWith(compareBy<LauncherApp> {
                        orderPositions[it.packageName] ?: Int.MAX_VALUE
                    }.thenBy { it.normalizedName })
            }

            val otherApps = remember(filteredApps, favorites) {
                filteredApps.filter { it.packageName !in favorites }
            }

            // L'index pointe uniquement vers la liste temporaire des applications.
            val letterPositions = remember(otherApps) {
                val positions = mutableMapOf<Char, Int>()
                otherApps.forEachIndexed { index, app ->
                    val letter = app.normalizedName.firstOrNull() ?: '#'
                    if (letter in 'A'..'Z') positions.putIfAbsent(letter, index)
                }
                positions.toMap()
            }

            MaterialTheme(
                colorScheme = if (darkTheme || backgroundBitmap != null) {
                    darkColorScheme()
                } else {
                    lightColorScheme()
                }
            ) {
                if (backupMessage != null) {
                    AlertDialog(
                        onDismissRequest = { backupMessage = null },
                        title = { Text("Sauvegarde Kiar'a") },
                        text = { Text(backupMessage ?: "") },
                        confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("OK") } }
                    )
                }
                if (confirmImport) {
                    AlertDialog(
                        onDismissRequest = { confirmImport = false },
                        title = { Text("Restaurer une sauvegarde ?") },
                        text = { Text("Cette operation remplacera les favoris, leurs positions, les reglages et le fond d'ecran actuels. Exporte d'abord ta configuration actuelle si tu souhaites la conserver.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmImport = false
                                importBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            }) { Text("Restaurer") }
                        },
                        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Annuler") } }
                    )
                }
                if (showSettings) {
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        title = { Text("Personnaliser Kiar'a") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Afficher l'horloge")
                                    Switch(checked = showClock, onCheckedChange = {
                                        showClock = it
                                        preferences.edit().putBoolean("show_clock", it).apply()
                                    })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Liste compacte")
                                    Switch(checked = compactRows, onCheckedChange = {
                                        compactRows = it
                                        preferences.edit().putBoolean("compact_rows", it).apply()
                                    })
                                }
                                HorizontalDivider()
                                Text("Sauvegarde et restauration", style = MaterialTheme.typography.titleSmall)
                                Text("Export ZIP : favoris, ordre, affichage et photo de fond.", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { exportBackup.launch("Kiara_sauvegarde.zip") }) {
                                        Text("Exporter")
                                    }
                                    TextButton(onClick = { confirmImport = true }) {
                                        Text("Importer")
                                    }
                                }
                                HorizontalDivider()
                                Text("Fond d'écran")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { photoPicker.launch("image/*") }) {
                                        Text("Choisir une photo")
                                    }
                                    TextButton(onClick = {
                                        backgroundFile.delete()
                                        backgroundVersion++
                                    }) {
                                        Text("Fond uni")
                                    }
                                }
                                if (backgroundBitmap != null) {
                                    Text("Assombrissement : ${(backgroundDim * 100).toInt()} %")
                                    Slider(
                                        value = backgroundDim,
                                        onValueChange = {
                                            backgroundDim = it
                                            preferences.edit().putFloat("background_dim", it).apply()
                                        },
                                        valueRange = 0f..0.8f
                                    )
                                }
                                HorizontalDivider()
                                Text("Colonnes des favoris")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3, 4).forEach { count ->
                                        FilterChip(
                                            selected = favoriteColumns == count,
                                            onClick = {
                                                favoriteColumns = count
                                                preferences.edit()
                                                    .putInt("favorite_columns", count).apply()
                                            },
                                            label = { Text("$count colonnes") }
                                        )
                                    }
                                }
                                HorizontalDivider()
                                Text("Taille de l'alphabet")
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(13 to "Compact", 16 to "Confort", 19 to "Large").forEach { (size, title) ->
                                        FilterChip(
                                            selected = alphabetSize == size,
                                            onClick = {
                                                alphabetSize = size
                                                preferences.edit().putInt("alphabet_size", size).apply()
                                            },
                                            label = { Text(title, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                Text("Effet Wave")
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Discret", "Normal", "Accentué").forEachIndexed { index, title ->
                                        FilterChip(
                                            selected = waveIntensity == index,
                                            onClick = {
                                                waveIntensity = index
                                                preferences.edit().putInt("wave_intensity", index).apply()
                                            },
                                            label = { Text(title, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                Text("Taille des icônes favorites")
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Petit", "Normal", "Grand").forEachIndexed { index, title ->
                                        FilterChip(
                                            selected = favoriteIconSize == index,
                                            onClick = {
                                                favoriteIconSize = index
                                                preferences.edit().putInt("favorite_icon_size", index).apply()
                                            },
                                            label = { Text(title, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                Text(
                                    "Appui long sur un favori : déplacer ou retirer.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSettings = false }) {
                                Text("Terminé")
                            }
                        }
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (backgroundBitmap != null) {
                        Image(
                            bitmap = backgroundBitmap,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier.matchParentSize().background(
                                Color.Black.copy(alpha = backgroundDim)
                            )
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (backgroundBitmap != null) Color.Transparent
                        else MaterialTheme.colorScheme.background,
                        contentColor = if (backgroundBitmap != null) Color.White
                        else MaterialTheme.colorScheme.onBackground
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kiar'a",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 2.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⚙",
                                        fontSize = 24.sp,
                                        modifier = Modifier
                                            .clickable { showSettings = true }
                                            .padding(horizontal = 12.dp)
                                    )
                                    Text(
                                        text = if (darkTheme) "☾" else "☀",
                                        fontSize = 26.sp,
                                        modifier = Modifier.clickable {
                                            darkTheme = !darkTheme
                                            preferences.edit()
                                                .putBoolean("dark_theme", darkTheme)
                                                .apply()
                                        }
                                    )
                                }
                            }

                            Text(
                                text = "L'essentiel, simplement.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (showClock) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.FRANCE)
                                        .format(Date(currentTime)),
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Light
                                )
                                Text(
                                    text = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
                                        .format(Date(currentTime))
                                        .replaceFirstChar { it.uppercase() },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // La zone centrale reste libre au repos : uniquement les favoris.
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (!showAppList) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(end = if (alphabetSize == 19) 62.dp else 56.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        if (favoriteApps.isNotEmpty()) {
                                            Text(
                                                text = "★ Favoris",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            ) {
                                                favoriteApps.chunked(favoriteColumns).forEach { group ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        group.forEach { app ->
                                                            FavoriteIcon(
                                                                app = app,
                                                                onOpen = { openApp(app) },
                                                                onLongPress = { selectedFavorite = app.packageName },
                                                                selected = selectedFavorite == app.packageName,
                                                                canMoveLeft = favoriteApps.indexOf(app) > 0,
                                                                canMoveRight = favoriteApps.indexOf(app) < favoriteApps.lastIndex,
                                                                onMoveLeft = { moveFavorite(app, -1) },
                                                                onMoveRight = { moveFavorite(app, 1) },
                                                                onRemove = { toggleFavorite(app) },
                                                                modifier = Modifier.weight(1f),
                                                                iconSize = when (favoriteIconSize) {
                                                                    0 -> if (favoriteColumns == 3) 46.dp else 40.dp
                                                                    2 -> if (favoriteColumns == 3) 62.dp else 54.dp
                                                                    else -> if (favoriteColumns == 3) 54.dp else 46.dp
                                                                }
                                                            )
                                                        }
                                                        repeat(favoriteColumns - group.size) {
                                                            Spacer(Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }

                                        }
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            items(otherApps, key = { it.packageName }) { app ->
                                                AppRow(
                                                    app = app,
                                                    isFavorite = false,
                                                    compact = compactRows,
                                                    onOpen = { openApp(app) },
                                                    onLongPress = {
                                                        toggleFavorite(app)
                                                        revealApps()
                                                    }
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }
                                }

                                var alphabetHeight by remember { mutableFloatStateOf(0f) }
                                var activeLetterIndex by remember { mutableIntStateOf(-1) }

                                fun navigateToAlphabetPosition(y: Float) {
                                    if (alphabetHeight <= 0f) return
                                    val index = (y / alphabetHeight * alphabet.size)
                                        .toInt().coerceIn(0, alphabet.lastIndex)
                                    activeLetterIndex = index
                                    revealApps()
                                    val target = letterPositions[alphabet[index]]
                                    if (target != null) {
                                        scope.launch { listState.scrollToItem(target) }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(if (alphabetSize == 19) 60.dp else 52.dp)
                                        .fillMaxHeight()
                                        .onSizeChanged { alphabetHeight = it.height.toFloat() }
                                        .pointerInput(letterPositions, alphabetHeight) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    navigateToAlphabetPosition(offset.y)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    navigateToAlphabetPosition(change.position.y)
                                                },
                                                onDragEnd = { activeLetterIndex = -1 },
                                                onDragCancel = { activeLetterIndex = -1 }
                                            )
                                        },
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    alphabet.forEachIndexed { index, letter ->
                                        val available = letterPositions.containsKey(letter)
                                        val distance = abs(index - activeLetterIndex).toFloat()
                                        val wave = if (activeLetterIndex >= 0) {
                                            exp(-distance * distance / 7.0f)
                                        } else 0f
                                        val animatedShift by animateFloatAsState(
                                            targetValue = -24f * wave * when (waveIntensity) {
                                                0 -> 0.65f
                                                2 -> 1.35f
                                                else -> 1f
                                            },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "alphabet-wave-$letter"
                                        )
                                        val animatedScale by animateFloatAsState(
                                            targetValue = 1f + 0.6875f * wave * when (waveIntensity) {
                                                0 -> 0.55f
                                                2 -> 1.25f
                                                else -> 1f
                                            },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "alphabet-scale-$letter"
                                        )
                                        Text(
                                            text = letter.toString(),
                                            fontSize = alphabetSize.sp,
                                            color = when {
                                                activeLetterIndex == index -> MaterialTheme.colorScheme.primary
                                                available -> MaterialTheme.colorScheme.onSurface
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            },
                                            modifier = Modifier
                                                .width(if (alphabetSize == 19) 46.dp else 40.dp)
                                                .graphicsLayer {
                                                    translationX = animatedShift * density
                                                    scaleX = animatedScale
                                                    scaleY = animatedScale
                                                }
                                                .clickable(enabled = available) {
                                                    activeLetterIndex = index
                                                    revealApps()
                                                    val target = letterPositions[letter]
                                                    if (target != null) {
                                                        scope.launch {
                                                            listState.scrollToItem(target)
                                                            delay(350L)
                                                            activeLetterIndex = -1
                                                        }
                                                    }
                                                }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            // Recherche escamotable : loupe à gauche, ouverture vers la droite.
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val searchWidth by animateDpAsState(
                                    targetValue = if (searchExpanded) maxWidth else 52.dp,
                                    animationSpec = tween(durationMillis = 260),
                                    label = "search-expand"
                                )
                                Box(
                                    modifier = Modifier
                                        .width(searchWidth)
                                        .height(56.dp)
                                ) {
                                    if (searchExpanded) {
                                        TextField(
                                            value = search,
                                            onValueChange = {
                                                search = it
                                                revealApps()
                                            },
                                            placeholder = { Text("Rechercher...") },
                                            leadingIcon = { Text("⌕", fontSize = 24.sp) },
                                            trailingIcon = {
                                                IconButton(onClick = { closeSearch() }) {
                                                    Text("×", fontSize = 25.sp)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .focusRequester(searchFocusRequester)
                                                .onFocusChanged {
                                                    searchFocused = it.isFocused
                                                    if (it.isFocused) revealApps()
                                                },
                                            singleLine = true,
                                            shape = MaterialTheme.shapes.large,
                                            colors = TextFieldDefaults.colors(
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                disabledIndicatorColor = Color.Transparent
                                            )
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable {
                                                    searchExpanded = true
                                                    revealApps()
                                                },
                                            shape = MaterialTheme.shapes.large,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("⌕", fontSize = 28.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            // Le compte à rebours est relancé à chaque saisie ou geste A–Z.

                        }
                    }
                }
            }
        }
    }
}



fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888
    )

    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)

    return bitmap.asImageBitmap()
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: LauncherApp,
    isFavorite: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val iconBitmap = app.iconBitmap

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )

        Spacer(Modifier.width(14.dp))

        Text(
            text = app.name,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )

        if (isFavorite) {
            Text(
                text = "★",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteIcon(
    app: LauncherApp,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    selected: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 46.dp
) {
    val iconBitmap = app.iconBitmap
    Column(
        modifier = modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = onLongPress
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = iconBitmap,
            contentDescription = app.name,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = app.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (selected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "◀",
                    fontSize = 17.sp,
                    color = if (canMoveLeft) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable(enabled = canMoveLeft) { onMoveLeft() }
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "×",
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onRemove() }
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "▶",
                    fontSize = 17.sp,
                    color = if (canMoveRight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable(enabled = canMoveRight) { onMoveRight() }
                )
            }
        }
    }
}
