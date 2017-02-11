package kotlinx.coroutines.experimental.internal

import com.devexperts.dxlab.lincheck.LinChecker
import com.devexperts.dxlab.lincheck.annotations.CTest
import com.devexperts.dxlab.lincheck.annotations.Operation
import com.devexperts.dxlab.lincheck.annotations.Param
import com.devexperts.dxlab.lincheck.annotations.Reset
import com.devexperts.dxlab.lincheck.generators.IntGen
import org.junit.Test

@CTest(iterations = 10_000, invocationsPerIteration = 10_000, actorsPerThread = arrayOf("1:3", "1:3", "1:3", "1:3"))
@Param(name = "x", gen = IntGen::class, conf = "1:3")
class LockFreeListLinCheck {
    var list = LockFreeLinkedListHead()
    data class IntNode(val x: Int) : LockFreeLinkedListNode()

    @Reset
    fun reset() {
        list = LockFreeLinkedListHead()
    }

    @Operation
    fun addFirst(@Param(name = "x") x: Int) {
        list.addFirst(IntNode(x))
    }

    @Operation
    fun addFirstIfEmpty(@Param(name = "x") x: Int) {
        list.addFirstIfEmpty(IntNode(x))
    }

    @Operation
    fun addLast(@Param(name = "x") x: Int) {
        list.addLast(IntNode(x))
    }

    @Operation
    fun addLastIfPrevDiff(@Param(name = "x") x: Int) {
        list.addLastIfPrev(IntNode(x)) { it !is IntNode || it.x != x }
    }

    @Operation
    fun removeFirst() : LockFreeLinkedListNode? {
        return list.removeFirstOrNull()
    }

    @Operation
    fun removeFirstOrPeekIf(@Param(name = "x") x: Int) : LockFreeLinkedListNode? {
        return list.removeFirstIfIsInstanceOfOrPeekIf<IntNode> { it.x == x }
    }

    @Test
    fun test() {
        LinChecker.check(this)
    }
}