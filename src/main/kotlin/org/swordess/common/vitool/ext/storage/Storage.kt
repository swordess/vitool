/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.storage

import java.io.File

interface ReadableStorage {
    fun read(): ByteArray
}

interface WriteableStorage<V> {
    fun write(data: ByteArray, callback: ((V) -> Unit)? = null)
}

object ConsoleStorage : WriteableStorage<Unit> {

    override fun write(data: ByteArray, callback: ((Unit) -> Unit)?) {
        println(String(data))
    }

}

class FileStorage(private val path: String) : ReadableStorage, WriteableStorage<String> {

    override fun read(): ByteArray = File(path).readBytes()

    override fun write(data: ByteArray, callback: ((String) -> Unit)?) {
        with(File(path)) {
            writeBytes(data)
            callback?.invoke(absolutePath)
        }
    }

}