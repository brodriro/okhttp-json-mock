package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.brodriro.okhttpjsonmock.sample.SampleViewModel
import io.github.brodriro.okhttpjsonmock.sample.catalog.Catalog
import io.github.brodriro.okhttpjsonmock.sample.catalog.FeatureKind

/** The catalog and one feature screen at a time. Back returns to the catalog. */
@Composable
fun SampleApp(viewModel: SampleViewModel) {
    var openFeature by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { openFeature = null }
    BackHandler(enabled = openFeature != null, onBack = close)
    AnimatedContent(
        targetState = openFeature,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "screen",
    ) { id ->
        if (id == null) {
            CatalogScreen(Catalog.features) { openFeature = it.id }
        } else {
            val feature = Catalog.feature(id)
            when (feature.kind) {
                FeatureKind.REQUESTS -> FeatureScreen(feature, viewModel, close)
                FeatureKind.NETWORK -> NetworkScreen(feature, viewModel, close)
                FeatureKind.CALLS -> CallsScreen(feature, viewModel, close)
                FeatureKind.RETROFIT -> RetrofitScreen(feature, viewModel, close)
            }
        }
    }
}
