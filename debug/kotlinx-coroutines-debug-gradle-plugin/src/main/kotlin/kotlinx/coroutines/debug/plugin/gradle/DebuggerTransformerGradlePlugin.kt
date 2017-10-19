package kotlinx.coroutines.debug.plugin.gradle

import kotlinx.coroutines.debug.transformer.FilesTransformer
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

open class DebuggerTransformerGradlePlugin : Plugin<Project> {
    private val TASK_NAME = "debuggerTransform"
    override fun apply(target: Project) {
        target.tasks.create(TASK_NAME, DebuggerTransformTask::class.java)
    }

}

open class DebuggerTransformTask : DefaultTask() {

    @InputFiles
    lateinit var inputFiles: FileCollection
    @OutputDirectory
    lateinit var outputDir: File

    @TaskAction
    fun transform() {
        inputFiles.forEach {
            FilesTransformer(it, outputDir).transform()
        }
    }
}