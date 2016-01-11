# CHANGELOG

- 1.7:
  * added hook switch for compatibility
 
- 1.6:
  * fixed youtube not playing videos when module activated.

- v1.5:
 * refactoring methods to be modular (to support multiple hooks)
 * added hook for apps using Cardboard SDK's orientation provider
 * fixed rounding strategy
 * fixed minimum value change strategy

- v1.4:
 * refactoring and renaming SensorMedianFilter -> GyroscopeNoiseFilter
 * support for more apps (TYPE_GYROSCOPE_UNCALIBRATED is now supported in addition to TYPE_GYROSCOPE)
 * licensing under GPLv2+

- v1.3:
 * all new preferences screen so that options can be switched and tweaked on-the-fly without having to restart the phone, and the new options are applied instantly, allowing to easily tweak and test the settings to your liking.
 * more filters: running average, low-pass
 * new strategies: minimum value change threshold ; stationary minimum value change ; rounding precision

- v1.2:
 * Added some extra support for different Google Cardboard apps containing older versions of the library.
 * Now hooks in system, so works with every Android application.
 * Fixed drifting issue in some applications.

- v1.0:
 * first public release
