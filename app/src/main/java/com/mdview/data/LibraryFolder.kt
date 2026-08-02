package com.mdview.data

/**
 * A folder the user made to group documents with.
 *
 * **Nothing here touches the file system.** No directory is created, renamed or deleted
 * for one of these; the documents stay exactly where the Storage Access Framework granted
 * them, and a folder is only a label this app keeps in its own index. That is not a
 * simplification — the app holds per-file URI grants rather than paths, so it could not
 * create a real directory even if the feature wanted one.
 *
 * Named `LibraryFolder` rather than `Folder` because [FolderGrant] already means *a real
 * folder on the device that MdView may read images from*, which is very nearly the
 * opposite of this. The user sees the word twice; the code must not.
 *
 * [id] is minted once in [LibraryStore.createFolder] and never shown. It exists so
 * renaming a folder does not orphan everything filed in it. Unlike a skin id it never
 * becomes a filename, so it carries no charset rule — it is only ever an escaped TSV
 * field and a key.
 */
data class LibraryFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
)

/** Why a folder name was refused, so the dialog can say which rule was broken. */
enum class FolderNameProblem { Blank, TooLong, Duplicate }

/**
 * The naming rules, in one place because two callers need the same answer.
 *
 * [LibraryStore] applies them as the authority — it is the only thing that may write —
 * while the dialog applies them to render an inline error and to decide whether it may
 * close. Splitting them would mean a name the dialog accepted and the store silently
 * dropped, which looks exactly like the folder failing to save.
 */
object FolderNames {

    /**
     * Folders are chips on one horizontal strip. Past this the strip is a scroll the user
     * has to hunt through, which is worse than the disorder it was meant to fix.
     */
    const val MAX_FOLDERS = 20

    /** Long enough for a real label, short enough that a chip stays a chip. */
    const val MAX_LENGTH = 40

    /**
     * Checks [name] against [existing], returning null when it is usable.
     *
     * [excluding] is the folder being renamed, which must not collide with itself. The
     * duplicate check ignores case because two chips reading "Work" and "work" are
     * indistinguishable on the strip, and filing into the wrong one is silent.
     */
    fun problemWith(
        name: String,
        existing: List<LibraryFolder>,
        excluding: String? = null,
    ): FolderNameProblem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> FolderNameProblem.Blank
            trimmed.length > MAX_LENGTH -> FolderNameProblem.TooLong
            existing.any {
                it.id != excluding && it.name.equals(trimmed, ignoreCase = true)
            } -> FolderNameProblem.Duplicate

            else -> null
        }
    }

    fun isFull(existing: List<LibraryFolder>): Boolean = existing.size >= MAX_FOLDERS
}
