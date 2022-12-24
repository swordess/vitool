/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.docker

enum class DockerRegistry(val url: String) {
    aliyun("registry.cn-beijing.aliyuncs.com"),
    dockerhub("registry.hub.docker.com")
}