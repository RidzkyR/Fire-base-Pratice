package com.example.custom_model_deployment

import android.content.Context
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite

class PredictionHelper(
    private var modelName: String = "rice_stock.tflite",
    val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var isGPUSupported: Boolean = false

    // inisialisasi TFLite dari Google Play Service
    init {
        TfLiteGpu.isGpuDelegateAvailable(context).onSuccessTask { gpuAvaible ->
            val optionsBuilder = TfLiteInitializationOptions.builder()
            if (gpuAvaible) {
                optionsBuilder.setEnableGpuDelegateSupport(true)
                isGPUSupported = true
            }
            TfLite.initialize(context, optionsBuilder.build())
        }
            .addOnSuccessListener {
                TODO()
            }
            .addOnFailureListener {
                onError(context.getString(R.string.tflite_is_not_initialized_yet))
            }
    }
}