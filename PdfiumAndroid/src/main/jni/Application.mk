APP_STL := c++_shared
APP_CPPFLAGS += -fexceptions
APP_LDFLAGS += -Wl,-z,max-page-size=16384

#For ANativeWindow support
APP_PLATFORM = android-19

APP_ABI :=  armeabi-v7a \
            arm64-v8a \
            x86 \
            x86_64
