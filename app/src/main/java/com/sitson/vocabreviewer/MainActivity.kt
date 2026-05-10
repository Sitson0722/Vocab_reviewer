package com.sitson.vocabreviewer

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.sitson.vocabreviewer.ui.theme.VocabReviewerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "vocab-db",
        ).build()
        val repository = WordRepository(db.wordDao())

        setContent {
            VocabReviewerTheme {
                val viewModel: WordViewModel = viewModel(
                    factory = WordViewModelFactory(application, repository)
                )
                val uiState by viewModel.uiState.collectAsState()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                
                var showRenameDialog by remember { mutableStateOf<String?>(null) }
                var newGroupName by remember { mutableStateOf("") }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        val fileName = getFileName(it)
                        contentResolver.openInputStream(it)?.let { inputStream ->
                            viewModel.importWords(inputStream, fileName)
                        }
                    }
                }

                if (showRenameDialog != null) {
                    AlertDialog(
                        onDismissRequest = { showRenameDialog = null },
                        title = { Text("重命名分组") },
                        text = {
                            TextField(
                                value = newGroupName,
                                onValueChange = { newGroupName = it },
                                label = { Text("新名称") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.renameGroup(showRenameDialog!!, newGroupName)
                                showRenameDialog = null
                                newGroupName = ""
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = null }) { Text("取消") }
                        }
                    )
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "词库分组",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleLarge
                            )
                            NavigationDrawerItem(
                                label = { Text("全部单词") },
                                selected = uiState.selectedGroup == null && !uiState.isMasteredView,
                                onClick = {
                                    viewModel.setSelectedGroup(null)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("已掌握") },
                                selected = uiState.isMasteredView,
                                onClick = {
                                    viewModel.setMasteredView()
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            uiState.availableGroups.forEach { group ->
                                NavigationDrawerItem(
                                    label = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(group, modifier = Modifier.weight(1f))
                                            Row {
                                                IconButton(onClick = {
                                                    newGroupName = group
                                                    showRenameDialog = group
                                                }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "重命名", modifier = Modifier.size(20.dp))
                                                }
                                                IconButton(onClick = {
                                                    viewModel.deleteGroup(group)
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    },
                                    selected = uiState.selectedGroup == group,
                                    onClick = {
                                        viewModel.setSelectedGroup(group)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            @OptIn(ExperimentalMaterial3Api::class)
                            TopAppBar(
                                title = { Text("单词复习") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                                    }
                                }
                            )
                        },
                        floatingActionButton = {
                            FloatingActionButton(onClick = { launcher.launch("text/plain") }) {
                                Icon(Icons.Default.Add, contentDescription = "导入词库")
                            }
                        },
                        floatingActionButtonPosition = FabPosition.End
                    ) { innerPadding ->
                        ReviewScreenContent(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "words.txt"
    }
}

@Composable
fun ReviewScreenContent(viewModel: WordViewModel, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        ReviewScreen(viewModel = viewModel)
    }
}

class WordViewModelFactory(
    private val application: Application,
    private val repository: WordRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WordViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
