// !LANGUAGE: -NewInference

interface Continuation<in T>

abstract class C {
    abstract fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean
}