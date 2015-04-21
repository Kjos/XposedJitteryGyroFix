package net.kajos.median;

import android.hardware.*;
import android.util.SparseArray;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import static de.robv.android.xposed.XposedHelpers.findClass;

import android.hardware.Sensor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;


public class MedianFilter implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        try {
            final Class<?> sensorEQ = findClass(
                    "android.hardware.SystemSensorManager$SensorEventQueue",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(sensorEQ, "dispatchSensorEvent", new
                    XC_MethodHook() {
                        float medianValues[][] = new float[3][10];
                        float tmpArray[] = new float[medianValues[0].length];

                        private void changeSensorEvent(float[] values) {
                            // Shift values
                            for (int k = 0; k < 3; k++) {
                                for (int i = tmpArray.length - 1; i > 0; i--) {
                                    medianValues[k][i] = medianValues[k][i - 1];
                                }

                                // Add newest values
                                medianValues[k][0] = values[k];

                                for (int f = 0; f < tmpArray.length; f++) {
                                    tmpArray[f] = medianValues[k][f];
                                }

                                Arrays.sort(tmpArray);

                                float median = tmpArray[tmpArray.length/2];

                                // Set median
                                values[k] = median;
                            }
                        }

                        @SuppressWarnings("unchecked")
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws
                                Throwable {
                            Field field = param.thisObject.getClass().getEnclosingClass().getDeclaredField("sHandleToSensor");
                            field.setAccessible(true);
                            int handle = (Integer) param.args[0];
                            Sensor ss = ((SparseArray<Sensor>) field.get(0)).get(handle);
                            if(ss.getType() == Sensor.TYPE_GYROSCOPE){
                                changeSensorEvent((float[]) param.args[1]);
                            }
                        }
                    });

            XposedBridge.log("Installed sensorevent patch in: " + lpparam.packageName);

        } catch (Throwable t) {
            // Do nothing
        }
    }
}