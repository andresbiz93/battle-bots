/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.andres.battle_bots;

import java.util.HashMap;

public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();

    // Declares some sample UUID's
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String HM_10_CONF = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static String HM_RX_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static String UUID_STRING_WELL_KNOWN_SPP =
            "00001101-0000-1000-8000-00805F9B34FB";

    //This is the TX UUID for the Adafruit Bluefruit, so we will check for devices that contain this ID
    public static String UUID_TX =
            "6e400002-b5a3-f393-e0a9-e50e24dcca9e";

    //This is the RX UUID for the Adafruit Bluefruit, so we will check for devices that contain this ID
    public static String UUID_RX =
                    "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    static {
        // Sample Services.
        attributes.put("0000ffe0-0000-1000-8000-00805f9b34fb", "HM 10 Serial");
        attributes.put("00001800-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HM_RX_TX,"RX/TX data");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    // Looks up UUIDs based on inputs
    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
