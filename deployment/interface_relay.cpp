#include<stdio.h>
#include<iostream>

#include<sys/socket.h>
#include<arpa/inet.h>
#include<unistd.h>
#include <net/if.h>

#include<cstring>

#include "Light.h"

const int ERROR_CODE = -1;
const int NO_FLAGS = 0;
const char* TARGET_IP = "10.10.100.254";
const int TARGET_PORT = 8899;

int connectTo(const char* interfaceName) {
  int socketFileDescriptor = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  if(socketFileDescriptor == ERROR_CODE) {
    perror("Failed to create socket");
    return ERROR_CODE;
  }
  if(setsockopt(socketFileDescriptor, SOL_SOCKET, SO_BINDTODEVICE, interfaceName, strlen(interfaceName)) == ERROR_CODE) {
    perror("Failed to bind to network interface");
    return ERROR_CODE;
  }
  struct sockaddr_in lightAddress{};
  lightAddress.sin_family = AF_INET;
  lightAddress.sin_addr.s_addr = inet_addr(TARGET_IP);
  lightAddress.sin_port = htons(TARGET_PORT);
  if(connect(socketFileDescriptor, (struct sockaddr*) &lightAddress, sizeof(lightAddress)) == ERROR_CODE) {
    perror("Failed to connect to light");
    return ERROR_CODE;
  }
  return socketFileDescriptor;
}

void disconnect(int socketFileDescriptor) {
  close(socketFileDescriptor);
}

void sendDataTo(const char* interfaceName, const jbyte* data, const jsize length) {
  int socketFileDescriptor = connectTo(interfaceName);
  if(socketFileDescriptor == ERROR_CODE) {
    return;
  }
  if(send(socketFileDescriptor, data, length, NO_FLAGS) != length) {
    std::cerr << "Failed to send data!" << std::endl;
  }
  disconnect(socketFileDescriptor);
}

JNIEXPORT void JNICALL Java_Light_sendDataToInterface(JNIEnv* env, jobject self, jstring nativeInterfaceName, jbyteArray nativeByteArray) {
  const char* interfaceName = env->GetStringUTFChars(nativeInterfaceName, NULL);
  jbyte* nativeBytes = env->GetByteArrayElements(nativeByteArray, NULL);
  jsize byteCount = env->GetArrayLength(nativeByteArray);

  sendDataTo(interfaceName, nativeBytes, byteCount);

  env->ReleaseByteArrayElements(nativeByteArray, nativeBytes, 0);
  env->ReleaseStringUTFChars(nativeInterfaceName, interfaceName);
}
