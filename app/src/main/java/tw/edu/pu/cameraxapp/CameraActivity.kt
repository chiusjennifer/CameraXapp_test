package tw.edu.pu.cameraxapp

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.Surface.ROTATION_180
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tw.edu.pu.cameraxapp.databinding.ActivityCameraBinding
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale


class CameraActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private val boxPaint = Paint()
    private val textPaint = Paint()
    private lateinit var viewBinding: ActivityCameraBinding////綁定布局文件中的視圖
    //If use cameraController
   //private lateinit var cameraController: LifecycleCameraController
    //If use cameraProvider
    //private  var imageCapture:ImageCapture?=null  //補捉圖像
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: Recording? = null
    private lateinit var imageAnalysis:ImageAnalysis
    //private lateinit var outputDirectory: File
    //private lateinit var cameraExecutor: ExecutorService
    //private var controller:LifecycleCameraController?=null
    private var cameraSelector:CameraSelector=CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var yolov8Detector: Detector_yolov8
    private val tf_path = "yolov8s_float32.tflite"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        imageView = findViewById(R.id.overlayView)
        videoCapture = VideoCapture.withOutput(Recorder.Builder().build())

        Log.v("TAG","viewBinding");

        //setContentView(viewBinding.root)
        // 初始化 View Binding
        //viewBinding = ActivityCameraBinding.inflate(layoutInflater)


       // 使用 viewBinding 访问 viewFinder
        val viewFinder = viewBinding.viewFinder
        if(!hasPermissions(baseContext)){   //作者選擇使用 baseContext，無論程式碼位於 Activity 還是 Fragment 中，都能夠確保使用相同的 Context 進行權限檢查。
            //Request camera-related permissions
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }else{
            lifecycleScope.launch {
                startCamera()
            }

        }
        //viewBinding.ImageCaptureButton.setOnClickListener { takePhoto() } //設置拍照按鈕的點擊監聽器
        viewBinding.Button.setOnClickListener { captureVideo() }
        // 初始化 YOLOv8 檢測器
        yolov8Detector = Detector_yolov8().apply {
            setModelFile(tf_path)
            initialModel(this@CameraActivity)
        }
// 設置繪製邊框的畫筆
        boxPaint.apply {
            strokeWidth = 5f
            style = Paint.Style.STROKE
            color = Color.RED
        }

        // 設置繪製文字的畫筆
        textPaint.apply {
            textSize = 300f
            color = Color.GREEN
            style = Paint.Style.FILL
        }
    }
    private suspend fun startCamera(){  //該方法用於啟動相機
        //val cameraProvider=ProcessCameraProvider.getInstance(this).await() //獲取相機提供程序的實例，getInstance方法回傳可監聽的future
        val cameraProvider = try {
            ProcessCameraProvider.getInstance(this).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get CameraProvider", e)
            return
        }
        val viewFinder = viewBinding.viewFinder
        //TODO bitmap
       // val bitmap = viewFinder.bitmap


        val preview=Preview.Builder().setTargetRotation(viewFinder.display.rotation).build()  //創建预覽和圖像捕獲用例，並绑定到相機生命週期
        preview.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)  //可在此處傳入自訂服務提供者
        //val imageCapture=ImageCapture.Builder().build()
        //todo 接實機要改鏡頭
        //val cameraSelector=CameraSelector.DEFAULT_FRONT_CAMERA //預設前置鏡頭
        //val cameraSelector=CameraSelector.DEFAULT_BACK_CAMERA //預設後置鏡頭
       // val controller=LifecycleCameraController(applicationContext)
        // 设置预览的 SurfaceProvider
        preview.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
        //添加ImageAnalysis
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this , cameraSelector, preview ,videoCapture, imageAnalysis
            )

        }catch (e:Exception){
            Log.e(TAG, "UseCase binding failed",e)

        }
    }
    /*private fun takePhoto(){  //该方法用於拍照並保存照片
        //建立帶有時間戳記的名稱和 MediaStore 條目
        val name=SimpleDateFormat(FILENAME_FORMART, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues= ContentValues().apply{  //創建了一个带有時間戳名稱的 ContentValues 對象，用於储存照片的元數據
            put(MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg")
            if(Build.VERSION.SDK_INT>Build.VERSION_CODES.P){
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Camerax-Image")
            }
        }

        val outputOptions =ImageCapture.OutputFileOptions //用於指定照片的输出選項，包括文件路徑和元數據
            .Builder(contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues)
            .build()

        imageCapture?.takePicture(    //調用 imageCapture?.takePicture() 方法拍攝照片，並傳入输出選項和回調函数處理照片捕獲成功或失敗的情况
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback{
                override fun onError(exc:ImageCaptureException){
                    Log.e(TAG,"Photo capture failed:${exc.message}",exc)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg ="Photo capture succeeded:${outputFileResults.savedUri}"
                    Toast.makeText(baseContext,msg,Toast.LENGTH_LONG).show()
                    Log.d(TAG,msg)
                }
            }
        )
    }
*/
    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        viewBinding.Button.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session and YOLOv8 detection.
            curRecording.stop()
            recording = null
            // Stop YOLOv8 detection
            imageAnalysis.clearAnalyzer()
        }

        // Create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMART, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@CameraActivity,
                        android.Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // Start YOLOv8 detection
                        startYoloV8Detection()
                        viewBinding.Button.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        // Stop YOLOv8 detection
                        imageAnalysis.clearAnalyzer()
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            imageAnalysis.clearAnalyzer()
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.Button.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }
    // 将 ImageProxy 转换为 Bitmap 的函数
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer // Y
        val uBuffer = imageProxy.planes[1].buffer // U
        val vBuffer = imageProxy.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    private val activityResultLauncher =    //使用 registerForActivityResult 注冊了權限請求的回調函数，用於處理權限請求的结果
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions->
            //Handel Permission granted/rejected
            var permissionGranted =true
            permissions.entries.forEach{
                if (it.key in REQUIRED_PERMISSIONS && it.value==false)
                    permissionGranted=false
            }
            if(!permissionGranted){
                Toast.makeText(this,"Permission request denied",Toast.LENGTH_LONG).show()
            }else{
                lifecycleScope.launch {
                    startCamera()
                }
            }
        }
    private fun displayDetectionResults(results: ArrayList<Recognition>) {
        // 假设 results 包含检测到的物体列表
        // 你可以在 UI 上显示它们，或者在 Bitmap 上绘制检测框
        for (result in results) {
            // 在 UI 中显示 result
        }
    }
    private fun startYoloV8Detection() {
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            val bitmap = imageProxyToBitmap(imageProxy)
            val results = yolov8Detector.detect(bitmap)
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)

            for (recognition in results) {
                if (recognition.confidence > 0.4) {
                    val location = recognition.location
                    canvas.drawRect(location, boxPaint)
                    canvas.drawText("${recognition.labelName}:${recognition.confidence}", location.left, location.top, textPaint)
                }
            }

            // 显示带有标注的图片
            runOnUiThread {
                imageView.setImageBitmap(mutableBitmap)
            }
            imageProxy.close()
        }
    }
    companion object{   //定義了一些常量和方法，包括標籤、文件名格式、所需的權限列表以及檢查權限的方法
        private const val TAG="CameraXAPP"
        private const val FILENAME_FORMART="yyyy-MM-dd-HH-mm-ss-SSS"
        private  val REQUIRED_PERMISSIONS=
            mutableListOf(
                android.Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <=Build.VERSION_CODES.P){
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        fun hasPermissions(context:Context)= REQUIRED_PERMISSIONS.all{
            ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED

        }
    }
}
