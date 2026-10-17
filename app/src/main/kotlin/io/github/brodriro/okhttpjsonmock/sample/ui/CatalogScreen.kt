package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.brodriro.okhttpjsonmock.sample.R
import io.github.brodriro.okhttpjsonmock.sample.catalog.Catalog
import io.github.brodriro.okhttpjsonmock.sample.catalog.Feature
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.SampleTheme

/** The list of every feature of the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(features: List<Feature>, onOpen: (Feature) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("okhttp-json-mock")
                        Text(
                            text = "Every feature, answered from assets/",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpen(Catalog.feature("calls")) }) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = "Recorded calls")
                    }
                    IconButton(onClick = { onOpen(Catalog.feature("network")) }) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = "Network settings")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("catalog"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(features, key = { it.id }) { feature -> FeatureRow(feature) { onOpen(feature) } }
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature, onClick: () -> Unit) {
    SampleCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("feature-${feature.id}")) {
        ListItem(
            leadingContent = { GlyphTile(feature.glyph) },
            headlineContent = { Text(feature.title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(feature.summary) },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CatalogPreview() {
    SampleTheme { CatalogScreen(Catalog.features) {} }
}
