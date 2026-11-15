package cometfit.stubs;

import org.springframework.stereotype.Component;

import cometfit.services.SmartDeviceGateway;

/**
 * *** STUB *** of the external "Smart Device" actor's pairing handshake (UC-2, SD-2 "pair (Bluetooth / cloud)").
 * No Bluetooth / HealthKit / Health Connect is contacted. Pairing always succeeds, except for device ids that
 * start with "FAIL" so the failure path can be demonstrated and tested.
 */
@Component
public class FakeSmartDeviceGateway implements SmartDeviceGateway {
    @Override
    public boolean pair(String deviceId, String deviceType, String platform) {
        return deviceId != null && !deviceId.toUpperCase().startsWith("FAIL");
    }
}
