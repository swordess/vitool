package org.swordess.common.vitool.util;

import org.springframework.util.StringUtils;

public final class Options {

    private Options() {
    }

    public static String orElseEnv(String value, String envVarName) {
        if (!StringUtils.isEmpty(value)) {
            return value;
        }
        return System.getenv(envVarName);
    }

    public static <T> T must(T value, String errMsg) {
        if (value == null) {
            throw new IllegalArgumentException(errMsg);
        }
        return value;
    }

}
