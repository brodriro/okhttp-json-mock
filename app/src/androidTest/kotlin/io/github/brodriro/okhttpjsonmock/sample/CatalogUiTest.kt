package io.github.brodriro.okhttpjsonmock.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.brodriro.okhttpjsonmock.sample.catalog.Catalog
import io.github.brodriro.okhttpjsonmock.sample.catalog.Feature
import io.github.brodriro.okhttpjsonmock.sample.catalog.FeatureKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The catalog on a device: every feature is listed and each example answers with the file it promises. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CatalogUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun open(feature: Feature) {
        compose.onNodeWithTag("catalog").performScrollToNode(hasTestTag("feature-${feature.id}"))
        compose.onNodeWithTag("feature-${feature.id}").performClick()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun catalogListsEveryFeature() {
        Catalog.features.forEach { feature ->
            compose.onNodeWithTag("catalog").performScrollToNode(hasTestTag("feature-${feature.id}"))
            compose.onNodeWithText(feature.title).assertExists()
        }
    }

    @Test
    fun everyExampleIsAnsweredByItsMockFile() {
        Catalog.features.filter { it.kind == FeatureKind.REQUESTS }.forEach { feature ->
            open(feature)
            feature.examples.forEach { example ->
                compose.onNodeWithTag("feature-screen").performScrollToNode(hasTestTag("example-${example.id}"))
                compose.onNodeWithTag("send-${example.id}").performClick()
                val card = hasAnyAncestor(hasTestTag("example-${example.id}"))
                // Without a mock the default answer names no file and the strict one fails the call.
                val expected = when {
                    example.request.strict -> hasText("failed")
                    example.answeredBy == "nothing" -> hasText("none")
                    else -> hasText(example.answeredBy)
                }
                compose.waitUntilAtLeastOneExists(card and expected, timeoutMillis = 10_000)
                compose.onNode(card and hasTestTag("exchange"), useUnmergedTree = true).assertExists()
            }
            pressBack()
        }
    }

    @Test
    fun imageAndPdfBodiesArePreviewed() {
        open(Catalog.feature("files"))
        mapOf(
            "file-logo.png" to "Decoded by BitmapFactory · 640×320",
            "file-manual.pdf" to "Rendered by PdfRenderer · 1 page(s)",
        ).forEach { (id, caption) ->
            compose.onNodeWithTag("feature-screen").performScrollToNode(hasTestTag("example-$id"))
            compose.onNodeWithTag("send-$id").performClick()
            compose.waitUntilAtLeastOneExists(hasText(caption), 10_000)
        }
    }

    @Test
    fun networkBurstAndRecordedCalls() {
        open(Catalog.feature("network"))
        compose.onNodeWithTag("network-screen").performScrollToNode(hasTestTag("burst"))
        compose.onNodeWithTag("burst").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("burst-summary"), 10_000)
        pressBack()

        compose.onNodeWithContentDescription("Recorded calls").performClick()
        compose.onNodeWithText("Calls (10)").assertExists()
        compose.onNodeWithContentDescription("Clear the history").performClick()
        compose.onNodeWithText("Calls (0)").assertExists()
    }

    @Test
    fun recordedCallsShowTheBodyThatWasSent() {
        open(Catalog.feature("methods"))
        mapOf("post" to "api/v1/users.post.json", "delete" to "api/v1/users/1.delete.json").forEach { (id, file) ->
            compose.onNodeWithTag("feature-screen").performScrollToNode(hasTestTag("example-$id"))
            compose.onNodeWithTag("send-$id").performClick()
            compose.waitUntilAtLeastOneExists(hasAnyAncestor(hasTestTag("example-$id")) and hasText(file), 10_000)
        }
        pressBack()

        compose.onNodeWithContentDescription("Recorded calls").performClick()
        compose.onNodeWithText("Calls (2)").assertExists()
        // Only the POST carried a body; the DELETE row shows none.
        compose.onAllNodesWithTag("sent-body").assertCountEquals(1)
        compose.onNodeWithText("""Sent: {"name":"Ana"}""").assertExists()
    }

    @Test
    fun theListedProviderOpensOnlyTheFileThatAnswers() {
        open(Catalog.feature("listed"))
        listOf("id-plain", "id-listed").forEach { id ->
            compose.onNodeWithTag("feature-screen").performScrollToNode(hasTestTag("example-$id"))
            compose.onNodeWithTag("send-$id").performClick()
            val answered = hasAnyAncestor(hasTestTag("example-$id")) and hasText("api/v1/users/{id}.json")
            compose.waitUntilAtLeastOneExists(answered, 10_000)
        }

        val oneLookup = hasText("1 provide() call")
        compose.onNode(hasAnyAncestor(hasTestTag("example-id-listed")) and oneLookup, useUnmergedTree = true)
            .assertExists()
        compose.onNode(hasAnyAncestor(hasTestTag("example-id-plain")) and oneLookup, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun retrofitServiceRunsOnTheMocks() {
        open(Catalog.feature("retrofit"))
        compose.onNodeWithTag("run-retrofit").performClick()
        compose.waitUntilAtLeastOneExists(hasText("201 · Location: /api/v1/users/4"), 10_000)
        compose.onNodeWithTag("retrofit-screen").performScrollToNode(hasText("Jack Dane"))
    }
}
