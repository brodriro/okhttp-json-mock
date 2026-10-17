package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.brodriro.okhttpjsonmock.sample.R
import io.github.brodriro.okhttpjsonmock.sample.SampleViewModel
import io.github.brodriro.okhttpjsonmock.sample.catalog.Example
import io.github.brodriro.okhttpjsonmock.sample.catalog.Feature
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.CodeStyle

/** A feature with requests to send: its description and one card per example. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScreen(feature: Feature, viewModel: SampleViewModel, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DetailTopBar(feature.title, onBack, scrollBehavior) {
                TextButton(onClick = { feature.examples.forEach(viewModel::send) }) { Text("Send all") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("feature-screen"),
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Intro(feature.description, Modifier.padding(bottom = 8.dp)) }
            items(feature.examples, key = { it.id }) { example -> ExampleCard(example, viewModel) }
        }
    }
}

/** A request, the file expected to answer it, a send button and, once sent, what came back. */
@Composable
fun ExampleCard(example: Example, viewModel: SampleViewModel) {
    val exchange = viewModel.result(example)
    val running = viewModel.isRunning(example)
    SampleCard(modifier = Modifier.fillMaxWidth().testTag("example-${example.id}")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(example.title, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.Top) {
                        MethodBadge(example.request.method)
                        Spacer(Modifier.width(8.dp))
                        Text("/${example.request.path}", style = CodeStyle)
                    }
                    Text(
                        text = "Expected: ${example.answeredBy}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { viewModel.send(example) },
                    enabled = !running,
                    modifier = Modifier.testTag("send-${example.id}"),
                ) {
                    if (running) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_send), contentDescription = null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Send")
                }
            }
            if (exchange != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExchangeView(exchange)
            }
        }
    }
}

/** Content padding of a scrolling screen below its top bar and above the navigation bar. */
fun screenPadding(scaffold: PaddingValues) = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = scaffold.calculateTopPadding() + 8.dp,
    bottom = scaffold.calculateBottomPadding() + 24.dp,
)
