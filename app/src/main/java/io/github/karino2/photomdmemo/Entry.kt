package io.github.karino2.photomdmemo

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.Date

data class Entry(val date: Date, val name: String, val uri: Uri) {
    companion object {
        fun from(dir: DocumentFile): List<Entry> {
            return dir.listFiles()
                .filter { it.name!!.endsWith(".md") }
                .sortedByDescending { it.name!! }
                .map{ Entry(Date(it.lastModified()), it.name!!, it.uri) }
                .toList()
        }
    }
}
