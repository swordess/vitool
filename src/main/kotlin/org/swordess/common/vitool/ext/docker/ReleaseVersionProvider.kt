/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.docker


sealed interface ReleaseVersionProvider {
    fun getRegistryUrl(useVPC: Boolean): String
    fun getReleaseVersion(buildVersion: ImageVersion, useVPC: Boolean): ReleaseVersion
}

object AliyunReleaseVersionProvider : ReleaseVersionProvider {

    override fun getRegistryUrl(useVPC: Boolean): String = DockerRegistry.aliyun.url.toVPC()

    override fun getReleaseVersion(buildVersion: ImageVersion, useVPC: Boolean): ReleaseVersion {
        var specific = buildVersion
        var latest = buildVersion.copy(tag = "latest")

        if (useVPC) {
            specific = specific.copy(name = specific.name.toVPC())
            latest = latest.copy(name = latest.name.toVPC())
        }

        return ReleaseVersion(specific, latest)
    }

    private fun String.toVPC(): String = replace("registry", "registry-vpc")

}

object DockerHubReleaseVersionProvider : ReleaseVersionProvider {

    override fun getRegistryUrl(useVPC: Boolean): String = DockerRegistry.dockerhub.url

    override fun getReleaseVersion(buildVersion: ImageVersion, useVPC: Boolean): ReleaseVersion {
        val imageName = "${DockerRegistry.dockerhub.url}/xingyuli/vitool"
        val specific = buildVersion.copy(name = imageName)
        val latest = ImageVersion(name = imageName, tag = "latest")
        return ReleaseVersion(specific, latest)
    }

}

val DockerRegistry.releaseVersionProvider: ReleaseVersionProvider
    get() = when (this) {
        DockerRegistry.aliyun -> AliyunReleaseVersionProvider
        DockerRegistry.dockerhub -> DockerHubReleaseVersionProvider
    }
