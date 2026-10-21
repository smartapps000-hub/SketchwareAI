package android.os;
public final class StrictMode {
    public static void setVmPolicy(VmPolicy p) {} public static void setThreadPolicy(ThreadPolicy p) {}
    public static final class VmPolicy { public static final class Builder { public Builder() {} public Builder(VmPolicy p) {} public Builder detectAll() { return this; } public Builder penaltyLog() { return this; } public Builder detectFileUriExposure() { return this; } public VmPolicy build() { return new VmPolicy(); } } }
    public static final class ThreadPolicy { public static final class Builder { public Builder permitAll() { return this; } public ThreadPolicy build() { return new ThreadPolicy(); } } }
}
