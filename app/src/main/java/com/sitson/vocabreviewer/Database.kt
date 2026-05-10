package com.sitson.vocabreviewer

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity
data class WordEntity(
    @PrimaryKey val wordName: String,
    val phonetic: String,
    val definition: String,
    val exampleSentence: String,
    val exampleTranslation: String,
    val sourceFileName: String,
    val isMastered: Boolean = false
)

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Query("SELECT * FROM WordEntity WHERE isMastered = 0 AND (:sourceFileName IS NULL OR sourceFileName = :sourceFileName) ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomUnmasteredWord(sourceFileName: String?): WordEntity?

    @Query("SELECT * FROM WordEntity WHERE isMastered = 0 AND (:sourceFileName IS NULL OR sourceFileName = :sourceFileName)")
    suspend fun getUnmasteredWords(sourceFileName: String?): List<WordEntity>

    @Query("SELECT * FROM WordEntity WHERE isMastered = 1")
    suspend fun getMasteredWords(): List<WordEntity>

    @Query("SELECT DISTINCT sourceFileName FROM WordEntity")
    fun getAllGroups(): Flow<List<String>>

    @Query("DELETE FROM WordEntity WHERE sourceFileName = :sourceFileName")
    suspend fun deleteWordsBySource(sourceFileName: String)

    @Query("UPDATE WordEntity SET sourceFileName = :newName WHERE sourceFileName = :oldName")
    suspend fun updateGroupName(oldName: String, newName: String)

    @androidx.room.Delete
    suspend fun deleteWord(word: WordEntity)

    @Query("UPDATE WordEntity SET isMastered = :isMastered WHERE wordName = :wordName")
    suspend fun updateMasteredStatus(wordName: String, isMastered: Boolean)
}

@Database(entities = [WordEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
}
