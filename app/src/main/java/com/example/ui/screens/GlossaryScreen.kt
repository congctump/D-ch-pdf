package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlossaryItemCard
import com.example.ui.viewmodel.MainViewModel

@Composable
fun GlossaryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val terms by viewModel.glossaryTerms.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf("Tất cả", "Cardiology", "Neurology", "Oncology", "Pharmacology", "Diagnostics")

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_term_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Thêm từ mới")
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("glossary_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Từ Điển Y Học Anh - Việt (${terms.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("glossary_search_input"),
                placeholder = { Text("Tìm kiếm thuật ngữ tiếng Anh hoặc tiếng Việt...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category filter tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.updateCategory(cat) },
                        label = { Text(cat) },
                        modifier = Modifier.testTag("filter_chip_$cat")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (terms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không tìm thấy thuật ngữ phù hợp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(terms, key = { it.id }) { term ->
                        GlossaryItemCard(
                            term = term,
                            onClick = { viewModel.selectGlossaryTerm(term) },
                            onDelete = {
                                viewModel.deleteTerm(term.id)
                                Toast.makeText(context, "Đã xóa thuật ngữ", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomTermDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { en, vn, cat, def ->
                viewModel.addCustomTerm(en, vn, cat, def)
                showAddDialog = false
                Toast.makeText(context, "Đã thêm thuật ngữ $en vào từ điển", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun AddCustomTermDialog(
    onDismiss: () -> Unit,
    onAdd: (termEn: String, termVn: String, category: String, definitionVn: String) -> Unit
) {
    var termEn by remember { mutableStateOf("") }
    var termVn by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Cardiology") }
    var definitionVn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm Thuật Ngữ Y Khoa Mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = termEn,
                    onValueChange = { termEn = it },
                    label = { Text("Thuật ngữ Tiếng Anh (ví dụ: Dyspnea)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = termVn,
                    onValueChange = { termVn = it },
                    label = { Text("Dịch Tiếng Việt (ví dụ: Khó thở)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Chuyên khoa (Cardiology, Neurology...)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = definitionVn,
                    onValueChange = { definitionVn = it },
                    label = { Text("Giải thích chi tiết (Không bắt buộc)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (termEn.isNotBlank() && termVn.isNotBlank()) {
                        onAdd(termEn, termVn, category, definitionVn)
                    }
                },
                enabled = termEn.isNotBlank() && termVn.isNotBlank()
            ) {
                Text("Thêm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
