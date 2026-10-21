#!/bin/bash
# usage: art.sh <classpath> <main class> [args]   Android 12 / API 31 ART (setup-art.sh), interpreter, no framework
export ANDROID_ROOT=/system ANDROID_ART_ROOT=/apex/com.android.art ANDROID_I18N_ROOT=/apex/com.android.i18n ANDROID_TZDATA_ROOT=/apex/com.android.tzdata
export ANDROID_DATA=${ANDROID_DATA:-/tmp/art-data}; mkdir -p $ANDROID_DATA
A=/apex/com.android.art/javalib
export BOOTCLASSPATH=$A/core-oj.jar:$A/core-libart.jar:$A/okhttp.jar:$A/bouncycastle.jar:$A/apache-xml.jar:/apex/com.android.i18n/javalib/core-icu4j.jar:/apex/com.android.conscrypt/javalib/conscrypt.jar
export LD_LIBRARY_PATH=/apex/com.android.art/lib64:/apex/com.android.i18n/lib64:/apex/com.android.runtime/lib64/bionic:/apex/com.android.runtime/lib64:/apex/com.android.conscrypt/lib64:/apex/com.android.os.statsd/lib64:/system/lib64
unset JAVA_TOOL_OPTIONS JAVA_HOME
CP=$1; shift
# -Xmx above ~1.5g aborts this runtime at start
exec /apex/com.android.art/bin/dalvikvm64 -Xbootclasspath:$BOOTCLASSPATH -Xnoimage-dex2oat -Xusejit:false -Xint ${ART_OPTS:--Xmx1536m -Xms64m} -cp $CP "$@"
