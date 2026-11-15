/*
 ============================================================================
 Name        : hev-jni.c
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2019 - 2023 hev
 Description : Jave Native Interface
 ============================================================================
 */

#ifdef ANDROID

#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <string.h>
#include <unistd.h>

#include "hev-main.h"

#include "hev-jni.h"

/* clang-format off */
#ifndef PKGNAME
#define PKGNAME hev/htproxy
#endif
#ifndef CLSNAME
#define CLSNAME TProxyService
#endif
/* clang-format on */

#define STR(s) STR_ARG (s)
#define STR_ARG(c) #c
#define N_ELEMENTS(arr) (sizeof (arr) / sizeof ((arr)[0]))

typedef struct _ThreadData ThreadData;

struct _ThreadData
{
    char *path;
    int fd;
};

static atomic_int is_running;
static int thread_joinable;
static JavaVM *java_vm;
static pthread_t work_thread;
static pthread_mutex_t mutex;
static pthread_key_t current_jni_env;

static int log_filter_installed;

static jboolean native_start_service (JNIEnv *env, jobject thiz,
                                      jstring conig_path, jint fd);
static jboolean native_stop_service (JNIEnv *env, jobject thiz);
static jboolean native_is_running (JNIEnv *env, jobject thiz);
static jlongArray native_get_stats (JNIEnv *env, jobject thiz);
static jboolean native_install_log_filter (JNIEnv *env, jobject thiz);

static JNINativeMethod native_methods[] = {
    { "TProxyStartService", "(Ljava/lang/String;I)Z",
      (void *)native_start_service },
    { "TProxyStopService", "()Z", (void *)native_stop_service },
    { "TProxyIsRunning", "()Z", (void *)native_is_running },
    { "TProxyGetStats", "()[J", (void *)native_get_stats },
    { "TProxyInstallLogFilter", "()Z", (void *)native_install_log_filter },
};

static void
detach_current_thread (void *env)
{
    (*java_vm)->DetachCurrentThread (java_vm);
}

jint
JNI_OnLoad (JavaVM *vm, void *reserved)
{
    JNIEnv *env = NULL;
    jclass klass;
    jint res;

    java_vm = vm;
    res = (*vm)->GetEnv (vm, (void **)&env, JNI_VERSION_1_4);
    if (res != JNI_OK)
        return JNI_ERR;

    klass = (*env)->FindClass (env, STR (PKGNAME) "/" STR (CLSNAME));
    if (!klass)
        return JNI_ERR;
    res = (*env)->RegisterNatives (env, klass, native_methods,
                                   N_ELEMENTS (native_methods));
    (*env)->DeleteLocalRef (env, klass);
    if (res < 0)
        return JNI_ERR;

    pthread_key_create (&current_jni_env, detach_current_thread);
    pthread_mutex_init (&mutex, NULL);

    return JNI_VERSION_1_4;
}

static void *
thread_handler (void *data)
{
    ThreadData *tdata = data;

    hev_socks5_tunnel_main (tdata->path, tdata->fd);

    atomic_store_explicit (&is_running, 0, memory_order_release);

    free (tdata->path);
    free (tdata);

    return NULL;
}

static jboolean
native_start_service (JNIEnv *env, jobject thiz, jstring config_path, jint fd)
{
    const jbyte *bytes;
    ThreadData *tdata;
    int res;
    jboolean result = JNI_FALSE;

    pthread_mutex_lock (&mutex);

    if (atomic_load_explicit (&is_running, memory_order_acquire))
        goto exit;

    if (thread_joinable) {
        pthread_join (work_thread, NULL);
        thread_joinable = 0;
    }

    tdata = malloc (sizeof (ThreadData));
    if (!tdata)
        goto exit;
    tdata->fd = fd;

    bytes = (const jbyte *)(*env)->GetStringUTFChars (env, config_path, NULL);
    if (!bytes) {
        free (tdata);
        goto exit;
    }
    tdata->path = strdup ((const char *)bytes);
    (*env)->ReleaseStringUTFChars (env, config_path, (const char *)bytes);
    if (!tdata->path) {
        free (tdata);
        goto exit;
    }

    atomic_store_explicit (&is_running, 1, memory_order_release);
    res = pthread_create (&work_thread, NULL, thread_handler, tdata);
    if (res != 0) {
        atomic_store_explicit (&is_running, 0, memory_order_release);
        free (tdata->path);
        free (tdata);
        goto exit;
    }

    thread_joinable = 1;
    result = JNI_TRUE;
exit:
    pthread_mutex_unlock (&mutex);
    return result;
}

static jboolean
native_stop_service (JNIEnv *env, jobject thiz)
{
    int res = 0;

    pthread_mutex_lock (&mutex);

    if (!thread_joinable)
        goto exit;

    if (atomic_load_explicit (&is_running, memory_order_acquire))
        hev_socks5_tunnel_quit ();
    res = pthread_join (work_thread, NULL);

    thread_joinable = 0;
    atomic_store_explicit (&is_running, 0, memory_order_release);
exit:
    pthread_mutex_unlock (&mutex);
    return res == 0 ? JNI_TRUE : JNI_FALSE;
}

static jboolean
native_is_running (JNIEnv *env, jobject thiz)
{
    return atomic_load_explicit (&is_running, memory_order_acquire) ? JNI_TRUE :
                                                                      JNI_FALSE;
}

static jlongArray
native_get_stats (JNIEnv *env, jobject thiz)
{
    size_t tx_packets, rx_packets, tx_bytes, rx_bytes;
    jlongArray res;
    jlong array[4];

    hev_socks5_tunnel_stats (&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);
    array[0] = tx_packets;
    array[1] = tx_bytes;
    array[2] = rx_packets;
    array[3] = rx_bytes;

    res = (*env)->NewLongArray (env, 4);
    (*env)->SetLongArrayRegion (env, res, 0, 4, array);

    return res;
}

static void
emit_filtered_log_line (const char *line, int *panic_burst)
{
    int prio = ANDROID_LOG_INFO;

    if (!line || line[0] == '\0')
        return;

    /* Drop emulator Goldfish/SwiftShader GL driver stderr noise */
    if (strncmp (line, "s_glBindAttribLocation:", 23) == 0)
        return;

    if (strstr (line, " ERROR ") || strstr (line, "[ERROR]") ||
        strstr (line, " FATAL ") || strstr (line, "[FATAL]") ||
        strstr (line, "fatal error:") || strstr (line, "panic:") ||
        strstr (line, "runtime error:")) {
        prio = ANDROID_LOG_ERROR;
        *panic_burst = 24;
    } else if (strstr (line, " WARN ") || strstr (line, "[WARN]") ||
               strstr (line, " WARNING ") || strstr (line, "[WARNING]")) {
        prio = ANDROID_LOG_WARN;
        *panic_burst = 0;
    } else if (strstr (line, " INFO ") || strstr (line, "[INFO]") ||
               strstr (line, " DEBUG ") || strstr (line, "[DEBUG]")) {
        prio = ANDROID_LOG_INFO;
        *panic_burst = 0;
    } else if (*panic_burst > 0) {
        prio = ANDROID_LOG_ERROR;
        (*panic_burst)--;
    }

    __android_log_write (prio, "GoLog", line);
}

static void *
log_filter_thread_handler (void *arg)
{
    int read_fd = *(int *)arg;
    char read_buf[2048];
    char line_buf[4096];
    size_t line_len = 0;
    int panic_burst = 0;

    free (arg);

    for (;;) {
        ssize_t n = read (read_fd, read_buf, sizeof (read_buf));
        if (n < 0) {
            if (errno == EINTR)
                continue;
            break;
        }
        if (n == 0)
            break;

        for (ssize_t i = 0; i < n; i++) {
            char ch = read_buf[i];
            if (ch == '\r')
                continue;
            if (ch == '\n') {
                line_buf[line_len] = '\0';
                emit_filtered_log_line (line_buf, &panic_burst);
                line_len = 0;
            } else {
                if (line_len >= sizeof (line_buf) - 1) {
                    line_buf[line_len] = '\0';
                    emit_filtered_log_line (line_buf, &panic_burst);
                    line_len = 0;
                }
                line_buf[line_len++] = ch;
            }
        }
    }

    if (line_len > 0) {
        line_buf[line_len] = '\0';
        emit_filtered_log_line (line_buf, &panic_burst);
    }

    close (read_fd);
    return NULL;
}

static jboolean
native_install_log_filter (JNIEnv *env, jobject thiz)
{
    int fds[2];
    int *read_fd_ptr;
    pthread_t thread;
    pthread_attr_t attr;
    int res;

    (void)env;
    (void)thiz;

    pthread_mutex_lock (&mutex);
    if (log_filter_installed) {
        pthread_mutex_unlock (&mutex);
        return JNI_TRUE;
    }

    if (pipe2 (fds, O_CLOEXEC) != 0) {
        pthread_mutex_unlock (&mutex);
        return JNI_FALSE;
    }

    read_fd_ptr = malloc (sizeof (int));
    if (!read_fd_ptr) {
        close (fds[0]);
        close (fds[1]);
        pthread_mutex_unlock (&mutex);
        return JNI_FALSE;
    }
    *read_fd_ptr = fds[0];

    pthread_attr_init (&attr);
    pthread_attr_setdetachstate (&attr, PTHREAD_CREATE_DETACHED);
    res = pthread_create (&thread, &attr, log_filter_thread_handler, read_fd_ptr);
    pthread_attr_destroy (&attr);

    if (res != 0) {
        free (read_fd_ptr);
        close (fds[0]);
        close (fds[1]);
        pthread_mutex_unlock (&mutex);
        return JNI_FALSE;
    }

    dup2 (fds[1], STDOUT_FILENO);
    dup2 (fds[1], STDERR_FILENO);
    close (fds[1]);

    log_filter_installed = 1;
    pthread_mutex_unlock (&mutex);
    return JNI_TRUE;
}

#endif /* ANDROID */
