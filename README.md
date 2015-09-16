# JitteryGyroFixForCardboard
Apply a noise filter on gyroscope to avoid jitter in head tracking / VR apps such as Google Cardboard. For example useful for Moto G and Huawei Ascend phones, but can be used with any rooted Android phone.

Needs Xposed in order to be installed.

Licensed under GPLv2+ (GNU General Public License v2 or later at your convenience).

## Description

The gyroscope is the main sensor for head tracking / virtual reality apps.

However, it's common for phones' gyroscopes to be noisy, which in practice shows as jitter, and this can create a dizzyness feeling and nausea, particularly when standing still (the "camera" still moves around just like if you were at sea).

Android now offers "virtual" sensors, which use a method known as sensor fusion to avoid those noisy outputs by combining several sensors (usually: gyroscope, accelerometer, magnetometer).

However, it's up to each app dev to use these virtual sensors, and to implement further post-processing to smooth the sensor and reduce the noise.

This module adopts another approach: it hooks directly to the hardware gyroscope and it preprocess every outputs with smoothing filters before relaying the data to the apps.

Therefore, the noise is reduced or even eliminated of the gyroscope's output  for **every apps**.

## Options

This module currently implements a few different strategies to filter and reduce noise, which can be used complementary or alone (each option can be disabled):

1. **Filter type**: type of the filter that will be applied to reduce noise in the gyroscope output.
2. **Filter size**: the number of samples to use to compute the filtering. Usually, the bigger the filter is, the less jitter there will be but at the expense of some lag.
3. **Filter optional value**: value of the constant that configures some types of filters such as lowpass or additive smoothing.
4. **Minimum value change threshold**: prevents the phone from registering the new sensor's values if the difference to the median is smaller than the given threshold.
5. **Stationary minimum value threshold**: when stationary, prevents the sensor from moving if the change is below the given threshold (this is similar to min value change but here it only affects the stationary state, when you are not moving).
6. **Rounding precision**: round all sensor's values to the given decimal.

Options can be changed on-the-fly without having to restart the phone, and are instantly applied to the sensor, so that you can switch between this option screen and a VR app to test for the parameters that reduce the jitter the most for you.

## ToDo

* Implement the various filters and strategies from the opensource app [GyroscopeExplorer](https://github.com/KEOpenSource/GyroscopeExplorer).
