package com.edaxortho.interphone;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListenerWithExceptions;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SerialPortReader implements SerialPortDataListenerWithExceptions {

    private static final Logger LOGGER = LoggerFactory.getLogger(SerialPortReader.class);
    public final SerialPort port;
    public List<String> messages = new ArrayList<>();
    public String currentMessage = "";
    public int lastIndexRead = 0;

    public SerialPortReader(SerialPort port) {
        super();
        this.port = port;
    }

    @Override
    public void catchException(Exception e) {
        System.out.print("EXCEPTION : " + e.getMessage());
        e.printStackTrace();
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
//        System.out.println("[serialEvent]-->" + new Date() + " : " + event.getEventType());
        if (port.bytesAvailable() > 0) {
            byte[] buffer = new byte[port.bytesAvailable()];
            int numRead = port.readBytes(buffer, buffer.length);
            currentMessage += new String(buffer, 0, numRead);
            if (currentMessage.endsWith("\r\n")) {
                LOGGER.info(currentMessage);
                messages.add(currentMessage);
                currentMessage = "";
            }
        }
    }

    public String getLastMessage() {
        int end = messages.size();
        if (end > lastIndexRead) {
            StringBuilder result = new StringBuilder();
            for (int i = lastIndexRead; i < end; i++) {
                result.append(messages.get(i));
            }
            lastIndexRead = end;
            return result.toString();
        }
        return null;
    }
}
