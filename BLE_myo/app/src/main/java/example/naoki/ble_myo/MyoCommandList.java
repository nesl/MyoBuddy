package example.naoki.ble_myo;

/**
 * Created by naoki on 15/04/13.
 * 
 * This class is List of Myo Commands, allowing to
 * [https://github.com/thalmiclabs/myo-bluetooth].
 *
 * Modified by TimeString on Mar. 18. 2017
 */
 
public class MyoCommandList {
    public static byte[] sendUnsetData() {
        byte command_data = (byte) 0x01;
        byte payload_data = (byte) 3;
        byte emg_mode     = (byte) 0x00;
        byte imu_mode     = (byte) 0x00;
        byte class_mode   = (byte) 0x00;
        return new byte[]{command_data, payload_data, emg_mode, imu_mode, class_mode};
    }

    public static byte[] sendVibration3() {
        byte command_vibrate = (byte) 0x03;
        byte payload_vibrate = (byte) 1;
        byte vibrate_type = (byte) 0x03;
        return new byte[]{command_vibrate, payload_vibrate, vibrate_type};
    }

    public static byte[] sendEmgOnly() {
        byte command_data = (byte) 0x01;
        byte payload_data = (byte) 3;
        byte emg_mode     = (byte) 0x02;
        byte imu_mode     = (byte) 0x00;
        byte class_mode   = (byte) 0x00;
        return new byte[]{command_data, payload_data, emg_mode, imu_mode, class_mode};
    }

    public static byte[] sendUnLock() {
        byte command_unlock = (byte) 0x0a;
        byte payload_unlock = (byte) 1;
        byte unlock_type    = (byte) 0x01;
        return new byte[]{command_unlock, payload_unlock, unlock_type};
    }

    public static byte[] sendUnSleep() {
        byte command_sleep_mode = (byte) 0x09;
        byte payload_unlock     = (byte) 1;
        byte never_sleep        = (byte) 1;
        return new byte[]{command_sleep_mode, payload_unlock, never_sleep};
    }

    public static byte[] sendNormalSleep() {
        byte command_sleep_mode = (byte) 0x09;
        byte payload_unlock     = (byte) 1;
        byte normal_sleep       = (byte) 0;
        return new byte[]{command_sleep_mode, payload_unlock, normal_sleep};
    }
}
