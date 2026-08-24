//
// AdMob unit IDs are injected as preprocessor macros at build time via
// cppFlags (-Dadmob_banner_id=..., -Dadmob_interstitial_id=...). The source
// values live in local.properties (not committed) or env vars.
//

#include <jni.h>
#include <string>

#define STRINGIFY(x) #x
#define TOSTRING(x) STRINGIFY(x)

extern "C" JNIEXPORT jstring JNICALL
Java_com_ahmadabuhasan_qrbarcode_utils_AppConfig_bannerAdId(
        JNIEnv *env,
        jclass /* clazz */) {
    return env->NewStringUTF(TOSTRING(admob_banner_id));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ahmadabuhasan_qrbarcode_utils_AppConfig_interstitialAdId(
        JNIEnv *env,
        jclass /* clazz */) {
    return env->NewStringUTF(TOSTRING(admob_interstitial_id));
}
