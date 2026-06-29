#include <jni.h>
#include <limits.h>
#include <sys/prctl.h>
#include <sys/system_properties.h>
#include <unistd.h>

JNIEXPORT jstring JNICALL
Java_app_grapheneos_goscompat_securespawn_SecureSpawnCheck_nativeSystemProperty(
        JNIEnv* env, jclass clazz, jstring key) {
    (void) clazz;

    const char* key_chars = (*env)->GetStringUTFChars(env, key, NULL);
    if (key_chars == NULL) {
        return NULL;
    }

    char value[PROP_VALUE_MAX] = "";
    __system_property_get(key_chars, value);
    (*env)->ReleaseStringUTFChars(env, key, key_chars);
    return (*env)->NewStringUTF(env, value);
}

JNIEXPORT jint JNICALL
Java_app_grapheneos_goscompat_securespawn_SecureSpawnCheck_nativeDumpable(
        JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;

    return prctl(PR_GET_DUMPABLE);
}

JNIEXPORT jint JNICALL
Java_app_grapheneos_goscompat_securespawn_SecureSpawnCheck_nativeChdir(
        JNIEnv* env, jclass clazz, jstring path) {
    (void) clazz;

    const char* path_chars = (*env)->GetStringUTFChars(env, path, NULL);
    if (path_chars == NULL) {
        return -1;
    }

    int result = chdir(path_chars);
    (*env)->ReleaseStringUTFChars(env, path, path_chars);
    return result;
}

JNIEXPORT jstring JNICALL
Java_app_grapheneos_goscompat_securespawn_SecureSpawnCheck_nativeGetcwd(
        JNIEnv* env, jclass clazz) {
    (void) clazz;

    char buffer[PATH_MAX];
    if (getcwd(buffer, sizeof(buffer)) == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, buffer);
}
