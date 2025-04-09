#include <jni.h>
#ifndef _Included_Light
#define _Included_Light
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT void JNICALL Java_Light_sendDataToInterface(JNIEnv *, jobject, jstring, jbyteArray);
#ifdef __cplusplus
}
#endif
#endif
