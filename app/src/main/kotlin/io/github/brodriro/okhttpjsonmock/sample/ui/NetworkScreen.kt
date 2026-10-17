package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.brodriro.okhttpjsonmock.sample.SampleViewModel
import io.github.brodriro.okhttpjsonmock.sample.catalog.Feature
import io.github.brodriro.okhttpjsonmock.sample.mock.Latency
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.CodeStyle
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.SampleTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Latency, failure percentage, failure code and strict mode, plus a burst of calls to see their effect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(feature: Feature, viewModel: SampleViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings
    Scaffold(topBar = { DetailTopBar(feature.title, onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("network-screen"),
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Intro(feature.description, Modifier.padding(bottom = 8.dp)) }
            item {
                SettingCard("Latency: ${settings.latency.window}", "Builder.delay(min, max)") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Latency.entries.forEachIndexed { index, latency ->
                            SegmentedButton(
                                selected = settings.latency == latency,
                                onClick = { viewModel.updateSettings(settings.copy(latency = latency)) },
                                shape = SegmentedButtonDefaults.itemShape(index, Latency.entries.size),
                            ) { Text(latency.label, maxLines = 1) }
                        }
                    }
                }
            }
            item {
                SettingCard("Failures: ${settings.failurePercentage} %", "Builder.failurePercentage(percent)") {
                    Slider(
                        value = settings.failurePercentage.toFloat(),
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(failurePercentage = it.roundToInt()))
                        },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.testTag("failure-slider"),
                    )
                }
            }
            item {
                SettingCard("Failure status", "Builder.failureCode(code)") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        FAILURE_CODES.forEachIndexed { index, code ->
                            SegmentedButton(
                                selected = settings.failureCode == code,
                                onClick = { viewModel.updateSettings(settings.copy(failureCode = code)) },
                                shape = SegmentedButtonDefaults.itemShape(index, FAILURE_CODES.size),
                            ) { Text(code.toString()) }
                        }
                    }
                }
            }
            item {
                SampleCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fail on missing mock", style = MaterialTheme.typography.titleMedium)
                            Text("Builder.failOnMissingMock()", style = CodeStyle)
                        }
                        Switch(
                            checked = settings.failOnMissingMock,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(failOnMissingMock = it)) },
                        )
                    }
                }
            }
            item { Burst(viewModel, feature) }
            items(feature.examples.size) { ExampleCard(feature.examples[it], viewModel) }
        }
    }
}

@Composable
private fun SettingCard(title: String, api: String, content: @Composable () -> Unit) {
    SampleCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(api, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Sends the ping ten times in parallel and shows one tile per answer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Burst(viewModel: SampleViewModel, feature: Feature) {
    val scope = rememberCoroutineScope()
    val codes = remember { mutableStateListOf<Int?>() }
    var running by remember { mutableStateOf(false) }
    SampleCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ten calls at once", style = MaterialTheme.typography.titleMedium)
                    Text("GET /api/v1/ping with these settings", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(
                    enabled = !running,
                    onClick = {
                        running = true
                        codes.clear()
                        scope.launch {
                            val request = feature.examples.first().request
                            val results = List(BURST) { async { viewModel.send(request) } }.awaitAll()
                            codes.addAll(results.map { it.statusCode })
                            running = false
                        }
                    },
                    modifier = Modifier.testTag("burst"),
                ) { Text(if (running) "Sending…" else "Send 10") }
            }
            if (codes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    codes.forEach { code -> CodeTile(code) }
                }
                val ok = codes.count { it in 200..299 }
                Text(
                    text = "$ok answered by the mock, ${codes.size - ok} simulated failures",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("burst-summary"),
                )
            }
        }
    }
}

@Composable
private fun CodeTile(code: Int?) {
    val (container, content) = when (code) {
        in 200..299 -> SampleTheme.status.success
        else -> SampleTheme.status.serverError
    }
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .semantics { contentDescription = "Status $code" },
        contentAlignment = Alignment.Center,
    ) { Text(code?.toString() ?: "–", style = CodeStyle, color = content) }
}

private val FAILURE_CODES = listOf(500, 503, 504)
private const val BURST = 10
