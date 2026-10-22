// aconfig flags of android.content.res as the read-only host library (android.content.res.flags-aconfig-cc-host-ro)
// of android-16.0.0 builds them: resource_readwrite_flags is fixed read-only and off in this release.
#pragma once
inline bool android_content_res_resource_readwrite_flags() { return false; }
