/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.storage

import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.model.GetObjectRequest
import com.aliyun.oss.model.PutObjectRequest
import java.io.Closeable
import java.io.File

interface ReadableData<V> {
    fun read(callback: ((V) -> Unit)? = null): ByteArray
}

interface WriteableData<V> {
    fun write(data: ByteArray, callback: ((V) -> Unit)? = null)
}

object ConsoleData : WriteableData<Unit> {

    override fun write(data: ByteArray, callback: ((Unit) -> Unit)?) {
        println(String(data))
    }

}

class FileData(private val path: String) : ReadableData<String>, WriteableData<String> {

    override fun read(callback: ((String) -> Unit)?): ByteArray =
        with(File(path)) {
            val result = readBytes()
            callback?.invoke(absolutePath)
            result
        }

    override fun write(data: ByteArray, callback: ((String) -> Unit)?) {
        with(File(path)) {
            writeBytes(data)
            callback?.invoke(absolutePath)
        }
    }

}

// url of the uploaded file will be:
//   {protocol} :// {bucket} . {endpoint}                  / {filepath}
// e.g.,
//   https      :// foo      . oss-cn-beijing.aliyuncs.com / bar/baz.json
data class OssFileProperties(
    val protocol: String,
    val bucket: String,
    val endpoint: String,
    val path: String,

    val accessId: String?,
    val accessSecret: String?
)

class OssData(private val config: OssFileProperties) : ReadableData<String>, WriteableData<String> {

    private val path: String
        get() = with(config) { "$protocol://$bucket.$endpoint/$path" }

    override fun read(callback: ((String) -> Unit)?): ByteArray {
        return Client().use { client ->
            val req = GetObjectRequest(config.bucket, config.path)
            val result = client.getObject(req).objectContent.use { it.readBytes() }
            callback?.invoke(path)
            result
        }
    }

    override fun write(data: ByteArray, callback: ((String) -> Unit)?) {
        Client().use {
            val req = PutObjectRequest(config.bucket, config.path, data.inputStream())
            it.putObject(req)
            callback?.invoke(path)
        }
    }

    private inner class Client(private val oss: OSS = ossClient()) : OSS by oss, Closeable {
        override fun close() {
            oss.shutdown()
        }
    }

    private fun ossClient(): OSS {
        checkNotNull(config.accessId) { "accessId is missing" }
        checkNotNull(config.accessSecret) { "accessSecret is missing" }
        return OSSClientBuilder()
            .build("${config.protocol}://${config.endpoint}", config.accessId, config.accessSecret)
    }

}
