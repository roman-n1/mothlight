package io.github.mothlight.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.mothlight.*
import io.github.mothlight.compose.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class DatabaseSchemaUiTest {
    @Test fun switchesBetweenRowsAndSchemaWithoutKeepingHiddenCollectors() = runDesktopComposeUiTest(width = 900, height = 850) {
        var active = 0
        var schemaReads = 0
        val source = object : DatabaseSource {
            override suspend fun tables() = listOf("items")
            override suspend fun page(table: String, limit: Int, offset: Int) = DatabasePage(table, listOf("name"), listOf(listOf(DatabaseCell.Text("row value"))), 1, offset, limit)
            override suspend fun schema(table: String): DatabaseSchema {
                schemaReads++
                return DatabaseSchema.Available(listOf(
                    DatabaseColumn("id", "INTEGER", true, 1),
                    DatabaseColumn("display_name", "TEXT", false, 0, DatabaseColumnKind.GeneratedVirtual),
                ))
            }
            override fun changes() = flow {
                active++
                try { emit(Unit); awaitCancellation() } finally { active-- }
            }
        }
        val tools = MothlightTools(listOf(databaseTool(source)))
        val mothlight = Mothlight()
        setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), tools) } }
        onNodeWithTag("database-picker").performClick()
        onNodeWithText("items").performClick()
        onNodeWithText("row value").assertIsDisplayed()
        runOnIdle { assertEquals(1, schemaReads); assertEquals(2, active) }
        onNodeWithTag("database-schema").performClick()
        onNodeWithText("INTEGER").assertIsDisplayed()
        onNodeWithText("display_name").assertIsDisplayed()
        onNodeWithText("row value").assertDoesNotExist()
        runOnIdle { assertEquals(2, schemaReads); assertEquals(2, active) }
        val screenshot = File("build/reports/demo-ui/database-schema.png")
        screenshot.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", screenshot)
        onNodeWithTag("database-data").performClick()
        onNodeWithText("row value").assertIsDisplayed()
        runOnIdle { assertEquals(2, active) }
    }
}
