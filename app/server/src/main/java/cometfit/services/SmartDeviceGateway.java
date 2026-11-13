package cometfit.services;

/**
 * Boundary to the external actor "Smart Device (watch / phone)" (UC-2; SD-2 lifeline
 * "SmartDevice (HealthKit / Health Connect)"; NFR Interoperability "bluetooth, cloud services ... healthkit or
 * health connect"). SD-2: ActivityController -> SmartDevice "pair (Bluetooth / cloud)" -> "pairing confirmed".
 * Only implementation shipped: the labelled stub {@link cometfit.stubs.FakeSmartDeviceGateway}.
 */
public interface SmartDeviceGateway {
    /** @return true when the device confirmed pairing. */
    boolean pair(String deviceId, String deviceType, String platform);
}
