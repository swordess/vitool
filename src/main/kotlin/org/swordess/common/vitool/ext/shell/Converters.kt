/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.shell

import org.swordess.common.vitool.ext.storage.OssFileProperties
import java.lang.IllegalArgumentException


inline fun <reified T : Enum<T>> String.toEnums(delimiter: Char = ','): Set<T> =
    split(delimiter).map { enumValueOf<T>(it) }.toSet()

private const val OSS_URL_ACCESS_ID = "accessId"
private const val OSS_URL_ACCESS_SECRET = "accessSecret"
private const val OSS_URL_PROTOCOL = "protocol"
private const val OSS_URL_BUCKET = "bucket"
private const val OSS_URL_ENDPOINT = "endpoint"
private const val OSS_URL_PATH = "path"

// Format:
//   oss://[<accessId>[:<accessSecret>]]@<protocol>://<bucket>.<endpoint>/<path>
//
// Example:
//   oss://i1oVNSvejfYIBWO0aUakag8F:2vhMi9Ii5Gppbi1UClQdpcQfU1zWVc@https://foo.oss-cn-beijing.aliyuncs.com/bar/baz.json
private const val OSS_URL_PATTERN =
    """oss://((?<$OSS_URL_ACCESS_ID>\w+)(:(?<$OSS_URL_ACCESS_SECRET>\w+))?@)?(?<$OSS_URL_PROTOCOL>http|https)://(?<$OSS_URL_BUCKET>[a-zA-Z_0-9-]+).(?<$OSS_URL_ENDPOINT>[^/]+)/(?<$OSS_URL_PATH>.+)"""

fun String.toOssFileProperties(): OssFileProperties {
    val matchResult = OSS_URL_PATTERN.toRegex().matchEntire(this)
        ?: throw IllegalArgumentException("malformed url \"$this\", should be in format of `oss://[<accessId>[:<accessSecret>]]@<protocol>://<bucket>.<endpoint>/<path>`")

    val groups = matchResult.groups as MatchNamedGroupCollection

    return OssFileProperties(
        groups[OSS_URL_PROTOCOL]!!.value,
        groups[OSS_URL_BUCKET]!!.value,
        groups[OSS_URL_ENDPOINT]!!.value,
        groups[OSS_URL_PATH]!!.value,
        groups[OSS_URL_ACCESS_ID]?.value,
        groups[OSS_URL_ACCESS_SECRET]?.value
    )
}
