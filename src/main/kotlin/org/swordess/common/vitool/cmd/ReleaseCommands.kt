/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.cmd

import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.shell.command.CommandRegistration
import org.springframework.shell.standard.EnumValueProvider
import org.springframework.shell.standard.ShellComponent
import org.swordess.common.vitool.Application
import org.swordess.common.vitool.ext.docker.*
import org.swordess.common.vitool.ext.shell.AbstractShellComponent
import javax.annotation.Resource

private const val COMMAND_GROUP = "Release Commands"

@ShellComponent
class ReleaseCommands : AbstractShellComponent() {

    @Resource
    private lateinit var buildProperties: BuildProperties

    private val imageNameWithRepository = "${DockerRegistry.aliyun.url}/viclau/vitool"

    private fun releaseSingle(to: DockerRegistry, username: String, password: String?, useVPC: Boolean) {
        if (System.getProperty("os.name") == "Mac OS X") {
            throw RuntimeException("Multi-platform images need extra work, run this command on Linux.")
        }

        val passwordOption = password.toOption()
            .orInput("Enter registry password:")
            .must("`registryPassword` cannot be inferred")

        val rvp = to.releaseVersionProvider
        val buildVersion = ImageVersion(imageNameWithRepository, buildProperties.version)

        DockerReleaser(
            DockerReleaser.Config(
                registryUrl = rvp.getRegistryUrl(useVPC),
                registryUsername = username,
                registryPassword = passwordOption,
                buildVersion = buildVersion,
                releaseVersion = rvp.getReleaseVersion(buildVersion, useVPC)
            )
        ).run()
    }

    private fun releaseMulti(buildDir: String, to: DockerRegistry, username: String, password: String?, useVPC: Boolean) {
        val passwordOption = password.toOption()
            .orInput("Enter registry password:")
            .must("`registryPassword` cannot be inferred")

        val rvp = to.releaseVersionProvider
        val buildVersion = ImageVersion(imageNameWithRepository, buildProperties.version)
        val releaseVersion = rvp.getReleaseVersion(buildVersion, useVPC)

        JibReleaser(
            JibReleaser.Config(
                registryUsername = username,
                registryPassword = passwordOption,
                toImage = releaseVersion.specific,
                additionalTags = setOf(releaseVersion.latest.tag)
            ),
            Project(
                buildDirectory = buildDir,
                mainClass = Application::class.qualifiedName!!
            )
        ).use {
            it.run()
        }
    }

    // @Bean
    fun releaseSingleRegistration(): CommandRegistration {
        return CommandRegistration.builder()
            .group(COMMAND_GROUP)
            .command("release single")
            .description("release with current arch to docker registry")
            .withOption()
                .longNames("to")
                .description("name of the registry. Possible values are: ${enumValues<DockerRegistry>().joinToString(", ")}")
                .required()
                .type(DockerRegistry::class.java)
                .completion(EnumValueProvider()::complete)
                .and()
            .withOption()
                .longNames("username")
                .shortNames('u')
                .required()
                .and()
            .withOption()
                .longNames("password")
                .shortNames('p')
                .and()
            .withOption()
                .longNames("useVPC")
                .description("push via VPC network")
                .defaultValue("false")
                .type(Boolean::class.java)
                .and()
            .withTarget()
                .method(this, "releaseSingle")
                .and()
            .build()
    }

    @Bean
    fun releaseMultiRegistration(): CommandRegistration {
        return CommandRegistration.builder()
            .group(COMMAND_GROUP)
            .command("release multi")
            .description("release multi-arch to docker registry")
            .withOption()
                .longNames("buildDir")
                .description("build directory of this project")
                .defaultValue("./target")
                .and()
            .withOption()
                .longNames("to")
                .description("name of the registry. Possible values are: ${enumValues<DockerRegistry>().joinToString(", ")}")
                .required()
                .type(DockerRegistry::class.java)
                .completion(EnumValueProvider()::complete)
                .and()
            .withOption()
                .longNames("username")
                .shortNames('u')
                .required()
                .and()
            .withOption()
                .longNames("password")
                .shortNames('p')
                .and()
            .withOption()
                .longNames("useVPC")
                .description("push via VPC network")
                .defaultValue("false")
                .type(Boolean::class.java)
                .and()
            .withTarget()
                .method(this, "releaseMulti")
                .and()
            .build()
    }

}