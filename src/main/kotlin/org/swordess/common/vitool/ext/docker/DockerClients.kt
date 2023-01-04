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
import kotlinx.coroutines.suspendCancellableCoroutine
import org.springframework.util.unit.DataSize
import org.swordess.common.vitool.ext.slf4j.CmdLogger
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


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
    CmdLogger.docker.info("Image tag created: $version")
    return version
}

suspend fun DockerClient.pushImage(version: ImageVersion): Unit = suspendCancellableCoroutine { continuation ->
    var processingStream: Closeable? = null
    val callback = object : ResultCallback.Adapter<PushResponseItem>() {

        override fun onStart(stream: Closeable) {
            super.onStart(stream)
            processingStream = stream
        }

        override fun onNext(item: PushResponseItem) {
            var msg: String? = null

            if (item.id != null) {
                if (item.progressDetail != null) {
                    val current = item.progressDetail!!.current?.let { DataSize.ofBytes(it) }
                    val total = item.progressDetail!!.total?.let { DataSize.ofBytes(it) }

                    if (current != null && total != null) {
                        val progress = if (current.toBytes() < total.toMegabytes()) {
                            "${current.toHumanizeString()}/${total.toHumanizeString()}"
                        } else current.toHumanizeString()
                        msg = "${item.status} ${item.id} $progress"
                    } else {
                        msg = "${item.status} ${item.id}"
                    }

                } else {
                    msg = "${item.status} ${item.id}"
                }

            } else if (item.errorDetail != null) {
                msg = item.errorDetail!!.message

            } else if (item.aux != null) {
                // the response right before this one matches the last branch of this if-else

            } else {
                msg = item.status
            }

            msg?.let { CmdLogger.docker.info(it) }
        }

        override fun onError(throwable: Throwable) {
            super.onError(throwable)
            continuation.resumeWithException(throwable)
        }

        override fun onComplete() {
            super.onComplete()
            continuation.resume(Unit)
        }

    }

    pushImageCmd(version.toString()).exec(callback)
    continuation.invokeOnCancellation {
        try {
            processingStream?.use {
                // intended to safely close the stream
            }
        } finally {
            CmdLogger.docker.info("Image push has been interrupted")
        }
    }
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
