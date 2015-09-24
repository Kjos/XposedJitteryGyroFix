package net.kajos.gyronoisefilter;

import android.util.Log;
import android.util.SparseArray;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static de.robv.android.xposed.XposedHelpers.findClass;
import android.hardware.Sensor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;


public class GyroscopeNoiseFilter implements IXposedHookLoadPackage {
	public static XSharedPreferences pref;
    private static final String TAG = "GyroFilter";

    private static List<Object> antiJitterValues(boolean absolute_mode, float[] values, float[][] medianValues, float[] prevValues) {
    	try {
	    	// Note about values[]:
	    	// values[] contains the current sensor's value for each axis (there are 3 since it's in 3D).
	    	// The values are measured in rad/s, which is standard since Android 2.3 (before, some phones can return values in deg/s).
	    	// To get values of about 1.0 you have to turn at a speed of almost 60 deg/s.

	    	// Init arrays
	    	int nbaxis = medianValues.length;
	    	int filter_size = medianValues[0].length;
	    	float tmpArray[] = new float[filter_size]; // used to temporarily copy medianValues to compute the median
	    	Log.d(TAG, "nbaxis: "+Integer.toString(nbaxis)+" filter_size: "+Integer.toString(filter_size));

	    	// Get preferences
	    	pref.makeWorldReadable(); // try to make the preferences world readable (because here we are inside the hook, we are not in our app anymore, so normally we do not have the rights to access the preferences of our app)
	    	pref.reload(); // reload the preferences to get the latest value (ie, if the user changed the values without rebooting the phone)
	    	int filter_size_new = Integer.parseInt(pref.getString("filter_size", "10")); // number of sample values to keep to compute the median
	    	String filter_type = pref.getString("filter_type", "median");
	    	float filter_alpha = Float.parseFloat(pref.getString("filter_alpha", "-1"));
	    	float filter_min_change = Float.parseFloat(pref.getString("filter_min_change", "0.0")); // minimum value change threshold in sensor's value to aknowledge the new value (otherwise don't change the value, but we still register the new value in the median array)
	    	float filter_stationary_min_change = Float.parseFloat(pref.getString("filter_stationary_min_change", "0.0"));
	    	int filter_round_precision = Integer.parseInt(pref.getString("filter_round_precision", "0"));

	    	// Resize the medianValues array and copy the new filter size
	    	if (filter_size_new < 1) filter_size_new = 1; // validate user input, the array must be at least of size 1 (this disables the computation of the mean, but other options will still work)
	    	if (filter_size_new != filter_size) {
	    		// Copy the current median values into a temporary array
	    		float cpyArray[][] = new float[nbaxis][filter_size];
	    		for (int k = 0; k < nbaxis; k++) {
	    			for (int i = 0; i < filter_size; i++) {
	    				cpyArray[k][i] = medianValues[k][i];
	    			}
	    		}
	    		// Resize the medianValues array
	    		medianValues = new float[nbaxis][filter_size_new];
	    		// Recopy back the values from the temporary array
	    		int min_filter_size = (filter_size < filter_size_new) ? filter_size : filter_size_new; // be careful not to overflow the array, if the new size is smaller than before
	    		for (int k = 0; k < nbaxis; k++) {
	    			for (int i = 0; i < min_filter_size; i++) {
	    				medianValues[k][i] = cpyArray[k][i];
	    			}
	    		}
	    		Log.d(TAG, "variables: filter_size: "+Integer.toString(filter_size)+" filter_size_new: "+Integer.toString(filter_size_new)+" filter_min_change:"+Float.toString(filter_min_change));
	    		// Update the filter size counter
	    		filter_size = filter_size_new;
	    	}

	        // Process the gyroscope's values (3D so 3 values)
	        for (int k = 0; k < nbaxis; k++) { // for each of the 3 dimensions of the gyro (or more if the number of axis is more)
	    		// -- Updating the medianValues array (which stores the last known values to be able to compute the median)
	            for (int i = filter_size - 1; i > 0; i--) { // shift the values in the medianValues array (we forget the oldest value at the end of the array)
	                medianValues[k][i] = medianValues[k][i - 1];
	            }

	            // Add new value (insert at index 0)
	            medianValues[k][0] = values[k];

	            // -- Compute the median and replace the current gyroscope's value

	            float filteredval = 0.0f;
	            if (filter_type.equals("none")) {
	            	// Disable the filter (but not the other strategies, such as stationary minimum value etc.)
	            	filteredval = values[k];
	            } else if (filter_type.equals("median")) {
	                // Median
	                // Copy the values to a temporary array
	                for (int f = 0; f < tmpArray.length; f++) tmpArray[f] = medianValues[k][f];

	                // Sort the values
	                Arrays.sort(tmpArray);

	                // Pick the median value
	                filteredval = tmpArray[(int)(tmpArray.length/2)];

	            } else if (filter_type.equals("mean")) {
	                // Moving average (special reduced case of a low-pass filter)
	                float sum = 0.0f;
	                for (float val : medianValues[k]) sum += val;
	                filteredval = sum / medianValues[k].length;

	            } else if (filter_type.equals("lowpass")) {
	                // Low pass filter
	                float alpha = 0.5f;
	                if (filter_alpha >= 0.0f) alpha = filter_alpha;
	                filteredval = lowPass(alpha, values[k], prevValues[k]);

	            } else if (filter_type.equals("addsmooth")) {
	            	// Additive smoothing (kind of, I'm not sure the computation is really correct since it's in the continuous domain, you should not use this method)
	                float alpha = 0.1f;
	                if (filter_alpha >= 0.0f) alpha = filter_alpha;
	                float sum = 0.0f;
	                for (float val : medianValues[k]) sum += val;
	                filteredval = (values[k] + alpha) / (sum + alpha*medianValues.length);
	            }

	            // Update the sensor's value for each axis
	            if (absolute_mode || values[k] != 0.0f) { // act on the sensor only if the value is not 0 (ie, if the sensor is moving, if it's not, we'd better stick to that and not replace the value with the median), except if we are in absolute coordinate mode, then we always need to check the values

	            	// Min stationary value change threshold
	            	// remember that gyroscope's values are relative, so 0.0 means the sensor didn't move for a given axis, and any other value (positive or negative) means it rotated in the given axis
	            	// so if the value is close to 0 but not quite, but still below than a minimum threshold, we consider that this is noise and we don't move
	            	if (filter_stationary_min_change > 0.0f && (
	            				(!absolute_mode && Math.abs(values[k]) < filter_stationary_min_change) || // with relative coordinates, it's easy to tell if we are close to the stationary state (ie, we don't move much, the acceleration is slow) because the stationary state is represented by 0.0f, and any movement is a greater (or negative) value, so we can easily cut the movement if it's too small (ie, probably a jitter)
	            				(absolute_mode && Math.abs(Math.abs(values[k]) - Math.abs(prevValues[k])) < filter_stationary_min_change) // when the coordinates are absolute, the stationary_min_change degenerates into min_change because there's no way to tell if we are close to the stationary state
	            				)
	            			) {
	            		Log.d(TAG, "NOT MOVING (stationary) axis: "+k+" current_val:"+Float.toString(values[k])+" previous_val:"+Float.toString(prevValues[k])+" filtered_val:"+Float.toString(filteredval));
	            		if (absolute_mode) {
	            			values[k] = prevValues[k];
	            		} else {
	            			values[k] = 0.0f;
	            		}
	            	} else {

	            		// Min value change threshold
	            		// If the difference between the previous position and current's coordinate is lesser than a value, we nullify the coordinate (and the movement) for the current axis
	                    if (filter_min_change > 0.0f && // either filter min change threshold is disabled (value == 0)
	                    		Math.abs(Math.abs(values[k]) - Math.abs(prevValues[k])) < filter_min_change) { // or it is enabled (value > 0) and then we check if the current median difference with the previous sensor's value is above the minimum change threshold
	                    	Log.d(TAG, "NOT MOVING (min change) axis: "+k+" current_val:"+Float.toString(values[k])+" previous_val:"+Float.toString(prevValues[k])+" filtered_val:"+Float.toString(filteredval));
	                    	// nullify the sensor for this axis, so that it does not move
	                    	if (absolute_mode) {
	                    		values[k] = prevValues[k]; // in absolute coordinate mode, we restore the previous coordinate
	                    	} else {
	                    		values[k] = 0.0f; // else in relative mode, we just set to 0
	                    	}
	                    } else {
	                    	// Else, it's ok, we can move the sensor
	                        // Set median (or another filter's result) in place of the value for this sensor's axis
	                    	Log.d(TAG, "moving axis: "+k+" current_val:"+Float.toString(values[k])+" previous_val:"+Float.toString(prevValues[k])+" filtered_val:"+Float.toString(filteredval));
	                        values[k] = filteredval;
	                    }
	            	}

	                // Rounding the value
	                if (filter_round_precision > 0) {
	                	float rounded = (float)(Math.floor(values[k] * Math.pow(10,filter_round_precision) +.5) / Math.pow(10,filter_round_precision));
	                	Log.d(TAG, "before rounding: "+Float.toString(values[k])+" after rounding: "+Float.toString(rounded));
	                	values[k] = rounded;
	                }

	                Log.d(TAG, "final value axis: "+k+" value: "+Float.toString(values[k]));
	            }

	            // Remember the current sensor's value
	            prevValues[k] = values[k];
	        }
    	} catch(Throwable t) {
    		Log.e(TAG, "Exception in antiJitterValues(): "+t.getMessage());
    	}

    	// Return the arrays we need to memorize so that the caller can update its arrays
    	// This is necessary because we do not only update the values of these arrays, but we can also resize them (and in that case, the values are not passed by reference)
        List<Object> retlist = new ArrayList<Object>();
        retlist.add(medianValues);
        retlist.add(prevValues);
    	return retlist;
    }

    // -- SystemSensorManager hook (system hook on the gyroscope for most apps)
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    	Log.d(TAG, "Package currently in: " + lpparam.packageName);

    	pref = new XSharedPreferences(GyroscopeNoiseFilter.class.getPackage().getName(), "pref_median"); // load the preferences using Xposed (necessary to be accessible from inside the hook, SharedPreferences() won't work)
    	pref.makeWorldReadable();

        try {
            final Class<?> sensorEQ = findClass(
                    "android.hardware.SystemSensorManager$SensorEventQueue",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(sensorEQ, "dispatchSensorEvent", new
                    XC_MethodHook() {
            			// Pre-process for this hook

            			// Set the number of values to anti-jitter
            			// You should edit this for each hook
                    	int nbaxis = 3;

		            	// Init the arrays
                    	int filter_size = 10;
                        float medianValues[][] = new float[nbaxis][filter_size]; // stores the last sensor's values in each dimension (3D so 3 dimensions)
                        float prevValues[] = new float[nbaxis]; // stores the previous sensor's values to restore them if needed

                        private void changeSensorEvent(float[] values) {
                        	// Note about values[]:
                        	// values[] contains the current sensor's value for each axis (there are 3 since it's in 3D).
                        	// The values are measured in rad/s, which is standard since Android 2.3 (before, some phones can return values in deg/s).
                        	// To get values of about 1.0 you have to turn at a speed of almost 60 deg/s.

                        	// Anti-jitter the values!
                        	// Externalizing the antiJitterValues() function (ie, putting it outside of the hook) allows us to reuse the same function for several hooks.
                        	// However, the previous values and the history of the median values will be different for different hooks (because the values are different), so we need to preprocess the values and to store them in different arrays for each hook. That's why we do this pre-processing here (and above this function).
                        	List<Object> retlist = antiJitterValues(false, values, medianValues, prevValues);

                        	// Update the local arrays for this hook
                        	medianValues = (float[][])retlist.get(0);
                        	prevValues = (float[])retlist.get(1);
                        }

                        // Hook caller
                        // This is where we tell what we should do when the hook is triggered (ie, when the hooked function/method is called)
                        // Basically, we just check a few stuffs about the sensor's values and then we call our changeSensorEvent() to do the rest
                        @SuppressWarnings("unchecked")
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws
                                Throwable {
                            Field field = param.thisObject.getClass().getEnclosingClass().getDeclaredField("sHandleToSensor");
                            field.setAccessible(true);
                            int handle = (Integer) param.args[0];
                            Sensor ss = ((SparseArray<Sensor>) field.get(0)).get(handle);
                            if(ss.getType() == Sensor.TYPE_GYROSCOPE || ss.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED){
                                changeSensorEvent((float[]) param.args[1]);
                            }
                        }
                    });

            Log.d(TAG, "Installed sensorevent patch in: " + lpparam.packageName);

        } catch (Throwable t) {
        	Log.e(TAG, "Exception in SystemSensorEvent hook: "+t.getMessage());
            // Do nothing
        }

        // -- Cardboard SDK hook: HeadTransform
        // This is an optional hook (ie, it will hook only if the lib is used in the app), hence the try/catch
        /*
        try {
            final Class<?> cla = findClass(
                    "com.google.vrtoolkit.cardboard.HeadTransform",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(cla, "getHeadView", new
                    XC_MethodHook() {

		    			// Pre-process for this hook

		    			// Set the number of values to anti-jitter
		    			// You should edit this for each hook
		            	int nbaxis = 13;

		            	// Init the arrays
		            	int filter_size = 10;
		                float medianValues[][] = new float[nbaxis][filter_size]; // stores the last sensor's values in each dimension (3D so 3 dimensions)
		                float prevValues[] = new float[nbaxis]; // stores the previous sensor's values to restore them if needed

		                private void changeSensorEvent(float[] values) {
		                	// Note about values[]:
		                	// values[] contains the current sensor's value for each axis (there are 3 since it's in 3D).
		                	// The values are measured in rad/s, which is standard since Android 2.3 (before, some phones can return values in deg/s).
		                	// To get values of about 1.0 you have to turn at a speed of almost 60 deg/s.

		                	// Anti-jitter the values!
		                	// Externalizing the antiJitterValues() function (ie, putting it outside of the hook) allows us to reuse the same function for several hooks.
		                	// However, the previous values and the history of the median values will be different for different hooks (because the values are different), so we need to preprocess the values and to store them in different arrays for each hook. That's why we do this pre-processing here (and above this function).
		                	List<Object> retlist = antiJitterValues(true, values, medianValues, prevValues);

		                	// Update the local arrays for this hook
		                	medianValues = (float[][])retlist.get(0);
		                	prevValues = (float[])retlist.get(1);
		                }

		                // Hook caller
		                // This is where we tell what we should do when the hook is triggered (ie, when the hooked function/method is called)
		                // Basically, we just check a few stuffs about the sensor's values and then we call our changeSensorEvent() to do the rest
	                    @Override
	                    protected void afterHookedMethod(MethodHookParam param) throws
	                            Throwable {
	                        Log.d(TAG, "Hook 1!");
	                        super.afterHookedMethod(param);
	                        float[] values = (float[])param.args[0];
	                        Log.d(TAG, "BOBO1");
		                    for (int i = 0;i<values.length;i++) {
		                    	Log.d(TAG, "BOBO before values: "+i+" : "+values[i]);
		                    	//values[i] = 0.0f;
		                    }
		                    changeSensorEvent(values);
		                    for (int i = 0;i<values.length;i++) {
		                    	Log.d(TAG, "BOBO after values: "+i+" : "+values[i]);
		                    	//values[i] = 0.0f;
		                    }
	                        Log.d(TAG, "BOBO2");
	                    }
                    });

            Log.d(TAG, "Installed cardboard head patch in: " + lpparam.packageName);

        } catch (Throwable t) {
        	Log.e(TAG, "Exception in Cardboard Head hook: "+t.getMessage());
            // Do nothing
        }
        */

        // -- Cardboard SDK hook: Eye
        // This is an optional hook (ie, it will hook only if the lib is used in the app), hence the try/catch
        try {
            final Class<?> cla = findClass(
                    "com.google.vrtoolkit.cardboard.Eye",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(cla, "getEyeView", new
                    XC_MethodHook() {

		    			// Pre-process for this hook

		    			// Set the number of values to anti-jitter
            			// You should edit this for each hook
		            	int nbaxis = 13;

		            	// Init the arrays
		            	int filter_size = 10;
		                float medianValues[][] = new float[nbaxis][filter_size]; // stores the last sensor's values in each dimension (3D so 3 dimensions)
		                float prevValues[] = new float[nbaxis]; // stores the previous sensor's values to restore them if needed

		                private void changeSensorEvent(float[] values) {
		                	// Note about values[]:
		                	// values[] contains the current sensor's value for each axis (there are 3 since it's in 3D).
		                	// The values are measured in rad/s, which is standard since Android 2.3 (before, some phones can return values in deg/s).
		                	// To get values of about 1.0 you have to turn at a speed of almost 60 deg/s.

		                	// Anti-jitter the values!
		                	// Externalizing the antiJitterValues() function (ie, putting it outside of the hook) allows us to reuse the same function for several hooks.
		                	// However, the previous values and the history of the median values will be different for different hooks (because the values are different), so we need to preprocess the values and to store them in different arrays for each hook. That's why we do this pre-processing here (and above this function).
		                	List<Object> retlist = antiJitterValues(true, values, medianValues, prevValues);

		                	// Update the local arrays for this hook
		                	medianValues = (float[][])retlist.get(0);
		                	prevValues = (float[])retlist.get(1);
		                }

		                // Hook caller
		                // This is where we tell what we should do when the hook is triggered (ie, when the hooked function/method is called)
		                // Basically, we just check a few stuffs about the sensor's values and then we call our changeSensorEvent() to do the rest

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws
                                Throwable {
                            Log.d(TAG, "Hook 2!");
                            super.afterHookedMethod(param);
                            float[] values = (float[])param.getResult();
                            Log.d(TAG, "BABA1");
                            for (int i = 0;i<values.length;i++) {
                            	Log.d(TAG, "BABA before values: "+i+" : "+values[i]);
                            	//values[i] = 0.0f;
                            }
                            changeSensorEvent(values);
                            for (int i = 0;i<values.length;i++) {
                            	Log.d(TAG, "BABA after values: "+i+" : "+values[i]);
                            	//values[i] = 0.0f;
                            }
                            Log.d(TAG, "BABA2");
                        }
                    });

            Log.d(TAG, "Installed cardboard eye patch in: " + lpparam.packageName);

        } catch (Throwable t) {
        	Log.e(TAG, "Exception in Cardboard Eye hook: "+t.getMessage());
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
    protected static float lowPass(float alpha, float current, float prev) {
        //if ( prev == null ) return current;

        //for ( int i=0; i<input.length; i++ ) {
        float output = prev + alpha * (current - prev);
        //}
        return output;
    }
}
