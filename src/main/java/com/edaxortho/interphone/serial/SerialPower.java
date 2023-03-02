package com.edaxortho.interphone.serial;

import java.io.IOException;

public class SerialPower {

    public static void callPowerScript(String scriptPath) throws IOException {
        Runtime.getRuntime().exec("sudo python " + scriptPath);
    }
}
