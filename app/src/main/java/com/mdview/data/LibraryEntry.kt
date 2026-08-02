package com.mdview.data

/**
 * One row of the document library — everything the dashboard needs to draw a card
 * without touching the file it describes.
 *
 * [uri] is a String rather than an `android.net.Uri` deliberately: the JVM stub returns
 * null from `Uri.parse` under `unitTests.isReturnDefaultValues`, which is the reason
 * `MainViewModelTest` had to move to `androidTest`. Keeping the library keyed by String
 * lets its store and codec stay unit-testable.
 */
data class LibraryEntry(
    val uri: String,
    val displayName: String,
    /** The document's first heading, or its front-matter `title:`. Null when it has neither. */
    val title: String? = null,
    /** A short, single-line excerpt of the body. */
    val excerpt: String = "",
    val lastOpened: Long = 0L,
    val isFavorite: Boolean = false,
    /**
     * Whether a *write* grant was actually persisted. Providers may hand out read-only
     * access, in which case saving will fail — better to disable Save up front than to
     * let the user type for ten minutes and discover it at the end.
     */
    val canWrite: Boolean = true,
    /**
     * Set when the document arrived through an ACTION_VIEW intent whose grant could not
     * be persisted. The entry is still worth showing, but it will stop working once the
     * granting app's permission lapses, so the card says so instead of pretending.
     */
    val isTransient: Boolean = false,
    /**
     * The [LibraryFolder] the user filed this under, or null when it is unfiled.
     *
     * Purely an in-app label: no directory exists for it and the document has not moved
     * on disk. Filing is also a statement that the document is wanted, so a filed row is
     * exempt from eviction the same way a starred one is.
     */
    val folderId: String? = null,
) {
    /** What the card shows as its headline. */
    val heading: String get() = title?.takeIf { it.isNotBlank() } ?: displayName
}

/**
 * The library as the UI consumes it. Distinguishing [Loading] from [Empty] keeps the
 * first frame from flashing "no documents yet" before the file has been read.
 */
sealed interface LibraryState {
    data object Loading : LibraryState
    data object Empty : LibraryState
    data class Content(val entries: List<LibraryEntry>) : LibraryState
}
