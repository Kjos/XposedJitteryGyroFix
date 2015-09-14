# JitteryGyroFixForCardboard
Apply a median filter on gyroscope to avoid jitter in head tracking / VR apps such as Google Cardboard. For example useful for Moto G phones, but can be used with any rooted Android phone.

Needs Xposed in order to be installed.

There are two options that can currently be configured:

1- Filter size: the number of samples to use to compute the median. The bigger the filter is, the less jitter there will be but at the expense of some lag.
2- Minimum value change threshold: prevents the phone from registering the new sensor's values if the difference to the median is smaller than the given threshold.

Options can be changed on the fly without having to restart the phone.
