package io.vertx.kotlin.core.coroutines.experimental

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import kotlin.coroutines.experimental.*

/**
 * Create a coroutine and launch on Vert.x context
 *
 * @return a future for coroutine
 */
fun <T> Vertx.launch(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): Future<T> {
    val future = Future.future<T>()
    val futureContinuation = FutureContinuation<T>(future, context)
    runOnContext {
        block.startCoroutine(completion = futureContinuation)
    }
    return future
}

/**
 * Register an `handler` for the Future and await termination
 */
suspend fun <T> Future<T>.await() =
        suspendCoroutine { cont: Continuation<T> ->
            this.setHandler { asyncResult ->
                if (asyncResult.succeeded())
                    cont.resume(asyncResult.result())
                else
                    cont.resumeWithException(asyncResult.cause())
            }
        }

/**
 * Await for [Handler] and return result
 */
suspend fun <T> handle(block: (Handler<T>) -> Unit): T =
        suspendCoroutine { cont: Continuation<T> ->
            // handler calls `resume`
            val handler = Handler<T> { cont.resume(it) }
            try {
                block(handler)
            } catch(exception: Throwable) {
                cont.resumeWithException(exception)
            }
        }

/**
 * Await for Handler<[AsyncResult]> and return async result
 */
suspend fun <T> handleResult(block: (Handler<AsyncResult<T>>) -> Unit): T {
    val asyncResult = handle<AsyncResult<T>> { block(it) }
    if (asyncResult.succeeded())
        return asyncResult.result()
    else
        throw asyncResult.cause()
}

/**
 * Vert.x Future adapter for Kotlin coroutine continuation
 */
private class FutureContinuation<T>(private val future: Future<T>,
                                    override val context: CoroutineContext) : Continuation<T> {

    override fun resume(value: T) = future.complete(value)

    override fun resumeWithException(exception: Throwable) = future.fail(exception)
}
