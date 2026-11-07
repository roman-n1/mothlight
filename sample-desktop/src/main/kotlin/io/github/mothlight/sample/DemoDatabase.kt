package io.github.mothlight.sample

import androidx.room3.*
import kotlinx.coroutines.flow.Flow
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

@Entity(tableName = "publications")
data class Publication(@PrimaryKey val id: String, val title: String, val description: String, val subscribed: Boolean = false)
@Entity(tableName = "articles")
data class Article(@PrimaryKey val id: Long, val publicationId: String, val title: String, val summary: String, val body: String, val minutes: Int,
    @ColumnInfo(defaultValue = "''") val sourceUrl: String = "",
    @ColumnInfo(defaultValue = "''") val author: String = "")
@Entity(tableName = "article_content")
data class ArticleContent(@PrimaryKey val articleId: Long, val text: String)

@Entity(tableName = "bookmarks")
data class Bookmark(@PrimaryKey val articleId: Long)

@Dao
interface ReaderDao {
    @Query("SELECT * FROM article_content WHERE articleId = :id") fun articleContent(id: Long): Flow<ArticleContent?>
    @Upsert suspend fun storeContent(content: ArticleContent)
    @Query("SELECT * FROM publications ORDER BY title") fun publications(): Flow<List<Publication>>
    @Query("SELECT * FROM articles ORDER BY id DESC") fun articles(): Flow<List<Article>>
    @Query("SELECT * FROM bookmarks") fun bookmarks(): Flow<List<Bookmark>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedPublications(items: List<Publication>)
    @Query("UPDATE publications SET title = :title, description = :description WHERE id = :id")
    suspend fun updatePublication(id: String, title: String, description: String)
    @Query("DELETE FROM articles WHERE sourceUrl = '' AND id NOT IN (SELECT articleId FROM bookmarks)")
    suspend fun removeUnsavedDemoArticles()
    @Transaction suspend fun replaceCatalog(items: List<Article>) {
        storeArticles(items)
        removeUnsavedDemoArticles()
    }
    @Upsert suspend fun storeArticles(items: List<Article>)
    @Query("UPDATE publications SET subscribed = NOT subscribed WHERE id = :id") suspend fun toggleSubscription(id: String)
    @Query("SELECT COUNT(*) FROM bookmarks WHERE articleId = :id") suspend fun bookmarkCount(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun save(bookmark: Bookmark)
    @Query("DELETE FROM bookmarks WHERE articleId = :id") suspend fun unsave(id: Long)
    @Transaction suspend fun toggleBookmark(id: Long) {
        if (bookmarkCount(id) == 0) save(Bookmark(id)) else unsave(id)
    }
}
@Database(entities = [Publication::class, Article::class, Bookmark::class, ArticleContent::class], version = 3, exportSchema = false)
abstract class DemoDatabase : RoomDatabase() { abstract fun reader(): ReaderDao }

internal val readerMigration1To2 = Migration(1, 2) { connection ->
    connection.execSQL("ALTER TABLE articles ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
    connection.execSQL("ALTER TABLE articles ADD COLUMN author TEXT NOT NULL DEFAULT ''")
}

internal val readerMigration2To3 = Migration(2, 3) { connection ->
    connection.execSQL("CREATE TABLE IF NOT EXISTS article_content (articleId INTEGER NOT NULL PRIMARY KEY, text TEXT NOT NULL)")
}
