package com.mybraintech.demosdk

import android.util.Log

class TNLog {
    companion object {


        private const val DEBUG_TAG = "Dev_debug:"
        private fun isShow(): Boolean {
//            return BuildConfig.DEBUG
            return true
        }

        fun d(tag: String?, msg: String) {
            if (isShow()) {
                Log.d(tag, DEBUG_TAG + msg)
            }
        }

        fun d(tag: String?, msg: String, e: Throwable?) {
            if (isShow()) {
                Log.d(
                    tag,
                    DEBUG_TAG + msg,
                    e
                )
            }
        }

        fun w(tag: String?, msg: String) {
            if (isShow()) {
                Log.w(tag, DEBUG_TAG + msg)
            }
        }

        fun v(tag: String?, msg: String) {
            if (isShow()) {
                Log.v(tag, DEBUG_TAG + msg)
            }
        }

        fun w(tag: String?, msg: String, error: Throwable?) {
            if (isShow()) {
                Log.w(
                    tag,
                    DEBUG_TAG + msg,
                    error
                )
            }
        }

        fun e(tag: String?, msg: String) {
            if (isShow()) {
                Log.e(tag, DEBUG_TAG + msg)
            }
        }

        fun e(tag: String?, msg: String, e: Throwable?) {
            if (isShow()) {
                Log.e(
                    tag,
                    DEBUG_TAG + msg,
                    e
                )
            }
        }

        fun i(tag: String?, msg: String) {
            if (isShow()) {
                Log.i(tag, DEBUG_TAG + msg)
            }
        }
    }
}