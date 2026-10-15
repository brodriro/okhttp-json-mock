package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.brodriro.okhttpjsonmock.sample.R
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.CodeStyle
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.SampleTheme

/** Top bar of a detail screen, with a back button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

/** The square monospace label that identifies a feature in the catalog. */
@Composable
fun GlyphTile(glyph: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = CodeStyle.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

/** HTTP method in a fixed-width badge, so paths line up. */
@Composable
fun MethodBadge(method: String) {
    Text(
        text = method,
        style = CodeStyle.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(vertical = 2.dp),
    )
}

/** Status code coloured by its class, or "failed" when the call threw. */
@Composable
fun StatusBadge(code: Int?, reason: String?) {
    val status = SampleTheme.status
    val (container, content) = when (code) {
        null -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        in 200..299 -> status.success
        in 300..399 -> status.redirect
        in 400..499 -> status.clientError
        else -> status.serverError
    }
    val label = if (code == null) "failed" else listOfNotNull(code.toString(), reason).joinToString(" ")
    Text(
        text = label,
        style = CodeStyle.copy(fontWeight = FontWeight.Bold),
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = "Status $label" },
    )
}

/** Small uppercase caption above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Monospace text on a sunken surface. */
@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = CodeStyle,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
    )
}

/** A label and its monospace value on one line, wrapping the value when it is long. */
@Composable
fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = key,
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(text = value, style = CodeStyle, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Paragraph that introduces a screen. */
@Composable
fun Intro(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The one card style of the sample: outlined, on the lightest surface, clickable when [onClick] is set. */
@Composable
fun SampleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    if (onClick == null) {
        OutlinedCard(modifier = modifier, colors = colors, content = content)
    } else {
        OutlinedCard(onClick = onClick, modifier = modifier, colors = colors, content = content)
    }
}
