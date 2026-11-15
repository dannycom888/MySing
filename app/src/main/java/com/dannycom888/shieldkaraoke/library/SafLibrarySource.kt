package com.dannycom888.shieldkaraoke.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Walks a user-picked folder (SAF tree — the way an external hard
 * disk is reached on Shield/Android TV) and lists every file that
 * sits inside one of the four category folders, wherever those
 * folders are under the picked root (root itself may be a category
 * folder, or contain them, or contain a "karaoke" folder that does).
 */
object SafLibrarySource {

    fun listFiles(context: Context, treeUri: Uri): List<RawFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<RawFile>()
        walk(root, inheritedCategory = null, depth = 0, out = out)
        return out
    }

    private fun walk(
        dir: DocumentFile,
        inheritedCategory: String?,
        depth: Int,
        out: MutableList<RawFile>,
    ) {
        if (depth > 4) return
        val ownCategory = Category.fromFolderName(dir.name ?: "")?.folderName ?: inheritedCategory
        val children = try { dir.listFiles() } catch (e: Exception) { return }
        for (child in children) {
            if (child.isDirectory) {
                walk(child, ownCategory, depth + 1, out)
            } else if (ownCategory != null) {
                val name = child.name ?: continue
                out += RawFile(folderName = ownCategory, fileName = name, ref = child.uri.toString())
            }
        }
    }
}
