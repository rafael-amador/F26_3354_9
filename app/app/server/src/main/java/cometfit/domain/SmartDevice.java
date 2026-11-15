package cometfit.domain;

import jakarta.persistence.*;

/**
 * SmartDevice — §5.2 Domain Data: deviceId, deviceType, platform; operation pushSteps(steps, heartRate).
 * SD-2 participant "SmartDevice (HealthKit / Health Connect)". FR-4 / UC-2.
 * The real device is an external actor; this entity is the server's record of it. pushSteps() is
 * received by ActivityController.pushSteps (see SD-2 loop "periodic sync").
 */
@Entity
@Table(name = "smart_devices")
public class SmartDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;
    @Column(name = "device_type", nullable = false)
    private String deviceType;
    @Column(nullable = false)
    private String platform;

    protected SmartDevice() {}

    public SmartDevice(String deviceId, String deviceType, String platform) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.platform = platform;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceType() { return deviceType; }
    public String getPlatform() { return platform; }
}
