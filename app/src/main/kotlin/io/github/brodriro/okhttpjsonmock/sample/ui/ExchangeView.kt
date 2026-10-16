package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brodriro.okhttpjsonmock.sample.mock.BodyPreview
import io.github.brodriro.okhttpjsonmock.sample.mock.Exchange
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.CodeStyle

/** Everything that came back for one request: status, mock file, headers and the body. */
@Composable
fun ExchangeView(exchange: Exchange, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().animateContentSize().testTag("exchange"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(exchange.statusCode, exchange.reason)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${exchange.elapsedMillis} ms",
                style = CodeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (exchange.error != null) {
            CodeBlock(exchange.error, color = MaterialTheme.colorScheme.error)
            return@Column
        }
        Column {
            KeyValue("Mock file", exchange.mockFile ?: "none")
            KeyValue("Content-Type", exchange.contentType ?: "none")
            exchange.headers
                .filterNot { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
                .forEach { (name, value) -> KeyValue(name, value) }
        }
        BodyView(exchange.preview)
    }
}

@Composable
private fun BodyView(preview: BodyPreview) {
    when (preview) {
        BodyPreview.Empty -> Text(
            text = "No body",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is BodyPreview.Image -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Image(
                bitmap = preview.bitmap,
                contentDescription = "Image body, ${preview.width} by ${preview.height}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            )
            Caption("Decoded by BitmapFactory · ${preview.width}×${preview.height}")
        }
        is BodyPreview.Pdf -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                bitmap = preview.firstPage,
                contentDescription = "First page of the PDF body",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(180.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .padding(1.dp),
            )
            Caption("Rendered by PdfRenderer · ${preview.pageCount} page(s)")
        }
        is BodyPreview.Text -> ExpandableCode(preview.text)
        is BodyPreview.Binary -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CodeBlock(preview.hex)
            Caption("Binary · ${preview.size} bytes")
        }
    }
}

/** Long bodies start folded at a few lines. */
@Composable
private fun ExpandableCode(text: String) {
    val lines = text.lines().size
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column {
        Text(
            text = text,
            style = CodeStyle,
            maxLines = if (expanded) Int.MAX_VALUE else FOLDED_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(12.dp),
        )
        if (lines > FOLDED_LINES) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Show all $lines lines")
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val FOLDED_LINES = 12
