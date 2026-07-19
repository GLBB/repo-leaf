package io.github.glbb.repoleaf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KnowledgeReaderApp() }
    }
}

private data class DocumentItem(
    val title: String,
    val path: String,
    val type: String,
    val color: Color,
)

private val demoDocuments = listOf(
    DocumentItem("项目入门", "指南/README.md", "MD", Color(0xFF4F6BED)),
    DocumentItem("系统架构", "研发/architecture.html", "HTML", Color(0xFF00897B)),
    DocumentItem("产品手册", "产品/manual.pdf", "PDF", Color(0xFFE45757)),
    DocumentItem("术语清单", "资料/glossary.xlsx", "XLSX", Color(0xFF2E7D32)),
)

@Composable
private fun KnowledgeReaderApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
            Scaffold(containerColor = Color.Transparent) { contentPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text("我的知识库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "已同步 · 4 份资料",
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                            color = Color(0xFF657080),
                        )
                    }
                    items(demoDocuments) { document -> DocumentCard(document) }
                    item {
                        Text(
                            "MVP 工程骨架 · 下一步接入仓库同步和格式渲染器",
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = Color(0xFF7A8492),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(document: DocumentItem) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .size(48.dp)
                    .background(document.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(document.type, color = document.color, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(document.title, fontWeight = FontWeight.SemiBold)
                Text(document.path, color = Color(0xFF737D8C), style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = Color(0xFF9AA2AE), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KnowledgeReaderPreview() = KnowledgeReaderApp()
