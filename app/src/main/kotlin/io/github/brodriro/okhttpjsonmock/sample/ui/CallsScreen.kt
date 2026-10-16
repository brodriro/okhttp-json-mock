package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brodriro.okhttpjsonmock.sample.R
import io.github.brodriro.okhttpjsonmock.sample.SampleViewModel
import io.github.brodriro.okhttpjsonmock.sample.catalog.Feature
import io.github.brodriro.okhttpjsonmock.sample.mock.Exchange
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.CodeStyle

/** Every call of the session as recorded by the interceptor, and the lines it logged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(feature: Feature, viewModel: SampleViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Column {
                DetailTopBar(feature.title, onBack) {
                    IconButton(onClick = viewModel::clearHistory, enabled = viewModel.history.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Clear the history")
                    }
                }
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = {
                        tab = 0
                    }, text = { Text("Calls (${viewModel.history.size})") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Log") })
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("calls-screen"),
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Intro(feature.description, Modifier.padding(bottom = 8.dp)) }
            if (viewModel.history.isEmpty()) {
                item {
                    Text(
                        text = "No calls yet. Send a request from any feature and it shows up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (tab == 0) {
                itemsIndexed(viewModel.history, key = { index, _ -> viewModel.history.size - index }) { _, exchange ->
                    CallRow(exchange)
                }
            } else {
                item { CodeBlock(viewModel.history.reversed().flatMap { it.log }.joinToString("\n")) }
            }
        }
    }
}

/** One recorded call: `RecordedCall.method`, `url`, `statusCode`, `mockFile` and the `body` that was sent. */
@Composable
private fun CallRow(exchange: Exchange) {
    SampleCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodBadge(exchange.request.method)
                Spacer(Modifier.width(8.dp))
                Text("/${exchange.request.path}", style = CodeStyle, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusBadge(exchange.statusCode, null)
            }
            Text(
                text = "${exchange.mockFile ?: "no mock"} · ${exchange.elapsedMillis} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exchange.sentBody?.let { sent ->
                Text(
                    text = "Sent: ${sent.ifEmpty { "(empty body)" }}",
                    style = CodeStyle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sent-body"),
                )
            }
        }
    }
}
