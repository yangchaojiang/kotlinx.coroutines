package kotlinx.coroutines.debug.test

import com.sun.tools.attach.VirtualMachine
import org.junit.BeforeClass
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicInteger

open class TestBase {
    companion object {
        var LOG_LEVEL = "debug"
        private val AGENT_JAR_PATH = "../kotlinx-coroutines-debug-agent/build/libs/coroutines-debug-agent.jar"
        private val AGENT_ARGUMENTS = "loglevel=${LOG_LEVEL}"

        @BeforeClass
        @JvmStatic
        fun prepare() {
            loadAgent()
        }

        @JvmStatic
        private fun loadAgent(agentJarPath: String = AGENT_JAR_PATH, agentArguments: String = AGENT_ARGUMENTS) {
            val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().name
            val pid = nameOfRunningVM.substring(0, nameOfRunningVM.indexOf('@'))
            val vm = VirtualMachine.attach(pid)
            vm.loadAgent(agentJarPath, agentArguments)
            vm.detach()
        }
    }
}