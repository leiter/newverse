package com.together.newverse.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Cancellation is not a failure, and `catch (e: Exception)` cannot tell the two apart.
 *
 * `CancellationException` *is* an `Exception`, so the broad catch every repository
 * method here is wrapped in takes it along with everything else. What follows the
 * catch is error handling: a line at `Log.e`, a `Result.failure`, a fallback value
 * emitted or returned. None of that is true of a coroutine that was asked to stop,
 * and two of the three are actively harmful:
 *
 *  - **A cancelled call reports a failure.** This is the one with a visible
 *    consequence, and it is the shape nearly every method here is written in:
 *    `catch (e: Exception) { Log.e(...); Result.failure(e) }`. Nothing downstream can
 *    tell that from the database being unreachable, so a caller that was merely
 *    stopped shows the user an error.
 *  - **Swallowing the cancellation breaks structured concurrency.** The exception is
 *    how the cancellation reaches the machinery that asked for it; caught and
 *    discarded, the coroutine carries on past the point it was supposed to stop.
 *  - **The error path runs for a navigation.** A `Log.e` line per screen change, which
 *    is how this was found on 2026-11-16 -- three `E/OrderRepo: observeSellerOrders:
 *    Error - Job was cancelled` lines in a release capture.
 *
 * What it does *not* do, measured rather than assumed on 2026-11-16: publish the
 * fallback value. A `catch` in a flow body that ends in `emit(fallback)` does not
 * reach the collector -- emitting into a cancelled collector throws on its own -- so
 * the wrong value is attempted and dropped. `CancellationTest` pins both halves.
 *
 * So every broad catch has to let this one exception past. These two make that the
 * short thing to write rather than four lines of discipline per site.
 */

/**
 * Rethrow if this is a cancellation, otherwise return and let the caller handle it.
 *
 * The first line of a broad catch:
 *
 * ```
 * } catch (e: Exception) {
 *     e.rethrowIfCancellation()
 *     Log.e(TAG) { "saveProfile: ${e.message}" }
 *     Result.failure(e)
 * }
 * ```
 *
 * Harmless in a `try` that cannot suspend — there is no cancellation to catch there —
 * which is why it can be applied across a file without judging each site.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/**
 * [runCatching], minus the one exception it should never have caught.
 *
 * For a new call site that wants a [Result]; the existing `try`/`catch` blocks keep
 * their shape and use [rethrowIfCancellation] instead, since most of them also log.
 */
inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
