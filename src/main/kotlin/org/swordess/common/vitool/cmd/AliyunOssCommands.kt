/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.cmd

import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.auth.sts.AssumeRoleRequest
import com.aliyuncs.exceptions.ClientException
import com.aliyuncs.http.MethodType
import com.aliyuncs.profile.DefaultProfile
import com.aliyuncs.profile.IClientProfile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod

@ShellComponent
class AliyunOssCommands {

    @ShellMethod(key = ["alioss verify-sts"], value = "Verify the STS configuration by retrieving the AK.")
    fun aliyunOssVerifySts(region: String, accessKeyId: String, accessKeySecret: String, arn: String): String {
        return try {
            val profile: IClientProfile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret)
            val client = DefaultAcsClient(profile)

            val response = client.getAcsResponse(AssumeRoleRequest().apply {
                sysMethod = MethodType.POST
                roleArn = arn
                roleSessionName = "vitool_verify_" + System.currentTimeMillis()
                durationSeconds = 1000L // 设置凭证有效时间
            })

            with(response) {
                """
                SUCCESS
                    Expiration: ${credentials.expiration}
                    Access Key Id: ${credentials.accessKeyId}
                    Access Key Secret: ${credentials.accessKeySecret}
                    Security Token: ${credentials.securityToken}
                    RequestId: $requestId""".trimIndent()
            }

        } catch (e: ClientException) {
            with(e) {
                """
                FAILURE
                    Error code: $errCode
                    Error message: $errMsg
                    RequestId: $requestId""".trimIndent()
            }
        }
    }

}