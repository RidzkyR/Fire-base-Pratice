package com.example.custom_model_deployment

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.cert.CertPath

class PredictionHelper(
    private var modelName: String = "rice_stock.tflite",
    val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private var onDownloadSuccess: () -> Unit
) {
    private var isGPUSupported: Boolean = false
    private var interpreter: InterpreterApi? = null

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
                downloadModel()
                //loadLocalModel()
            }
            .addOnFailureListener {
                onError(context.getString(R.string.tflite_is_not_initialized_yet))
            }
    }

    //mengunduh model dari firebase
    @Synchronized
    private fun downloadModel() {
        val conditions = CustomModelDownloadConditions.Builder()
            .requireWifi()
            .build()
        FirebaseModelDownloader.getInstance()
            .getModel("Rice-Stock", DownloadType.LOCAL_MODEL, conditions)
            .addOnSuccessListener { model: CustomModel ->
                try {
                    onDownloadSuccess()
                    initializeInterpreter(model) // menginisialisasi interpreter TensorFlow Lite
                } catch (e: IOException) {
                    onError(e.message.toString())
                }
            }
            .addOnFailureListener { e: Exception? ->
                onError(context.getString(R.string.firebaseml_model_download_failed))
            }
    }

    // menginisialisasi interpreter TensorFlow Lite
    private fun initializeInterpreter(model: Any) {
        interpreter?.close()
        try {
            val options = InterpreterApi.Options()
                .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
            if (isGPUSupported) {
                options.addDelegateFactory(GpuDelegateFactory())
            }
            if (model is ByteBuffer) {
                interpreter = InterpreterApi.create(model, options)
            }else if (model is CustomModel){
                model.file?.let{
                    interpreter = InterpreterApi.create(it, options)
                }
            }
        } catch (e: Exception) {
            onError(e.message.toString())
            Log.e(TAG, e.message.toString())
        }
    }

    fun predict(inputString: String) {
        if (interpreter == null) {
            return
        }

        val inputArray = FloatArray(1)
        inputArray[0] = inputString.toFloat()
        val outputArray = Array(1) { FloatArray(1) }
        try {
            interpreter?.run(inputArray, outputArray)
            onResult(outputArray[0][0].toString())
        } catch (e: Exception) {
            onError(context.getString(R.string.no_tflite_interpreter_loaded))
            Log.e(TAG, e.message.toString())
        }
    }

    fun close() {
        interpreter?.close()
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        assetManager.openFd(modelPath).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val starOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, starOffset, declaredLength)
            }
        }
    }

    companion object {
        private const val TAG = "PredictionHelper"
    }

}