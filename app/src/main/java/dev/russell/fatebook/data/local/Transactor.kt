package dev.russell.fatebook.data.local

/**
 * Run a block as a single Room transaction. Subscribers to Flow-based queries
 * see exactly one re-emission after the block commits, regardless of how many
 * tables the block touches.
 *
 * Abstracted so tests can substitute a no-op implementation that simply runs
 * the block.
 */
fun interface Transactor {
    suspend fun transact(block: suspend () -> Unit)
}
