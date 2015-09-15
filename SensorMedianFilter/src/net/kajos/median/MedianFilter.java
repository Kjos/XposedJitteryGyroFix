package net.kajos.median;

import android.util.Log;
import android.util.SparseArray;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.util.Arrays;
import static de.robv.android.xposed.XposedHelpers.findClass;

import android.hardware.Sensor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;


public class MedianFilter implements IXposedHookLoadPackage {
	public XSharedPreferences pref;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    	pref = new XSharedPreferences(MedianFilter.class.getPackage().getName(), "pref_median"); // load the preferences using Xposed (necessary to be accessible from inside the hook, SharedPreferences() won't work)
    	pref.makeWorldReadable();

        try {
            final Class<?> sensorEQ = findClass(
                    "android.hardware.SystemSensorManager$SensorEventQueue",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(sensorEQ, "dispatchSensorEvent", new
                    XC_MethodHook() {
            			int filter_size = 10;
            			float filter_min_change = 0.0f; // TODO: should use a different threshold for each dimension

		            	// Init the variables
                        float medianValues[][] = new float[3][filter_size]; // stores the last sensor's values in each dimension (3D so 3 dimensions)
                        float tmpArray[] = new float[medianValues[0].length]; // used to temporarily copy medianValues to compute the median
                        float prevValues[] = new float[3]; // stores the previous sensor's values to restore them if needed

                        private void changeSensorEvent(float[] values) {
                        	// Note about values[]:
                        	// values[] contains the current sensor's value for each axis (there are 3 since it's in 3D).
                        	// The values are measured in rad/s, which is standard since Android 2.3 (before, some phones can return values in deg/s).
                        	// To get values of about 1.0 you have to turn at a speed of almost 60 deg/s.

                        	// Get preferences
                        	pref.makeWorldReadable(); // try to make the preferences world readable (because here we are inside the hook, we are not in our app anymore, so normally we do not have the rights to access the preferences of our app)
                        	pref.reload(); // reload the preferences to get the latest value (ie, if the user changed the values without rebooting the phone)
                        	int filter_size_new = Integer.parseInt(pref.getString("filter_size", "10")); // number of sample values to keep to compute the median
                        	float filter_min_change_new = Float.parseFloat(pref.getString("filter_min_change", "0.0")); // minimum value change threshold in sensor's value to aknowledge the new value (otherwise don't change the value, but we still register the new value in the median array)
                        	String filter_type = pref.getString("filter_type", "median");
                        	float filter_alpha = Float.parseFloat(pref.getString("filter_alpha", "-1"));

                        	// Copy new value of min change threshold
                        	filter_min_change = filter_min_change_new;
                        	// Resize the medianValues array and copy the new filter size
                        	if (filter_size_new < 1) filter_size_new = 1; // validate user input, the array must be at least of size 1 (this disables the computation of the mean, but other options will still work)
                        	if (filter_size_new != filter_size) {
                        		float cpyArray[][] = new float[3][filter_size];
                        		for (int k = 0; k < 3; k++) {
                        			for (int i = 0; i < filter_size; i++) {
                        				cpyArray[k][i] = medianValues[k][i];
                        			}
                        		}
                        		medianValues = new float[3][filter_size_new];
                        		int min_filter_size = (filter_size < filter_size_new) ? filter_size : filter_size_new;
                        		for (int k = 0; k < 3; k++) {
                        			for (int i = 0; i < min_filter_size; i++) {
                        				medianValues[k][i] = cpyArray[k][i];
                        			}
                        		}
                        		filter_size = filter_size_new;
                        	}

                        	Log.d("MedianFilter", "MedianFilter variables: filter_size: "+Integer.toString(filter_size)+" filter_min_change:"+Float.toString(filter_min_change));

                            // Process the gyroscope's values (3D so 3 values)
                            for (int k = 0; k < 3; k++) { // for each of the 3 dimensions of the gyro
                        		// -- Updating the medianValues array (which stores the last known values to be able to compute the median)
                                for (int i = tmpArray.length - 1; i > 0; i--) { // shift the values in the medianValues array (we forget the oldest value at the end of the array)
                                    medianValues[k][i] = medianValues[k][i - 1];
                                }

                                // Add new value (insert at index 0)
                                medianValues[k][0] = values[k];

                                // -- Compute the median and replace the current gyroscope's value

                                float filteredval = 0.0f;
                                if (filter_type.equals("none")) {
                                	filteredval = values[k];
                                } else if (filter_type.equals("median")) {
	                                // Median
	                                // Copy the values to a temporary array
	                                for (int f = 0; f < tmpArray.length; f++) {
	                                    tmpArray[f] = medianValues[k][f];
	                                }
	
	                                Arrays.sort(tmpArray); // Sort the values
	
	                                // Pick the median value
	                                filteredval = tmpArray[(int)(tmpArray.length/2)];

                                } else if (filter_type.equals("mean")) {
	                                // Moving average (special reduced case of a low-pass filter)
	                                float sum = 0.0f;
	                                for (float val : medianValues[k]) sum += val;
	                                filteredval = sum / medianValues[k].length;

                                } else if (filter_type.equals("lowpass")) {
	                                // Low pass filter
	                                float alpha = 0.2f;
	                                if (filter_alpha >= 0.0f) alpha = filter_alpha;
	                                filteredval = lowPass(alpha, values[k], prevValues[k]);

                                } else if (filter_type.equals("addsmooth")) {
	                            	// Additive smoothing
	                                float alpha = 0.1f;
	                                if (filter_alpha >= 0.0f) alpha = filter_alpha;
	                                float sum = 0.0f;
	                                for (float val : medianValues[k]) sum += val;
	                                filteredval = (values[k] + alpha) / (sum + alpha*medianValues.length);
                                }

                                // Update the sensor's value for each axis
                                if (values[k] != 0.0f) {
	                                if (filter_min_change <= 0.0f || // either filter min change threshold is disabled (value == 0)
	                                		Math.abs(values[k] - filteredval) >= filter_min_change) { // or it is enabled (value > 0) and then we check if the current median difference with the previous sensor's value is above the minimum change threshold
		                                // Set median in place of the value for this sensor's axis
	                                	Log.d("MedianFilter", "MedianFilter moving axis: "+k+" median: "+Float.toString(filteredval)+" current_val:"+Float.toString(values[k])+" previous_val:"+Float.toString(medianValues[k][1]));
		                                values[k] = filteredval;
	                                } else {
	                                	// else do not move this sensor's axis
	                                	Log.d("MedianFilter", "MedianFilter NOT MOVING axis: "+k+" median: "+Float.toString(filteredval)+" current_val:"+Float.toString(values[k])+" previous_val:"+Float.toString(medianValues[k][1]));
	                                	values[k] = 0.0f; // nullify the sensor for this axis, so that it does not move
	                                }
                                }

                                // Remember the current sensor's value
                                prevValues[k] = values[k];
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

    /**
     * Low-pass filter
     * @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
     * @see http://en.wikipedia.org/wiki/Low-pass_filter#Simple_infinite_impulse_response_filter
     * alpha is the time smoothing constant for low-pass filter
     * 0 <= alpha <= 1 ; a smaller value basically means more smoothing
     * See: http://en.wikipedia.org/wiki/Low-pass_filter#Discrete-time_realization
     * All credits go to Thom Nichols thom_nic  http://blog.thomnichols.org/2011/08/smoothing-sensor-data-with-a-low-pass-filter and http://stackoverflow.com/a/5780505/1121352
     */
    protected float lowPass(float alpha, float current, float prev) {
        //if ( prev == null ) return current;

        //for ( int i=0; i<input.length; i++ ) {
        float output = prev + alpha * (current - prev);
        //}
        return output;
    }
}