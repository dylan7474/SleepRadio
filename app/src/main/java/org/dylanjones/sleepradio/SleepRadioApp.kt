package org.dylanjones.sleepradio

import android.app.Application

/**
 * Application entry point.
 *
 * Kept plain for Phase 0. `@HiltAndroidApp` and the DI graph are added as the
 * first task of Phase 1, once the KSP/Hilt toolchain integration is verified
 * against this preview AGP.
 */
class SleepRadioApp : Application()
