package com.example.android.bluetoothlegatt;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String GAS_RESISTANCE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
       attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
       attributes.put("00002a05-0000-1000-8000-00805f9b34fb", "Unknown Service");
       attributes.put("00002a00-0000-1000-8000-00805f9b34fb", "Hex Values");
       attributes.put("00002a01-0000-1000-8000-00805f9b34fb", "Zero Value");
       attributes.put("00002a04-0000-1000-8000-00805f9b34fb", "Unknown Value");
       attributes.put("d973f2e1-b19e-11e2-9e96-0800200c9a66", "Safety Sensors Data");
       attributes.put("d973f2e2-b19e-11e2-9e96-0800200c9a66", "Device Information Service");
    }
    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
