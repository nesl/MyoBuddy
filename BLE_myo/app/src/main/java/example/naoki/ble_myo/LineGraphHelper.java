package example.naoki.ble_myo;

import android.graphics.Color;
import android.os.Handler;

import com.echo.holographlibrary.Line;
import com.echo.holographlibrary.LineGraph;
import com.echo.holographlibrary.LinePoint;

/**
 * Created by timestring on 3/18/17.
 */
public class LineGraphHelper {
    private final static int NUM_SAMPLES = 200;

    private LineGraph mLineGraph;
    private Handler mHandler;

    private final int[][] data = new int[8][NUM_SAMPLES];


    public LineGraphHelper(LineGraph lineGraph, Handler handler) {
        mLineGraph = lineGraph;
        mHandler = handler;
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
        draw();
    }

    public void reset() {
        synchronized (data) {
            for (int i = 0; i < 8; i++)
                for (int j = 0; j < NUM_SAMPLES; j++)
                    data[i][j] = 0;
        }
        draw();
    }

    private void draw() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
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
        });
    }
}
