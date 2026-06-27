package com.example.pricefinder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pricefinder.data.PriceItem
import com.example.pricefinder.data.SearchResult
import com.example.pricefinder.ui.SearchViewModel
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { AppScreen() }
            }
        }
    }
}

private const val PAGE_SIZE = 10

private fun money(v: Double): String =
    NumberFormat.getInstance(Locale("ru")).format(v.toLong()) + " ₽"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var visibleCounts by remember {
        mutableStateOf<Map<com.example.pricefinder.data.Source, Int>>(emptyMap())
    }
    androidx.compose.runtime.LaunchedEffect(state.result) {
        visibleCounts = emptyMap()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.recognizeAndSearch(context, it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraUri?.let { vm.recognizeAndSearch(context, it) } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = ImagePicker.newCameraUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text("Поиск цены товара",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("WB · Ozon · Маркет · ММ · Авито · Ali",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.name, onValueChange = vm::onName,
                label = { Text("Наименование") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.article, onValueChange = vm::onArticle,
                label = { Text("Артикул") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.model, onValueChange = vm::onModel,
                label = { Text("Модель") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = vm::search,
                enabled = !state.loading && !state.recognizing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.loading) "Поиск..." else "Найти цену") }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    enabled = !state.recognizing && !state.loading,
                    modifier = Modifier.weight(1f)
                ) { Text("📷 Камера") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !state.recognizing && !state.loading,
                    modifier = Modifier.weight(1f)
                ) { Text("🖼 Галерея") }
            }

            Spacer(Modifier.height(16.dp))

            if (state.recognizing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Распознаю фото...")
                }
            }
            if (state.recognizedLabels.isNotEmpty()) {
                Text("Распознано: " + state.recognizedLabels.take(5).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error?.let {
                Text("Ошибка: $it", color = MaterialTheme.colorScheme.error)
            }
            state.result?.let { ResultHeader(it) }
        }

        state.result?.items?.let { list ->
            val grouped = list.groupBy { it.source }
            grouped.forEach { (source, products) ->
                item(key = "h_${source.name}") {
                    Spacer(Modifier.height(12.dp))
                    Text("${source.label}  (${products.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
                val shown = visibleCounts[source] ?: PAGE_SIZE
                items(products.take(shown), key = { it.id }) { PriceRow(it) }
                if (products.size > shown) {
                    item(key = "m_${source.name}") {
                        OutlinedButton(
                            onClick = {
                                visibleCounts = visibleCounts.toMutableMap().apply {
                                    this[source] = shown + PAGE_SIZE
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) { Text("Показать ещё (${products.size - shown})") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(result: SearchResult) {
    if (result.sourceErrors.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        result.sourceErrors.forEach {
            Text("⚠ $it", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
    if (result.items.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Ничего не найдено. Уточните запрос.")
        return
    }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Средняя цена по рынку",
                style = MaterialTheme.typography.labelLarge)
            Text(money(result.averagePrice),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("от ${money(result.minPrice)} до ${money(result.maxPrice)} · " +
                "${result.items.size} предложений",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun PriceRow(item: PriceItem) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val sub = buildString {
                    append(item.source.label)
                    if (item.brand.isNotBlank()) append(" · ${item.brand}")
                    item.rating?.let { append(" · ★ %.1f".format(it)) }
                    item.feedbacks?.let { append(" · $it отз.") }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Text(money(item.price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        }
    }
}
