package com.sitson.vocabreviewer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class WordRepository(private val wordDao: WordDao) {

    val allGroups = wordDao.getAllGroups()

    suspend fun getRandomUnmasteredWord(group: String?): WordEntity? = wordDao.getRandomUnmasteredWord(group)

    suspend fun getUnmasteredWords(group: String?): List<WordEntity> = wordDao.getUnmasteredWords(group)

    suspend fun getMasteredWords(): List<WordEntity> = wordDao.getMasteredWords()

    suspend fun deleteWordsBySource(sourceFileName: String) = wordDao.deleteWordsBySource(sourceFileName)

    suspend fun renameGroup(oldName: String, newName: String) = wordDao.updateGroupName(oldName, newName)

    suspend fun deleteWord(word: WordEntity) = wordDao.deleteWord(word)

    suspend fun updateMasteredStatus(wordName: String, isMastered: Boolean) =
        wordDao.updateMasteredStatus(wordName, isMastered)

    /**
     * 解析 TXT 输入流并存入数据库
     * 格式：单词|音标|释义|例句|翻译
     */
    suspend fun importWordsFromStream(inputStream: InputStream, fileName: String) {
        withContext(Dispatchers.IO) {
            inputStream.use { stream ->
                val words = mutableListOf<WordEntity>()
                try {
                    stream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) {
                                val parts = line.split("|")
                                if (parts.size >= 5) {
                                    words.add(
                                        WordEntity(
                                            wordName = parts[0].trim(),
                                            phonetic = parts[1].trim(),
                                            definition = parts[2].trim(),
                                            exampleSentence = parts[3].trim(),
                                            exampleTranslation = parts[4].trim(),
                                            sourceFileName = fileName,
                                            isMastered = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (words.isNotEmpty()) {
                        wordDao.insertWords(words)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
