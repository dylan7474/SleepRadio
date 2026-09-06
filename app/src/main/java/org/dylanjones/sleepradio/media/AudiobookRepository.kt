package org.dylanjones.sleepradio.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.core.data.Audiobook
import org.dylanjones.sleepradio.core.data.Chapter
import org.dylanjones.sleepradio.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads audiobooks from a SAF-granted tree. The tree is expected to contain one
 * folder per book (each with chapter audio files), and/or loose single-file books.
 */
@Singleton
class AudiobookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val audioExtensions =
        setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mka")

    suspend fun listBooks(treeUri: String): List<Audiobook> = withContext(io) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return@withContext emptyList()
        val books = ArrayList<Audiobook>()
        for (child in root.listFiles()) {
            when {
                child.isDirectory -> {
                    val count = child.listFiles().count { it.isFile && it.isAudio() }
                    if (count > 0) {
                        books += Audiobook(
                            id = child.uri.toString(),
                            title = child.name ?: "Untitled",
                            chapterCount = count,
                        )
                    }
                }

                child.isFile && child.isAudio() -> {
                    books += Audiobook(
                        id = child.uri.toString(),
                        title = (child.name ?: "Untitled").substringBeforeLast('.'),
                        chapterCount = 1,
                    )
                }
            }
        }
        books.sortedBy { it.title.lowercase() }
    }

    /** Chapters for [bookId] (a folder or single-file URI under [treeUri]). */
    suspend fun chapters(treeUri: String, bookId: String): List<Chapter> = withContext(io) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return@withContext emptyList()
        val book = root.listFiles().firstOrNull { it.uri.toString() == bookId }
            ?: return@withContext emptyList()

        val files = if (book.isDirectory) {
            book.listFiles().filter { it.isFile && it.isAudio() }
        } else {
            listOf(book)
        }
        files
            .sortedBy { (it.name ?: "").lowercase() }
            .mapIndexed { i, f ->
                Chapter(
                    index = i,
                    title = (f.name ?: "Chapter ${i + 1}").substringBeforeLast('.'),
                    uri = f.uri.toString(),
                )
            }
    }

    private fun DocumentFile.isAudio(): Boolean {
        val name = name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in audioExtensions) return true
        return type?.startsWith("audio/") == true
    }
}
