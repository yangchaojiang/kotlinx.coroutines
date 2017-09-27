package kotlinx.coroutines.debug.manager

import java.io.File

interface StupidSerializer<T> {
    fun read(string: String): T
    fun write(obj: T): String
}

fun <T> File.readAll(reader: StupidSerializer<T>) = readLines().map { reader.read(it.trim(' ', '\n')) }

fun <T> File.writeAll(writer: StupidSerializer<T>, objects: List<T>)
        = writeText(objects.joinToString("\n") { writer.write(it) })

data class MethodId(val name: String, val className: String, val desc: String) {
    init {
        check(!className.contains('/')) { "Must be a Java class name, not an internal name" }
    }

    override fun toString() = "$className.$name $desc"
    fun equalsTo(ste: StackTraceElement) = name == ste.methodName && className == ste.className

    companion object : StupidSerializer<MethodId> {
        override fun read(string: String) = string.split(',').let { MethodId(it[0], it[1], it[2]) }
        override fun write(obj: MethodId) = "${obj.name},${obj.className},${obj.desc}"
        fun build(name: String, owner: String, desc: String) = MethodId(name, owner.replace('/', '.'), desc)
        val UNKNOWN = MethodId("unknown", "unknown", "unknown")
    }
}

data class CallPosition(val file: String, val line: Int) {
    override fun toString() = "at $file:$line"

    companion object : StupidSerializer<CallPosition> {
        override fun read(string: String) = string.split(',').let { CallPosition(it[0], it[1].toInt()) }
        override fun write(obj: CallPosition) = "${obj.file},${obj.line}"
        val UNKNOWN = CallPosition("unknown", -1)
    }
}

sealed class MethodCall {
    abstract val method: MethodId
    abstract val fromMethod: MethodId
    abstract val position: CallPosition
    val stackTraceElement
            by lazy { StackTraceElement(fromMethod.className, fromMethod.name, position.file, position.line) }

    override fun toString() = "${method.name} from $fromMethod $position"
}

data class DoResumeCall(
        override val method: MethodId,
        override val fromMethod: MethodId,
        override val position: CallPosition
) : MethodCall() {
    override fun toString() = super.toString()

    companion object : StupidSerializer<DoResumeCall> {
        override fun read(string: String) = string.split('\t').let {
            DoResumeCall(MethodId.read(it[0]), MethodId.read(it[1]), CallPosition.read(it[2]))
        }

        override fun write(obj: DoResumeCall)
                = "${MethodId.write(obj.method)}\t${MethodId.write(obj.fromMethod)}\t${CallPosition.write(obj.position)}"
    }
}

data class SuspendCall(
        override val method: MethodId,
        override val fromMethod: MethodId,
        override val position: CallPosition
) : MethodCall() {
    override fun toString() = super.toString()

    companion object : StupidSerializer<SuspendCall> {
        override fun read(string: String) = string.split('\t').let {
            SuspendCall(MethodId.read(it[0]), MethodId.read(it[1]), CallPosition.read(it[2]))
        }

        override fun write(obj: SuspendCall)
                = "${MethodId.write(obj.method)}\t${MethodId.write(obj.fromMethod)}\t${CallPosition.write(obj.position)}"
    }
}