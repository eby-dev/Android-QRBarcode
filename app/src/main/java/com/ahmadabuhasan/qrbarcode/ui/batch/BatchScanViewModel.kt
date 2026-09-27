package com.ahmadabuhasan.qrbarcode.ui.batch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

// Holds the batch list. Deliberately not written to scan history, so a stock
// count of hundreds of codes doesn't bury the user's everyday scans. The list
// survives rotation (ViewModel) and process death (SavedStateHandle).
class BatchScanViewModel(private val state: SavedStateHandle) : ViewModel() {

    // Insertion order = scan order; a re-scanned code moves to the end.
    private val items = LinkedHashMap<String, BatchItem>()

    // When each code was last seen in any frame, to tell "still in view" from
    // "scanned again".
    private val lastSeenAt = HashMap<String, Long>()

    private val _items = MutableLiveData<List<BatchItem>>(emptyList())
    val list: LiveData<List<BatchItem>> = _items

    init {
        val contents = state.get<ArrayList<String>>(KEY_CONTENTS)
        val formats = state.get<ArrayList<String>>(KEY_FORMATS)
        val counts = state.get<IntArray>(KEY_COUNTS)
        val times = state.get<LongArray>(KEY_TIMES)
        if (contents != null && formats != null && counts != null && times != null) {
            contents.indices.forEach { i ->
                val item = BatchItem(contents[i], formats[i], counts[i], times[i])
                items[item.key] = item
            }
            publish()
        }
    }

    // ML Kit reports a code on every frame it stays in view, so a code counts
    // again only after it has been out of view for REPEAT_GAP_MS — i.e. the user
    // moved the item away and scanned it (or another unit of it) again.
    // Returns true when the list changed, so the caller can beep.
    fun onDetected(content: String, format: String, now: Long = System.currentTimeMillis()): Boolean {
        val key = BatchItem.keyOf(content, format)
        val previous = lastSeenAt.put(key, now)
        if (previous != null && now - previous < REPEAT_GAP_MS) return false

        val existing = items.remove(key)
        items[key] = existing?.copy(count = existing.count + 1, lastScannedAt = now)
            ?: BatchItem(content, format, count = 1, lastScannedAt = now)
        publish()
        return true
    }

    fun remove(item: BatchItem) {
        items.remove(item.key)
        lastSeenAt.remove(item.key)
        publish()
    }

    fun clear() {
        items.clear()
        lastSeenAt.clear()
        publish()
    }

    fun toCsv(): String = BatchCsv.build(items.values.toList())

    fun toText(): String = items.values.joinToString("\n") { item ->
        if (item.count > 1) "${item.content} ×${item.count}" else item.content
    }

    private fun publish() {
        val values = items.values.toList()
        _items.value = values.asReversed().toList()  // newest first on screen
        state[KEY_CONTENTS] = ArrayList(values.map { it.content })
        state[KEY_FORMATS] = ArrayList(values.map { it.format })
        state[KEY_COUNTS] = values.map { it.count }.toIntArray()
        state[KEY_TIMES] = values.map { it.lastScannedAt }.toLongArray()
    }

    companion object {
        const val REPEAT_GAP_MS = 1500L
        private const val KEY_CONTENTS = "contents"
        private const val KEY_FORMATS = "formats"
        private const val KEY_COUNTS = "counts"
        private const val KEY_TIMES = "times"
    }
}
