package io.github.glbb.repoleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepoLeafEndToEndTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test fun publicRepositorySyncAndMarkdownReader() {
        val token = InstrumentationRegistry.getArguments().getString("githubToken").orEmpty()
        assumeTrue("githubToken instrumentation argument is required for network E2E", token.isNotBlank())
        syncAndOpen("GLBB", "repo-leaf", token)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun privateRepositorySyncAndMarkdownReader() {
        val token = InstrumentationRegistry.getArguments().getString("githubToken").orEmpty()
        assumeTrue("githubToken instrumentation argument is required", token.isNotBlank())
        syncAndOpen("GLBB", "knowledge", token)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun syncAndOpen(owner: String, repo: String, token: String = "") {
        compose.onNodeWithTag("repository-list").assertIsDisplayed()
        compose.onNodeWithTag("add-repository").performClick()
        compose.onNodeWithTag("owner").performTextInput(owner)
        compose.onNodeWithTag("repo").performTextInput(repo)
        if (token.isNotBlank()) compose.onNodeWithTag("token").performTextInput(token)
        compose.onNodeWithTag("confirm-add").performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("document-list"), timeoutMillis = 120_000)
        compose.onNodeWithTag("document-list").assertIsDisplayed()
        compose.onAllNodesWithTag("document-item")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("markdown-reader"), timeoutMillis = 10_000)
        compose.onNodeWithTag("markdown-reader").assertIsDisplayed()
        compose.onNodeWithTag("reader-favorite").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilAtLeastOneExists(hasTestTag("repository-list"), timeoutMillis = 10_000)
        compose.onNodeWithTag("library-favorites").performClick()
        compose.onAllNodesWithTag("document-item")[0].assertIsDisplayed()
        compose.onNodeWithTag("library-all").performClick()
        compose.onNodeWithTag("global-search").performTextInput("README")
        compose.onAllNodesWithTag("document-item")[0].assertIsDisplayed()
    }
}
