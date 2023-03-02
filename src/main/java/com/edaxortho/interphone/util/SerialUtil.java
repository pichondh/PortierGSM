package com.edaxortho.interphone.util;

import com.fazecast.jSerialComm.SerialPort;

public class SerialUtil {

    public final SerialPort port;

    public SerialUtil(SerialPort port) {
        this.port = port;
    }

    public void sendCommand(String command) {
        byte[] data = command.getBytes();
        port.writeBytes(data, data.length);
    }
}
