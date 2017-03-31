package example.naoki.ble_myo.example.naoki.ble_myo.uihelper;

import android.graphics.Color;

import com.echo.holographlibrary.Line;
import com.echo.holographlibrary.LineGraph;
import com.echo.holographlibrary.LinePoint;

/**
 * Created by timestring on 3/18/17.
 */
public class LineGraphHelper {
    private final static int NUM_SAMPLES = 300;

    private LineGraph mLineGraph;

    private final int[][] data = new int[8][NUM_SAMPLES];


    public LineGraphHelper(LineGraph lineGraph) {
        mLineGraph = lineGraph;
        reset();
    }

    /*
    public void turnOn(int channel) {

    }

    public void turnOff(int channel) {

    }
    */

    public void update(final int[] values) {
        synchronized (data) {
            for (int i = 0; i < 8; i++) {
                System.arraycopy(data[i], 1, data[i], 0, NUM_SAMPLES - 1);
                data[i][NUM_SAMPLES - 1]  = values[i];
            }
        }
    }

    public void reset() {
        synchronized (data) {
            for (int i = 0; i < 8; i++)
                for (int j = 0; j < NUM_SAMPLES; j++)
                    data[i][j] = 0;
        }
    }

    public void draw() {
        mLineGraph.removeAllLines();

        for (int i = 0; i < 1; i++) {
            Line line = new Line();
            for (int j = 0; j < NUM_SAMPLES; j++) {
                LinePoint point = new LinePoint();
                point.setX(j);
                point.setY(data[i][j]);
                line.addPoint(point);
            }

            line.setColor(Color.BLUE);
            line.setShowingPoints(false);

            mLineGraph.addLine(line);
            mLineGraph.setRangeY(-128, 128);
        }
    }
}
