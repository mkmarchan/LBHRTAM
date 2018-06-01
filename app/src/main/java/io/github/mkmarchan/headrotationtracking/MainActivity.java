package io.github.mkmarchan.headrotationtracking;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.illposed.osc.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mRotationVector;
    private boolean updateRelativeOri;
    private float[] relativeOri;
    private OSCPortOut sender;

    /*
    Ran when the app is loaded. Creates the layout of the application and initializes
    class variables / button handlers.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the layout
        setContentView(R.layout.activity_main);

        // Register the rotation vector listener
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(this, mRotationVector,
                SensorManager.SENSOR_DELAY_FASTEST);

        // Set the orientation button handler. Makes the next rotation vector message be the
        // "base" orientation of the phone
        final Button orientation_button = findViewById(R.id.orientation_button);
        orientation_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateRelativeOri = true;
            }
        });

        // Set the host update button handler. Attempts to create an OSC message sender with the
        // provided information.
        final Button host_update_button = findViewById(R.id.host_update_button);
        host_update_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    sender = new OSCPortOut(InetAddress.getByName(((TextView)findViewById(R.id.host_ip)).getText().toString()),
                            Integer.parseInt(((TextView)findViewById(R.id.host_port)).getText().toString()));
                } catch (Exception e) {
                    System.out.println("create sender fail");
                    System.out.println(e.getMessage());
                }
            }
        });

        sender = null;
    }

    // Empty function to fulfill interface contract
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // Triggers every time the rotation vector sensor has new information. This reads the sensor
    // information and sends out over the network through OSC if host information has been
    // input.
    public void onSensorChanged(SensorEvent event) {
        float[] rotVec = event.values;
        float[] rotMat = new float[16];
        float[] orientation = new float[3];

        // Transform the rotation vector into a rotation matrix
        SensorManager.getRotationMatrixFromVector(rotMat, rotVec);

        // Transform the rotation matrix into 3 rotational coordinates (mixed extrinsic / intrinsic)
        SensorManager.getOrientation(rotMat, orientation);

        // Set the base orientation if indicated by the user
        if (updateRelativeOri) {
            System.out.println(Arrays.toString(orientation));
            relativeOri = Arrays.copyOf(orientation, orientation.length);
            updateRelativeOri = false;
        }

        // If a base orientation has been set, get the difference between that orientation and the
        // current one.
        if (relativeOri != null) {
            for (int i = 0; i < orientation.length; i++) {
                float diff = orientation[i] - relativeOri[i];
                float diffMag = Math.abs(diff);
                float fPi = (float) Math.PI;
                orientation[i] = diffMag <= Math.PI ? diff
                        : (fPi - Math.abs(diffMag - fPi)) * -1 * Math.signum(diff);
            }
        }

        // Flip the signs of the coordinates to conform to the ATK standard
        for (int i = 0; i < orientation.length; i++) {
            orientation[i] *= -1;
        }

        // Post the new values to the screen
        updateDisplayValues(orientation);

        // Build orientation OSC message
        List<Object> args = new ArrayList<Object>();
        for (int i = 0; i < orientation.length; i++) {
            args.add(orientation[i]);
        }
        OSCMessage msg = new OSCMessage("/headTracker", args);
        Thread t = new Thread(new MyRunnable(msg));
        t.start();
    }

    // Sets the azimuth, pitch, and roll values on the screen to be those contained in orientation
    private void updateDisplayValues(float[] orientation) {
        ((TextView)findViewById(R.id.azimuth)).setText(String.format("%.3f",orientation[0]));
        ((TextView)findViewById(R.id.pitch)).setText(String.format("%.3f",orientation[1]));
        ((TextView)findViewById(R.id.roll)).setText(String.format("%.3f",orientation[2]));
    }

    // Private thread class to send OSC messages
    private class MyRunnable implements Runnable {
        private OSCMessage msg;
        public MyRunnable(OSCMessage msg) {
            this.msg = msg;
        }

        public void run() {
            if (sender != null) {
                try {
                    System.out.println(msg.getAddress());
                    System.out.println(msg.getArguments());
                    sender.send(msg);
                } catch (Exception e) {
                    System.out.println("send fail");
                    e.printStackTrace();
                }
            }
        }
    }
}
