package com.applyfuse.fuse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// FUSE: @HiltAndroidApp triggers Hilt's code generation.
// Every Android app using Hilt must have this annotation
// on the Application class.
@HiltAndroidApp
class FuseApplication : Application()
