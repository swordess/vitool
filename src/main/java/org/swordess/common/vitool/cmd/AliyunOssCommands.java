package org.swordess.common.vitool.cmd;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class AliyunOssCommands {

    @ShellMethod("Verify the STS configuration by retrieving the AK.")
    public String aliyunOssVerifySts(String region, String accessKeyId, String accessKeySecret, String arn) {
        try {
            IClientProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
            DefaultAcsClient client = new DefaultAcsClient(profile);

            AssumeRoleRequest request = new AssumeRoleRequest();
            request.setMethod(MethodType.POST);
            request.setRoleArn(arn);
            request.setRoleSessionName("vitool_verify_" + System.currentTimeMillis());
            request.setDurationSeconds(1000L); // 设置凭证有效时间

            AssumeRoleResponse response = client.getAcsResponse(request);

            String message = "\tExpiration: " + response.getCredentials().getExpiration() + "\n" +
                    "\tAccess Key Id: " + response.getCredentials().getAccessKeyId() + "\n" +
                    "\tAccess Key Secret: " + response.getCredentials().getAccessKeySecret() + "\n" +
                    "\tSecurity Token: " + response.getCredentials().getSecurityToken() + "\n" +
                    "\tRequestId: " + response.getRequestId() + "\n";

            return "SUCCESS\n" + message;

        } catch (ClientException e) {
            String errorMessage = "\tError code: " + e.getErrCode() + "\n" +
                    "\tError message: " + e.getErrMsg() + "\n" +
                    "\tRequestId: " + e.getRequestId();

            return "FAILURE\n" + errorMessage;
        }
    }

}
