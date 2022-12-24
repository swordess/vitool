/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.transport.DockerHttpClient
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import org.springframework.util.unit.DataSize
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


data class ImageVersion(val name: String, val tag: String) {
    override fun toString(): String = "$name:$tag"
}

fun DockerClient(registryUrl: String, registryUsername: String, registryPassword: String): DockerClient {
    val config: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost("unix:///var/run/docker.sock")
        .withApiVersion("1.23")
        .withRegistryUrl(registryUrl)
        .withRegistryUsername(registryUsername)
        .withRegistryPassword(registryPassword)
        .build()

    val httpClient: DockerHttpClient = ZerodepDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .build()

    return DockerClientImpl.getInstance(config, httpClient)
}

fun DockerClient.getImage(version: ImageVersion): Image? =
    listImagesCmd().withImageNameFilter(version.name)
        .exec()
        .firstOrNull { "${version.name}:${version.tag}" in (it.repoTags ?: emptyArray()) }

fun DockerClient.tagImage(id: String, version: ImageVersion): ImageVersion {
    tagImageCmd(id, version.name, version.tag).exec()
    println("Image tag created: $version")
    return version
}

fun DockerClient.pushImage(version: ImageVersion) {
    var started = false
    var finished = false

    pushImageCmd(version.toString()).exec(object : ResultCallback.Adapter<PushResponseItem>() {

        override fun onStart(stream: Closeable) {
            super.onStart(stream)
            started = true
        }

        override fun onNext(item: PushResponseItem) {
            if (item.id != null) {
                if (item.progressDetail != null) {
                    val current = item.progressDetail?.current?.let { DataSize.ofBytes(it) }
                    val total = item.progressDetail?.total?.let { DataSize.ofBytes(it) }

                    if (current != null && total != null) {
                        val progress = if (current.toBytes() < total.toMegabytes()) {
                            "${current.toHumanizeString()}/${total.toHumanizeString()}"
                        } else current.toHumanizeString()
                        println("${item.status} ${item.id} $progress")
                    } else {
                        println("${item.status} ${item.id}")
                    }

                } else {
                    println("${item.status} ${item.id}")
                }

            } else if (item.errorDetail != null) {
                println(item.errorDetail?.message)

            } else if (item.aux != null) {
                // the response right before this one matches the last branch of this if-else

            } else {
                println(item.status)
            }
        }

        override fun onComplete() {
            super.onComplete()
            finished = true
        }

    })

    thread {
        try {
            while (!started) {
                TimeUnit.SECONDS.sleep(1)
                if (!started) {
                    println("Waiting for start ...")
                }
            }
            while (!finished) {
                TimeUnit.SECONDS.sleep(1)
            }
        } catch (e: InterruptedException) {
            println("Image push has been interrupted")
        }
    }.join()
}

private fun DataSize.toHumanizeString(): String {
    if (toBytes() < 1024) {
        return "${toBytes()}B"
    }

    val inKb: Double = toBytes() / 1024.0
    if (inKb < 1024) {
        return "%.2f".format(inKb) + "KB"
    }

    val inMb: Double = inKb / 1024
    if (inMb < 1024) {
        return "%.2f".format(inMb) + "MB"
    }

    val inGb: Double = inMb / 1024
    return "%.2f".format(inGb) + "GB"
}
