package tw.edu.pu.cameraxapp

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaSync.OnErrorListener
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.OutputOptions
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tw.edu.pu.cameraxapp.databinding.ActivityMainBinding
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.io.path.OnErrorResult

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    //If use cameraController
   // private lateinit var cameraController: LifecycleCameraController
    //If use cameraProvider
    private  var imageCapture:ImageCapture?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if(!hasPermissions(baseContext)){
            //Request camera-related permissions
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }else{
            lifecycleScope.launch {
                startCamera()
            }

        }
        viewBinding.ImageCaptureButton.setOnClickListener { takePhoto() }
    }
    private suspend fun startCamera(){
        val cameraProvider=ProcessCameraProvider.getInstance(this).await() //實作cameraProvider介面，getInstance方法回傳可監聽的future
        val preview=Preview.Builder().build()
        preview.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)  //可在此處傳入自訂服務提供者
        imageCapture=ImageCapture.Builder().build()

        val cameraSelector=CameraSelector.DEFAULT_BACK_CAMERA //預設後置鏡頭

        try {
            cameraProvider.unbindAll()
            var camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview ,imageCapture
            )

        }catch (e:Exception){
            Log.e(TAG, "UseCase binding failed",e)

        }

    }
    private fun takePhoto(){
        //Create time stamped name and MediaStore entry
        val name=SimpleDateFormat(FILENAME_FORMART, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues= ContentValues().apply{
            put(MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg")
            if(Build.VERSION.SDK_INT>Build.VERSION_CODES.P){
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Camerax-Image")
            }
        }
        //Create output options object which contains file + metadata
        val outputOptions =ImageCapture.OutputFileOptions
            .Builder(contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues)
            .build()

        //Set up image capture listener ,which is triggered after photo has
        //been taken
        imageCapture?.takePicture(
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
    private val activityResultLauncher =
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
    companion object{
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
        fun hasPermissions(context:Context)= Companion.REQUIRED_PERMISSIONS.all{
            ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED

        }
    }
}
