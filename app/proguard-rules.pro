# SleepRadio R8 rules.
# Hilt, Room, Media3, DataStore and Compose all ship their own consumer rules;
# these are the app-specific defensive keeps.

# Keep enum valueOf()/values() — used by the DataStore string codecs
# (decodePattern -> NoiseColor.valueOf / BinauralPreset.valueOf).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Line numbers for readable crash reports; hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The ambient service is reached from the manifest + a Hilt @EntryPoint.
-keep class org.dylanjones.sleepradio.playback.AmbientPlaybackService { *; }
