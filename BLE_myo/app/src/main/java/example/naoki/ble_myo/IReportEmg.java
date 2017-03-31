package example.naoki.ble_myo;

/**
 * Created by timestring on 3/18/17.
 */
public interface IReportEmg {
    void onReportEmg(MyoGattCallback myoCallback, int[][] channels);
}
