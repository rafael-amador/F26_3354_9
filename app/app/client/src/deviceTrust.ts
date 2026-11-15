// TD-1 (3) "Remember this device for 30 days": the device token the server gives after a sign-in with the code is
// kept on this device, per username. Phone: expo-secure-store (OS keychain / keystore). Browser: localStorage.
// The server stores only a hash and decides whether the token is still valid (A-39).
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { deviceKey, type RememberedDevice } from './logic';

async function read(key: string): Promise<string | null> {
  if (Platform.OS === 'web') return globalThis.localStorage?.getItem(key) ?? null;
  return SecureStore.getItemAsync(key);
}
async function write(key: string, value: string | null): Promise<void> {
  if (Platform.OS === 'web') {
    if (value === null) globalThis.localStorage?.removeItem(key);
    else globalThis.localStorage?.setItem(key, value);
    return;
  }
  if (value === null) await SecureStore.deleteItemAsync(key);
  else await SecureStore.setItemAsync(key, value);
}

export async function getRememberedDevice(username: string): Promise<RememberedDevice | null> {
  if (!username.trim()) return null;
  try {
    const raw = await read(deviceKey(username));
    return raw ? (JSON.parse(raw) as RememberedDevice) : null;
  } catch {
    return null;
  }
}

export async function saveRememberedDevice(username: string, d: RememberedDevice): Promise<void> {
  await write(deviceKey(username), JSON.stringify(d));
}

export async function forgetRememberedDevice(username: string): Promise<void> {
  await write(deviceKey(username), null);
}
