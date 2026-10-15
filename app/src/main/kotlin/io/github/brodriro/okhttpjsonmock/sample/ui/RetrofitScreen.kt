package io.github.brodriro.okhttpjsonmock.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.brodriro.okhttpjsonmock.sample.SampleViewModel
import io.github.brodriro.okhttpjsonmock.sample.catalog.Feature
import io.github.brodriro.okhttpjsonmock.sample.ui.theme.CodeStyle

/** The typed calls of `UsersService`, answered by the same mock files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetrofitScreen(feature: Feature, viewModel: SampleViewModel, onBack: () -> Unit) {
    val state = viewModel.retrofit
    Scaffold(topBar = { DetailTopBar(feature.title, onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("retrofit-screen"),
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Intro(feature.description) }
            item {
                Button(
                    onClick = viewModel::loadRetrofit,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().testTag("run-retrofit"),
                ) { Text("Run the service calls") }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { CodeBlock(error, color = MaterialTheme.colorScheme.error) } }
            state.created?.let { item { ResultCard("createUser(ana): Response<UserModel>", it) } }
            state.missing?.let { item { ResultCard("findUser(9): Response<UserModel>", it) } }
            if (state.users.isNotEmpty()) {
                item { SectionLabel("getUsers(page = 1): List<UserModel>", Modifier.padding(top = 8.dp)) }
                items(state.users, key = { it.id }) { user ->
                    SampleCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            leadingContent = { GlyphTile(user.name.take(1)) },
                            headlineContent = { Text("${user.name} ${user.lastName}") },
                            supportingContent = {
                                val phones = user.phoneNumbers.orEmpty()
                                Text(
                                    "Age ${user.age} · ${if (phones.isEmpty()) "no phone numbers" else phones.joinToString()}",
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(call: String, result: String) {
    SampleCard(Modifier.fillMaxWidth().testTag("retrofit-result")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(call, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(result, style = CodeStyle)
        }
    }
}
