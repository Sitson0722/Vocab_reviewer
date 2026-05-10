package com.sitson.vocabreviewer

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class ReviewMode {
    WORD_FIRST, // 看词模式
    SENTENCE_FIRST // 看句模式
}

data class WordUiState(
    val currentWord: WordEntity? = null,
    val isFlipped: Boolean = false,
    val reviewMode: ReviewMode = ReviewMode.WORD_FIRST,
    val availableGroups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val isMasteredView: Boolean = false, // 是否处于“已掌握”列表视图
    val history: List<WordEntity> = emptyList(),
    val unreviewedDeck: List<WordEntity> = emptyList(),
    val currentIndex: Int = -1,
    val noMoreWords: Boolean = false
)

class WordViewModel(
    application: Application,
    private val repository: WordRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WordUiState())
    val uiState: StateFlow<WordUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)
            }
        }
        
        viewModelScope.launch {
            repository.allGroups.collect { groups ->
                _uiState.update { it.copy(availableGroups = groups) }
            }
        }
        
        initializeDeck()
    }

    private fun initializeDeck() {
        viewModelScope.launch {
            val state = uiState.value
            val words = if (state.isMasteredView) {
                repository.getMasteredWords().shuffled()
            } else {
                repository.getUnmasteredWords(state.selectedGroup).shuffled()
            }
            
            if (words.isNotEmpty()) {
                _uiState.update { it.copy(
                    unreviewedDeck = words.drop(1),
                    history = listOf(words.first()),
                    currentIndex = 0,
                    currentWord = words.first(),
                    isFlipped = false,
                    noMoreWords = false
                ) }
            } else {
                _uiState.update { it.copy(
                    unreviewedDeck = emptyList(),
                    history = emptyList(),
                    currentIndex = -1,
                    currentWord = null,
                    noMoreWords = true
                ) }
            }
        }
    }

    fun fetchNextWord() {
        if (uiState.value.unreviewedDeck.isEmpty()) {
            _uiState.update { it.copy(noMoreWords = true) }
            return
        }
        
        _uiState.update { state ->
            val nextWord = state.unreviewedDeck.first()
            state.copy(
                history = state.history + nextWord,
                unreviewedDeck = state.unreviewedDeck.drop(1),
                noMoreWords = false
            )
        }
    }

    fun onPageChanged(index: Int) {
        val history = uiState.value.history
        if (index >= 0 && index < history.size) {
            _uiState.update { it.copy(
                currentIndex = index,
                currentWord = history[index],
                isFlipped = false
            ) }
        }
    }

    fun restartSession() {
        initializeDeck()
    }

    fun setSelectedGroup(group: String?) {
        _uiState.update { it.copy(
            selectedGroup = group,
            isMasteredView = false,
            history = emptyList(),
            currentIndex = -1,
            currentWord = null,
            noMoreWords = false
        ) }
        initializeDeck()
    }

    fun setMasteredView() {
        _uiState.update { it.copy(
            isMasteredView = true,
            selectedGroup = null,
            history = emptyList(),
            currentIndex = -1,
            currentWord = null,
            noMoreWords = false
        ) }
        initializeDeck()
    }

    fun flipCard() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun setReviewMode(mode: ReviewMode) {
        _uiState.update { it.copy(reviewMode = mode, isFlipped = false) }
    }

    fun toggleMasteredStatus() {
        val current = uiState.value.currentWord ?: return
        val newStatus = !current.isMastered
        viewModelScope.launch {
            repository.updateMasteredStatus(current.wordName, newStatus)
            removeWordFromActiveSession(current)
        }
    }

    fun deleteCurrentWord() {
        val current = uiState.value.currentWord ?: return
        viewModelScope.launch {
            repository.deleteWord(current)
            removeWordFromActiveSession(current)
        }
    }

    private fun removeWordFromActiveSession(word: WordEntity) {
        _uiState.update { state ->
            val newHistory = state.history.filter { it.wordName != word.wordName }
            val newDeck = state.unreviewedDeck.filter { it.wordName != word.wordName }
            
            val newIndex = if (newHistory.isEmpty()) -1 
                          else state.currentIndex.coerceIn(0, newHistory.size - 1)
            val newCurrentWord = if (newIndex != -1) newHistory[newIndex] else null

            state.copy(
                history = newHistory,
                unreviewedDeck = newDeck,
                currentIndex = newIndex,
                currentWord = newCurrentWord,
                isFlipped = false,
                noMoreWords = newHistory.isEmpty() && newDeck.isEmpty()
            )
        }
    }

    fun deleteGroup(groupName: String) {
        viewModelScope.launch {
            repository.deleteWordsBySource(groupName)
            if (uiState.value.selectedGroup == groupName) {
                setSelectedGroup(null)
            } else {
                _uiState.update { state ->
                    state.copy(
                        history = state.history.filter { it.sourceFileName != groupName },
                        unreviewedDeck = state.unreviewedDeck.filter { it.sourceFileName != groupName }
                    )
                }
            }
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameGroup(oldName, newName)
            _uiState.update { state ->
                val renameOp = { w: WordEntity -> 
                    if (w.sourceFileName == oldName) w.copy(sourceFileName = newName) else w 
                }
                state.copy(
                    history = state.history.map(renameOp),
                    unreviewedDeck = state.unreviewedDeck.map(renameOp),
                    selectedGroup = if (state.selectedGroup == oldName) newName else state.selectedGroup
                )
            }
        }
    }

    fun speakWord() {
        val text = uiState.value.currentWord?.wordName ?: return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun speakSentence() {
        val text = uiState.value.currentWord?.exampleSentence ?: return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun importWords(inputStream: java.io.InputStream, fileName: String) {
        viewModelScope.launch {
            repository.importWordsFromStream(inputStream, fileName)
            if (uiState.value.selectedGroup == null || uiState.value.selectedGroup == fileName) {
                initializeDeck()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
