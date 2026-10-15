package io.github.brodriro.okhttpjsonmock.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.brodriro.okhttpjsonmock.sample.ui.SampleApp
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.SampleTheme

/**
 * A catalog of every feature of okhttp-json-mock. Each request goes through an `OkHttpMockInterceptor`
 * that reads the mock files in `assets/` with [mock.Playground.provider].
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme { SampleApp(viewModel) }
        }
    }
}
