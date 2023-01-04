/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.docker

import com.google.cloud.tools.jib.api.*
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import com.google.cloud.tools.jib.api.buildplan.Platform
import com.google.cloud.tools.jib.event.events.ProgressEvent
import com.google.cloud.tools.jib.event.events.TimerEvent
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.swordess.common.vitool.ext.slf4j.CmdLogger
import org.swordess.common.vitool.ported.jib.HelpfulSuggestions
import org.swordess.common.vitool.ported.jib.JibBuildRunner
import org.swordess.common.vitool.ported.jib.TimerEventHandler
import org.swordess.common.vitool.ported.jib.logging.ConsoleLoggerBuilder
import org.swordess.common.vitool.ported.jib.logging.ProgressDisplayGenerator
import org.swordess.common.vitool.ported.jib.logging.SingleThreadedExecutor
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.div


sealed class ProjectReleaser<T>(protected val config: T) {
    abstract fun run()
}

class DockerReleaser(config: Config) : ProjectReleaser<DockerReleaser.Config>(config) {

    data class Config(
        val registryUrl: String,
        val registryUsername: String,
        val registryPassword: String,
        val buildVersion: ImageVersion,
        val releaseVersion: ReleaseVersion
    )

    override fun run() {
        DockerClient(config.registryUrl, config.registryUsername, config.registryPassword).use { client ->
            val image = client.getImage(config.buildVersion)

            if (image != null) {
                val releaseVersion = config.releaseVersion

                runBlocking {
                    client.pushImage(releaseVersion.specific.also {
                        if (it != config.buildVersion) {
                            client.tagImage(image.id, it)
                        }
                    })

                    client.pushImage(client.tagImage(image.id, releaseVersion.latest))
                }
            } else {
                CmdLogger.docker.info("Image not found: {}", config.buildVersion)
            }
        }
    }

}

private object AppRoot {
    private val path: AbsoluteUnixPath = AbsoluteUnixPath.get(JavaContainerBuilder.DEFAULT_APP_ROOT)
    fun resolve(relativeUnixPath: String): AbsoluteUnixPath = path.resolve(relativeUnixPath)
}

class JibReleaser(config: Config, private val project: Project) : ProjectReleaser<JibReleaser.Config>(config), Closeable {

    data class Config(
        val registryUsername: String,
        val registryPassword: String,
        val toImage: ImageVersion,
        val additionalTags: Set<String>
    )

    private inline val logger: Logger
        get() = CmdLogger.jib

    private val singleThreadedExecutor by lazy { SingleThreadedExecutor() }

    private val consoleLogger by lazy {
        val builder = ConsoleLoggerBuilder.rich(singleThreadedExecutor, true)
        // val builder = ConsoleLoggerBuilder.plain(singleThreadedExecutor).progress(CmdLogger.jib::info)

        builder
            .lifecycle(logger::info)
            .warn(logger::warn)
            .build()
    }

    override fun run() {
        val targetImageReference = ImageReference.parse(config.toImage.toString())
        val targetImage = RegistryImage.named(targetImageReference)

        targetImage.addCredential(config.registryUsername, config.registryPassword)

        val containerizer = Containerizer.to(targetImage).setAlwaysCacheBaseImage(false)
        config.additionalTags.forEach {
            containerizer.withAdditionalTag(it)
        }

        configureEventHandlers(containerizer)

        // Create and configure JibContainerBuilder
        val javaContainerBuilder = JavaContainerBuilder.from(ImageReference.parse(FROM_IMAGE))
        val jibContainerBuilder = project.createJibContainerBuilder(javaContainerBuilder)
        jibContainerBuilder
            .setPlatforms(setOf(Platform("amd64", "linux"), Platform("arm64", "linux")))
            .setEntrypoint(computeEntrypoint(jibContainerBuilder))

        JibBuildRunner.forBuildImage(
            jibContainerBuilder,
            containerizer,
            ::log,
            HelpfulSuggestions(HELPFUL_SUGGESTIONS_PREFIX),
            targetImageReference,
            config.additionalTags
        ).runBuild()
    }

    override fun close() {
        singleThreadedExecutor.shutDownAndAwaitTermination(Duration.ofSeconds(5))
    }

    private fun configureEventHandlers(containerizer: Containerizer) {
        containerizer.addEventHandler(LogEvent::class.java, ::log)
            .addEventHandler(
                TimerEvent::class.java, TimerEventHandler { message -> log(LogEvent.debug(message)) })
            .addEventHandler(
                ProgressEvent::class.java,
                ProgressEventHandler { update: ProgressEventHandler.Update ->
                    consoleLogger.setFooter(
                        ProgressDisplayGenerator.generateProgressDisplay(
                            update.progress, update.unfinishedLeafTasks
                        )
                    )
                })
    }

    private fun computeEntrypoint(jibContainerBuilder: JibContainerBuilder): List<String> {
        val classpath = listOf(
            AppRoot.resolve("resources"),
            AppRoot.resolve("classes"),
            AppRoot.resolve("libs/*")
        )
        val classpathString = classpath.joinToString(":")

        addJvmArgFilesLayer(jibContainerBuilder, classpathString)

        return buildList {
            add("java")
            // addAll(jvmFlags)
            add("-cp")
            add(classpathString)
            add(project.mainClass)
        }
    }

    private fun addJvmArgFilesLayer(jibContainerBuilder: JibContainerBuilder, classpath: String) {
        val classpathFile: Path = project.jibCacheDirectory / JIB_CLASSPATH_FILE
        val mainClassFile: Path = project.jibCacheDirectory / JIB_MAIN_CLASS_FILE

        // It's perfectly fine to always generate a new temp file or rewrite an existing file. However,
        // fixing the source file path and preserving the file timestamp prevents polluting the Jib
        // layer cache space by not creating new cache selectors every time. (Note, however, creating
        // new selectors does not affect correctness at all.)
        writeFileConservatively(classpathFile, classpath)
        writeFileConservatively(mainClassFile, project.mainClass)

        jibContainerBuilder.addFileEntriesLayer(
            FileEntriesLayer.builder()
                .setName(JavaContainerBuilder.LayerType.JVM_ARG_FILES.getName())
                .addEntry(classpathFile, AppRoot.resolve(JIB_CLASSPATH_FILE))
                .addEntry(mainClassFile, AppRoot.resolve(JIB_MAIN_CLASS_FILE))
                .build()
        )
    }

    /**
     * Writes a file only when needed (when the file does not exist or the existing file has a
     * different content). It reads the entire bytes into a `String` for content comparison, so
     * care should be taken when using this method for a huge file.
     *
     * @param file target file to write
     * @param content file content to write
     */
    private fun writeFileConservatively(file: Path, content: String) {
        if (Files.exists(file)) {
            val oldContent = String(Files.readAllBytes(file), Charsets.UTF_8)
            if (oldContent == content) {
                return
            }
        }
        Files.createDirectories(file.parent)
        Files.write(file, content.toByteArray(Charsets.UTF_8))
    }

    private fun log(event: LogEvent) {
        consoleLogger.log(event.level, event.message)
    }

    companion object {
        private const val FROM_IMAGE = "eclipse-temurin:8-jre"

        /* ***** respect jib settings ***** */

        private const val HELPFUL_SUGGESTIONS_PREFIX = "Build image failed"

        private const val JIB_CLASSPATH_FILE = "jib-classpath-file"
        private const val JIB_MAIN_CLASS_FILE = "jib-main-class-file"
    }

}