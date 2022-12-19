/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.storage

import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.model.GetObjectRequest
import com.aliyun.oss.model.PutObjectRequest
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
    var protocol: String,
    var bucket: String,
    var endpoint: String,
    var path: String,

    var accessId: String?,
    var accessSecret: String?
)

class OssData(private val config: OssFileProperties) : ReadableData<String>, WriteableData<String> {

    private val path: String
        get() = with(config) { "$protocol://$bucket.$endpoint/$path" }

    override fun read(callback: ((String) -> Unit)?): ByteArray {
        val req = GetObjectRequest(config.bucket, config.path)

        val client = OSSClientBuilder().build("${config.protocol}://${config.endpoint}", config.accessId, config.accessSecret)

        val result = client.getObject(req).objectContent.use { it.readBytes() }
        client.shutdown()

        callback?.invoke(path)

        return result
    }

    override fun write(data: ByteArray, callback: ((String) -> Unit)?) {
        val req = PutObjectRequest(config.bucket, config.path, data.inputStream())

        val client = OSSClientBuilder().build("${config.protocol}://${config.endpoint}", config.accessId, config.accessSecret)
        client.putObject(req)
        client.shutdown()

        callback?.invoke(path)
    }

}
