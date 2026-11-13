// Smart device boundary on the client. Deliverable 1 §5.3.2: "Smart device connection via Bluetooth -> Client
// (Mobile Application)"; SD-2 "SmartDevice (HealthKit / Health Connect)"; NFR Interoperability; FR-4 / UC-2.
//
// A browser on a laptop has no Bluetooth wearable, HealthKit or Health Connect, so the only implementation here is
// the clearly-labelled STUB `FakeSmartDevice` (CLAUDE.md rule 4). A native build would add an adapter for the
// platform health API that implements the same interface.

export interface HealthPermissionPrompt {
  /** UC-2 "Grant Health Permissions"; SD-2 "request health permissions / grant permission". */
  requestHealthPermissions(): Promise<boolean>;
}

export interface Reading {
  steps: number; // steps since the previous sync (A-17)
  heartRate: number;
  timestamp: string;
}

export interface SmartDeviceAdapter {
  readonly deviceId: string;
  readonly deviceType: string;
  readonly platform: string;
  /** SmartDevice.pushSteps(steps, heartRate) source data (§5.2). */
  readSinceLastSync(): Reading;
}

/** Deterministic pseudo-random generator so a demo run is repeatable. */
export function seeded(seed: number) {
  let s = seed >>> 0 || 1;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0x100000000;
  };
}

/** *** STUB *** simulated wearable. Produces plausible step and heart-rate readings. */
export class FakeSmartDevice implements SmartDeviceAdapter {
  readonly deviceType = 'watch';
  readonly platform: string;
  private rand: () => number;

  constructor(readonly deviceId: string, platform = 'simulated', seed = 42) {
    this.platform = platform;
    this.rand = seeded(seed);
  }

  readSinceLastSync(): Reading {
    const steps = 300 + Math.floor(this.rand() * 1700); // 300..1999 steps per sync
    const heartRate = 62 + Math.floor(this.rand() * 60); // 62..121 bpm
    return { steps, heartRate, timestamp: new Date().toISOString() };
  }
}
