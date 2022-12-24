/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.docker

import com.google.cloud.tools.jib.api.JavaContainerBuilder
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType
import com.google.cloud.tools.jib.api.JibContainerBuilder
import org.swordess.common.vitool.ext.slf4j.CmdLogger
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Predicate


data class Artifact(
    val groupId: String,
    val artifactId: String,
    val versionId: String,
    val scope: String,
    val classifier: String,
    val path: String
) {
    val snapshot: Boolean = "SNAPSHOT" in versionId
}

class Project(private val buildDirectory: String, val mainClass: String) {

    private val classesDirectory: Path = Paths.get(buildDirectory, "classes")

    val jibCacheDirectory: Path
        get() = Paths.get(buildDirectory, CACHE_DIRECTORY_NAME)

    private val dependencyListFile: File
        get() = File(buildDirectory, "vitool${File.separator}dependency-list.txt")

    fun createJibContainerBuilder(javaContainerBuilder: JavaContainerBuilder): JibContainerBuilder {
        // Add resources, and classes
        // Don't use Path.endsWith(), since Path works on path elements.
        val isClassFile = Predicate<Path> { path -> path.fileName.toString().endsWith(".class") }
        javaContainerBuilder
            .addResources(classesDirectory, isClassFile.negate())
            .addClasses(classesDirectory, isClassFile)

        // Classify and add dependencies
        val classifiedDependencies: Map<LayerType, List<Path>> = classifyDependencies(parseArtifacts())
        javaContainerBuilder.addDependencies(
            classifiedDependencies[LayerType.DEPENDENCIES]
        )
        javaContainerBuilder.addSnapshotDependencies(
            classifiedDependencies[LayerType.SNAPSHOT_DEPENDENCIES]
        )

        return javaContainerBuilder.toContainerBuilder()
    }

    private fun parseArtifacts(): Set<Artifact> {
        var lines = dependencyListFile.readLines().takeIf { it.size > 2 }
            ?: throw RuntimeException("the file content of `dependency-list.txt` seems not okay :(")

        // skip top 2 lines
        lines = lines.slice(2 until lines.size)

        lines = lines.filter { it.isNotBlank() }.map { it.trim() }

        val result = mutableSetOf<Artifact>()

        lines.forEach { line ->
            // org.jetbrains.kotlin:kotlin-stdlib-jdk8:jar:1.7.20:compile:/Users/viclau/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.7.20/kotlin-stdlib-jdk8-1.7.20.jar
            val tokens = line.split(':')

            if (tokens.size == 6) {
                val groupId = tokens[0]
                val artifactId = tokens[1]
                val classifier = tokens[2]
                val versionId = tokens[3]
                val scope = tokens[4]
                val path = tokens[5]
                result.add(Artifact(groupId, artifactId, versionId, scope, classifier, path))

            } else {
                CmdLogger.jib.warn("unrecognized dependency line: {}", line)
            }
        }

        return result
    }

    private fun classifyDependencies(dependencies: Set<Artifact>): Map<LayerType, List<Path>> {
        val classifiedDependencies: MutableMap<LayerType, MutableList<Path>> = mutableMapOf()
        classifiedDependencies[LayerType.DEPENDENCIES] = mutableListOf()
        classifiedDependencies[LayerType.SNAPSHOT_DEPENDENCIES] = mutableListOf()
        for (artifact in dependencies) {
            if (artifact.snapshot) {
                classifiedDependencies[LayerType.SNAPSHOT_DEPENDENCIES]!!.add(File(artifact.path).toPath())
            } else {
                classifiedDependencies[LayerType.DEPENDENCIES]!!.add(File(artifact.path).toPath())
            }
        }
        return classifiedDependencies
    }

    companion object {

        /** Directory name for the cache. The directory will be relative to the build output directory.  */
        private const val CACHE_DIRECTORY_NAME = "jib-cache"

    }

}
