/*
 * Copyright 2016-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlinx.coroutines.experimental

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.internal.unwrap
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Abstract class for coroutines.
 *
 *  * Coroutines implement completion [Continuation], [Job], and [CoroutineScope] interfaces.
 *  * Coroutine stores the result of continuation in the state of the job.
 *  * Coroutine waits for children coroutines to finish before completing.
 *  * Coroutines are cancelled through an intermediate _cancelling_ state.
 *
 * @param active when `true` coroutine is created in _active_ state, when `false` in _new_ state. See [Job] for details.
 * @suppress **This is unstable API and it is subject to change.**
 */
public abstract class AbstractCoroutine<in T>(
    private val parentContext: CoroutineContext,
    active: Boolean
) : JobSupport(active), Continuation<T>, CoroutineScope {
    @Suppress("LeakingThis")
    public final override val context: CoroutineContext = parentContext + this
    public final override val coroutineContext: CoroutineContext get() = context

    // all coroutines are cancelled through an intermediate cancelling state
    final override val hasCancellingState: Boolean get() = true

    override fun createParentCancellationHandler(parent: Job): JobCancellationNode<*> =
        if (parent is AbstractCoroutine<*>) Child(parent, this)
            else super.createParentCancellationHandler(parent)

    final override fun resume(value: T) =
        resumeImpl(value, null)

    final override fun resumeWithException(exception: Throwable) =
        resumeImpl(CompletedExceptionally(exception), null)

    internal fun resumeImpl(proposedUpdate: Any?, lastNode: LockFreeLinkedListNode?) {
        loopOnState { state ->
            if (state !is Incomplete) error("Coroutine $this is already complete, but got resumed with $proposedUpdate")
            // figure out if we need to wait for a child
            val nextNode: LockFreeLinkedListNode? //
            val waitChild: Child?
            if (lastNode != null) {
                nextNode = nextChildOrLast(lastNode)
                waitChild = nextNode as? Child
            } else when (state) {
                is NodeList -> {
                    nextNode = nextChildOrLast(state)
                    waitChild = nextNode as? Child
                }
                is Cancelling -> {
                    nextNode = nextChildOrLast(state.list)
                    waitChild = nextNode as? Child
                }
                is Child -> {
                    nextNode = null
                    waitChild = state
                }
                else -> {
                    nextNode = null
                    waitChild = null
                }
            }
            // wait for child if needed
            if (waitChild != null) {
                val child = waitChild.child
                child.invokeOnCompletion(ChildCompletion(this, child, proposedUpdate, nextNode))
                return
            }
            // no more children to wait --> try update state
            if (updateState(state, proposedUpdate, MODE_ATOMIC_DEFAULT)) return
        }
    }

    private fun nextChildOrLast(start: LockFreeLinkedListNode): LockFreeLinkedListNode? {
        var last = start // keep track of last non-removed node found
        while (last.isRemoved) last = last.prev.unwrap() // rollback to prev non-removed (or list head)
        var cur = last // now go forward
        while (true) {
            cur = cur.next.unwrap()
            if (cur.isRemoved) continue
            if (cur is Child) return cur
            if (cur is NodeList) return last // checked all -- return last non-removed
            last = cur
        }
    }

    override fun cancelChildren(cause: Throwable?) {
        val state = this.state
        when (state) {
            is NodeList -> cancelChildrenList(state, cause)
            is Cancelling -> cancelChildrenList(state.list, cause)
            is Child -> state.child.cancel(cause)
        }
    }

    private fun cancelChildrenList(list: NodeList, cause: Throwable?) {
        list.forEach<Child> { it.child.cancel(cause) }
    }

    final override fun handleException(exception: Throwable) {
        handleCoroutineException(parentContext, exception)
    }

    override fun nameString(): String {
        val coroutineName = context.coroutineName ?: return super.nameString()
        return "\"$coroutineName\":${super.nameString()}"
    }
}

private class Child(
    parent: AbstractCoroutine<*>,
    val child: AbstractCoroutine<*>
) : JobCancellationNode<AbstractCoroutine<*>>(parent) {
    override fun invokeOnce(reason: Throwable?) { child.onParentCancellation(job) }
    override fun toString(): String = "Child[$child]"
}

private class ChildCompletion(
    private val parent: AbstractCoroutine<*>,
    child: AbstractCoroutine<*>,
    private val proposedUpdate: Any?,
    private val nextNode: LockFreeLinkedListNode?
) : JobNode<AbstractCoroutine<*>>(child) {
    override fun invoke(reason: Throwable?) {
        parent.resumeImpl(proposedUpdate, nextNode)
    }
}