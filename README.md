# JitteryGyroFixForCardboard
Apply a noise filter on gyroscope to avoid jitter in head tracking / VR apps such as Google Cardboard. For example useful for Moto G and Huawei Ascend phones, but can be used with any rooted Android phone.

Needs Xposed in order to be installed.

This module currently implements a few different strategies to filter and reduce noise, which can be used complementary or alone (each option can be disabled):

1- Filter type: type of the filter that will be applied to reduce noise in the gyroscope output.
2- Filter size: the number of samples to use to compute the filtering. Usually, the bigger the filter is, the less jitter there will be but at the expense of some lag.
3- Filter optional value: value of the constant that configures some types of filters such as lowpass or additive smoothing.
4- Minimum value change threshold: prevents the phone from registering the new sensor's values if the difference to the median is smaller than the given threshold.
5- Stationary minimum value threshold: when stationary, prevents the sensor from moving if the change is below the given threshold (this is similar to min value change but here it only affects the stationary state, when you are not moving).
6- Rounding precision: round all sensor's values to the given decimal.

Options can be changed on-the-fly without having to restart the phone, and are instantly applied to the sensor, so that you can switch between this option screen and a VR app to test for the parameters that reduce the jitter the most for you.
