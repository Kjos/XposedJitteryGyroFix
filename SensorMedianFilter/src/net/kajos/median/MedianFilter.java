package net.kajos.median;

import android.hardware.*;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.Arrays;
import static de.robv.android.xposed.XposedHelpers.findClass;

import android.hardware.Sensor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;


public class MedianFilter implements IXposedHookLoadPackage {
    static float medianValues[][] = new float[3][10];

    static float tmpArray[] = new float[medianValues[0].length];


    private static void changeSensorEvent(SensorEvent sensorEvent) {
        // Shift values
        for (int k = 0; k < 3; k++)
            for (int i = medianValues[0].length-1; i > 0; i--) {
                medianValues[k][i] = medianValues[k][i-1];
            }

        // Add newest values
        medianValues[0][0] = sensorEvent.values[0];
        medianValues[1][0] = sensorEvent.values[1];
        medianValues[2][0] = sensorEvent.values[2];

        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < tmpArray.length; k++)
                tmpArray[k] = medianValues[i][k];

            Arrays.sort(tmpArray);

            float median = tmpArray[tmpArray.length/2];

            // Set median
            sensorEvent.values[i] = median;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("Android package: " + lpparam.packageName);
        Class<?> sensorClass = null;
        try {
            sensorClass = findClass(
                    "com.google.vrtoolkit.cardboard.sensors.HeadTracker",
                    lpparam.classLoader);


        } catch (Throwable t) {
            // Do nothing
        }
        if (sensorClass == null)
            return;

        try {
            XposedBridge.log("Going to inject in android package: " + lpparam.packageName);

            XposedHelpers.findAndHookMethod(sensorClass, "onSensorChanged",
                    SensorEvent.class,
                    new XC_MethodHook() {
                        @SuppressWarnings("unchecked")
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws
                                Throwable {
                            SensorEvent event = (SensorEvent)param.args[0];
                            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                                changeSensorEvent(event);
                            }
                    }
                }
            );

        } catch (Throwable t) {
            // Legacy support for old Cardboard games.
            try {
                XposedBridge.log("Going to inject in android package: " + lpparam.packageName);

                XposedHelpers.findAndHookMethod(sensorClass, "processSensorEvent",
                        SensorEvent.class,
                        new XC_MethodHook() {
                            @SuppressWarnings("unchecked")
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws
                                    Throwable {
                                SensorEvent event = (SensorEvent)param.args[0];
                                if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                                    changeSensorEvent(event);
                                }
                            }
                        }
                );

            } catch (Throwable t2) {
                throw t2;
            }
        }
    }
}