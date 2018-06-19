package com.jason.experiment.bletest.utils;

import java.util.UUID;

/**
 * UuidUtils
 * Created by jason on 13/6/18.
 */
public class UuidUtils {

    private static final String UUID_SERVICE      = "39b105d6-9915-47e6-9de2-bcd27254aa02";
    private static final String UUID_INCOMING     = "03e1b19f-5f6d-4095-945a-2dab076dd974";
    private static final String UUID_OUTGOING     = "a16aa006-264d-4d9b-8ed3-6f5e3fd5188d";
    private static final String UUID_NOTIFICATION = "fba05e5d-39bc-4f16-911d-e669837cca7f";

    public static UUID getServiceUuid() {
        return UUID.fromString(UUID_SERVICE);
    }

    public static UUID getClientOutUuid() {
        return UUID.fromString(UUID_INCOMING);
    }

    public static UUID getServerOutUuid() {
        return UUID.fromString(UUID_OUTGOING);
    }

    public static UUID getNotificationUuid() {
        return UUID.fromString(UUID_NOTIFICATION);
    }
}
