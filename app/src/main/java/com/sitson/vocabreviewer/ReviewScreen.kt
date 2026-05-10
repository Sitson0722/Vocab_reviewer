package com.sitson.vocabreviewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(viewModel: WordViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 始终允许滑向下一页（加载页或结束页）
    val pageCount = uiState.history.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // 当 history 或分组改变时，尝试回到第一页
    LaunchedEffect(uiState.selectedGroup) {
        pagerState.scrollToPage(0)
    }

    // 同步 Pager 的页码到 ViewModel
    LaunchedEffect(pagerState.currentPage, uiState.history) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部模式切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = uiState.reviewMode == ReviewMode.WORD_FIRST,
                onClick = { viewModel.setReviewMode(ReviewMode.WORD_FIRST) },
                label = { Text("看词模式") }
            )
            FilterChip(
                selected = uiState.reviewMode == ReviewMode.SENTENCE_FIRST,
                onClick = { viewModel.setReviewMode(ReviewMode.SENTENCE_FIRST) },
                label = { Text("看句模式") }
            )
        }

        if (uiState.isMasteredView) {
            Text(
                text = "当前视图: 已掌握单词",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (uiState.selectedGroup != null) {
            Text(
                text = "当前分组: ${uiState.selectedGroup}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 核心卡片区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = true
            ) { page ->
                if (page < uiState.history.size) {
                    WordCard(uiState.history[page], viewModel, uiState)
                } else {
                    if (uiState.noMoreWords) {
                        EndSessionCard(onRestart = { 
                            viewModel.restartSession()
                            scope.launch { pagerState.scrollToPage(0) }
                        })
                    } else {
                        LoadingCard()
                        LaunchedEffect(Unit) {
                            viewModel.fetchNextWord()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 底部操作区
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.deleteCurrentWord() }) {
                Icon(Icons.Default.Delete, contentDescription = "删除单词", tint = Color.Red)
            }
            
            Button(
                onClick = { viewModel.toggleMasteredStatus() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isMasteredView) Color.Gray else Color(0xFF4CAF50)
                ),
                modifier = Modifier.fillMaxWidth(0.7f),
                enabled = pagerState.currentPage < uiState.history.size
            ) {
                Text(if (uiState.isMasteredView) "标记为未掌握" else "标为已掌握")
            }
            
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun WordCard(word: WordEntity, viewModel: WordViewModel, uiState: WordUiState) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable { viewModel.flipCard() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (uiState.reviewMode == ReviewMode.WORD_FIRST) {
                    if (!uiState.isFlipped) {
                        WordFront(word, onSpeak = { viewModel.speakWord() })
                    } else {
                        WordBack(word, onSpeakSentence = { viewModel.speakSentence() })
                    }
                } else {
                    if (!uiState.isFlipped) {
                        SentenceFront(word, onSpeak = { viewModel.speakSentence() })
                    } else {
                        SentenceBack(word, onSpeakWord = { viewModel.speakWord() })
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                if (uiState.isMasteredView) {
                    Text(
                        text = "来自词库: ${word.sourceFileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = if (uiState.isFlipped) "左右滑动切换 · 点击翻面" else "左右滑动切换 · 点击查看详情",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun EndSessionCard(onRestart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("太棒了！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("该分组下的单词已全部加载完", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRestart) {
                Text("重新开始随机复习")
            }
        }
    }
}

@Composable
fun WordFront(word: WordEntity, onSpeak: () -> Unit) {
    Text(text = word.wordName, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(text = word.phonetic, fontSize = 18.sp, color = Color.Gray)
    IconButton(onClick = onSpeak) {
        Icon(Icons.Default.PlayArrow, contentDescription = "朗读单词")
    }
}

@Composable
fun WordBack(word: WordEntity, onSpeakSentence: () -> Unit) {
    Text(text = word.definition, fontSize = 24.sp, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(16.dp))
    ExampleSentenceText(sentence = word.exampleSentence, keyword = word.wordName)
    Text(text = word.exampleTranslation, fontSize = 16.sp, color = Color.Gray, textAlign = TextAlign.Center)
    IconButton(onClick = onSpeakSentence) {
        Icon(Icons.Default.PlayArrow, contentDescription = "朗读例句")
    }
}

@Composable
fun SentenceFront(word: WordEntity, onSpeak: () -> Unit) {
    ExampleSentenceText(sentence = word.exampleSentence, keyword = word.wordName, fontSize = 22.sp)
    IconButton(onClick = onSpeak) {
        Icon(Icons.Default.PlayArrow, contentDescription = "朗读例句")
    }
}

@Composable
fun SentenceBack(word: WordEntity, onSpeakWord: () -> Unit) {
    Text(text = word.exampleTranslation, fontSize = 18.sp, color = Color.Gray, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = word.wordName, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(text = word.phonetic, fontSize = 18.sp, color = Color.Gray)
    Text(text = word.definition, fontSize = 20.sp, textAlign = TextAlign.Center)
    IconButton(onClick = onSpeakWord) {
        Icon(Icons.Default.PlayArrow, contentDescription = "朗读单词")
    }
}

@Composable
fun ExampleSentenceText(sentence: String, keyword: String, fontSize: androidx.compose.ui.unit.TextUnit = 18.sp) {
    val annotatedString = buildAnnotatedString {
        val lowerSentence = sentence.lowercase()
        val lowerKeyword = keyword.lowercase()
        var startIndex = 0
        while (startIndex < sentence.length) {
            val foundIndex = lowerSentence.indexOf(lowerKeyword, startIndex)
            if (foundIndex == -1) {
                append(sentence.substring(startIndex))
                break
            }
            append(sentence.substring(startIndex, foundIndex))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(sentence.substring(foundIndex, foundIndex + keyword.length))
            }
            startIndex = foundIndex + keyword.length
        }
    }
    
    Text(
        text = annotatedString,
        fontSize = fontSize,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        textAlign = TextAlign.Center
    )
}
