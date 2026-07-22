package io.github.glbb.repoleaf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderToolbarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun toolbarControlsDoNotOverlapAt320Dp() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).testTag("narrow-reader")) {
                    ReaderToolbar(
                        title = "一篇非常长的知识库文档标题",
                        dark = false,
                        playing = false,
                        favorite = false,
                        onBack = {},
                        onPlay = {},
                        onFavorite = {},
                        onSettings = {},
                    )
                }
            }
        }

        val tags = listOf("reader-back", "reader-toolbar-title", "speech-play", "reader-favorite", "reader-settings")
        tags.forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }
        val bounds = tags.map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }

        assertTrue("title must retain visible width", bounds[1].width > 0f)
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("toolbar items overlap: $left and $right", left.right <= right.left + .5f)
        }
    }
}
