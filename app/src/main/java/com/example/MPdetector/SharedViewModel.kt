// SharedViewModel.kt
package com.example.MPdetector

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {

    // 撮影された生のフレーム(Bitmap)を保持します。
    private val _rawFrame = MutableLiveData<Bitmap>()
    val rawFrame: LiveData<Bitmap> = _rawFrame

    fun setRawFrame(frame: Bitmap) {
        _rawFrame.postValue(frame)
    }

    // アクティブな画像処理モジュールのリストを保持します。
    // `Detector` から `ImgProcessor` に名称を統一しました。
    private val _activeProcessors = MutableLiveData<List<ImgProcessor>>()
    val activeProcessors: LiveData<List<ImgProcessor>> = _activeProcessors

    fun setActiveProcessors(processors: List<ImgProcessor>) {
        _activeProcessors.value = processors
    }
}
