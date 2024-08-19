package tw.edu.pu.cameraxapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.util.Size
import android.widget.Toast
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.DequantizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.common.ops.QuantizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

class Detector_yolov8 {
    //輸入圖像大小
    private val INPUT_SIZE = Size(640, 640)
    //輸出大小，[1,6300,85]
    private val x = 8400
    private val OUTPUT_SIZE = intArrayOf(1, x, 85)
    private val IS_INT8 = false // 是否使用INT8量化模型
    private val DETECT_THRESHOLD = 0.25f //置信度阈值
    private val IOU_THRESHOLD = 0.45f //IOU阈值
    private val IOU_CLASS_DUPLICATED_THRESHOLD = 0.7f //類別重疊的IOU阈值

    private val LABEL_FILE = "coco_label.txt" //標籤文件名

    private var BITMAP_HEIGHT = 0
    private var BITMAP_WIDTH = 0
    private val input8SINT8QuantParams = MetadataExtractor.QuantizationParams(0.003921568859368563f, 0)
    private val output8SINT8QuantParams = MetadataExtractor.QuantizationParams(0.006305381190031767f, 5)
    private lateinit var MODEL_FILE: String

    private var tflite: Interpreter? = null
    private var associatedAxisLabels: List<String>? = null
    private val options = Interpreter.Options()

    fun getModelFile(): String? {
        return MODEL_FILE
    }

    fun setModelFile(modelFile: String) {
        MODEL_FILE = modelFile
        Log.d(">>> ", "MODEL NAME SET --- $MODEL_FILE, $modelFile")
    }
    fun getLabelFile(): String {
        return LABEL_FILE
    }
    fun getInputSize(): Size {
        return INPUT_SIZE
    }
    fun getOutputSize(): IntArray {
        return OUTPUT_SIZE
    }
    /**
     * 初始化模型，可以通过 addNNApiDelegate() 或 addGPUDelegate() 提前加载相应代理
     *
     * @param activity
     */
    fun initialModel(activity: Context) {
        // 初始化模型
        try {
            Log.d(">>> ", "loading model --- $MODEL_FILE")
            val tfliteModel = FileUtil.loadMappedFile(activity, MODEL_FILE)
            tflite = Interpreter(tfliteModel, options)
            Log.i("tfliteSupport", "Success reading model: $MODEL_FILE")

            associatedAxisLabels = FileUtil.loadLabels(activity, LABEL_FILE)
            Log.i("tfliteSupport", "Success reading label: $LABEL_FILE")

        } catch (e: IOException) {
            Log.e("tfliteSupport", "Error reading model or label: ", e)
            Toast.makeText(activity, "load model error: " + e.message, Toast.LENGTH_LONG).show()
        }
    }
    /**
     * 检测步骤
     *
     * @param bitmap
     * @return
     */
    fun detect(bitmap: Bitmap): ArrayList<Recognition> {
        Log.d("TAG","bitmap width"+bitmap.height)
        BITMAP_HEIGHT = bitmap.height
        BITMAP_WIDTH = bitmap.width

        // yolov8s-tflite的输入是:[1, 640, 640,3], 摄像头每一帧图片需要resize,再归一化
        var yolov8sTfliteInput: TensorImage
        val imageProcessor: ImageProcessor
        if (IS_INT8) {
            // Initialization code
            imageProcessor = ImageProcessor.Builder()
                // Create an ImageProcessor with all ops required.
                .add(ResizeOp(INPUT_SIZE.height, INPUT_SIZE.width, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0.0f, 255.0f))
                .add(QuantizeOp(input8SINT8QuantParams.zeroPoint.toFloat(), input8SINT8QuantParams.scale))
//                .add(QuantizeOp(input5SINT8QuantParams.zeroPoint.toFloat(), input5SINT8QuantParams.scale))
                .add(CastOp(DataType.UINT8))
                .build()
            // Create a TensorImage object. This creates the tensor of the corresponding
            // tensor type (uint8 in this case) that the TensorFlow Lite interpreter needs.
            yolov8sTfliteInput = TensorImage(DataType.UINT8)
        } else {
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(INPUT_SIZE.height, INPUT_SIZE.width, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0.0f, 255.0f))
                .build()
            yolov8sTfliteInput = TensorImage(DataType.FLOAT32)
        }
        // Analysis code for every frame
        // Preprocess the image
        yolov8sTfliteInput.load(bitmap)
        yolov8sTfliteInput = imageProcessor.process(yolov8sTfliteInput);
//        val processedInput = imageProcessor.process(yolov5sTfliteInput)

        // yolov5s-tflite的输出是:[1, 6300, 85], 可以从v5的GitHub release处找到相关tflite模型, 输出是[0,1], 处理到320.
        var probabilityBuffer: TensorBuffer
        if (IS_INT8) {
            probabilityBuffer = TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.UINT8)
        } else {
            probabilityBuffer = TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.FLOAT32)
        }

        // 推理计算
        if (tflite != null) {
            // 这里tflite默认会加一个batch=1的纬度
            Log.d(">>> ", "${yolov8sTfliteInput.tensorBuffer.flatSize} ${probabilityBuffer.flatSize}")
            tflite!!.run(yolov8sTfliteInput.buffer, probabilityBuffer.buffer)
        }

        // 输出反量化,需要是模型tflite.run之后执行.
        if (IS_INT8) {
            val tensorProcessor = TensorProcessor.Builder()
                .add(DequantizeOp(output8SINT8QuantParams.zeroPoint.toFloat(), output8SINT8QuantParams.scale))
                .build()
            probabilityBuffer = tensorProcessor.process(probabilityBuffer)
        }

        // 输出数据被平铺了出来
        val recognitionArray = probabilityBuffer.floatArray

        // 这里将flatten的数组重新解析(xywh,obj,classes).
        val allRecognitions = ArrayList<Recognition>()
        for (i in 0 until OUTPUT_SIZE[1]) {
            val gridStride = i * OUTPUT_SIZE[2]
            // 由于yolov5作者在导出tflite的时候对输出除以了image size, 所以这里需要乘回去
            val x = recognitionArray[0 + gridStride] * BITMAP_WIDTH
            val y = recognitionArray[1 + gridStride] * BITMAP_HEIGHT
            val w = recognitionArray[2 + gridStride] * BITMAP_WIDTH
            val h = recognitionArray[3 + gridStride] * BITMAP_HEIGHT
            val xmin = max(0, (x - w / 2).toInt())
            val ymin = max(0, (y - h / 2).toInt())
            val xmax = min(BITMAP_WIDTH, (x + w / 2).toInt())
            val ymax = min(BITMAP_HEIGHT, (y + h / 2).toInt())
            val confidence = recognitionArray[4 + gridStride]
            val classScores = recognitionArray.copyOfRange(5 + gridStride, OUTPUT_SIZE[2] + gridStride)
            //    if (i % 1000 == 0) {
            //        Log.i("tfliteSupport", "x,y,w,h,conf:$x,$y,$w,$h,$confidence")
            //    }
            var labelId = 0
            var maxLabelScores = 0f
            for (j in 0 until classScores.size) {
                if (classScores[j] > maxLabelScores) {
                    maxLabelScores = classScores[j]
                    labelId = j
                }
            }
            // 添加你的 Recognition 对象到 allRecognitions 列表中

            val recognition = Recognition(
                labelId,
                "",
                maxLabelScores,
                confidence,
                RectF(xmin.toFloat(), ymin.toFloat(), xmax.toFloat(), ymax.toFloat())
            )
            allRecognitions.add(recognition)
        }

        // 非极大抑制输出
        val nmsRecognitions = nms(allRecognitions)
        // 第二次非极大抑制, 过滤那些同个目标识别到2个以上目标边框为不同类别的
        val nmsFilterBoxDuplicationRecognitions = nmsAllClass(nmsRecognitions)

        // 更新label信息
        for (recognition in nmsFilterBoxDuplicationRecognitions) {
            val labelId = recognition.labelId
            val labelName = associatedAxisLabels!![labelId]
            recognition.labelName = labelName
        }

        return nmsFilterBoxDuplicationRecognitions
    }
    private fun nms(allRecognitions: ArrayList<Recognition>): ArrayList<Recognition> {
        val nmsRecognitions = ArrayList<Recognition>()

// 遍历每个类别, 在每个类别下做nms
        for (i in 0 until OUTPUT_SIZE[2] - 5) {
            // 这里为每个类别做一个队列, 把labelScore高的排前面
            val pq = PriorityQueue(x, Comparator<Recognition> { l, r ->
                // Intentionally reversed to put high confidence at the head of the queue.
                java.lang.Float.compare(r.confidence, l.confidence)
            })

            // 相同类别的过滤出来, 且obj要大于设定的阈值
            for (recognition in allRecognitions) {
                if (recognition.labelId == i && recognition.confidence > DETECT_THRESHOLD) {
                    pq.add(recognition)
                }
            }

            // nms循环遍历
            while (pq.isNotEmpty()) {
                // 概率最大的先拿出来
                val detections = pq.toTypedArray()
                val max = detections[0]
                nmsRecognitions.add(max)
                pq.clear()

                for (k in 1 until detections.size) {
                    val detection = detections[k]
                    if (boxIou(max.location, detection.location) < IOU_THRESHOLD) {
                        pq.add(detection)
                    }
                }
            }
        }
        return nmsRecognitions
    }
    /**
     * 对所有数据不区分类别做非极大抑制
     *
     * @param allRecognitions
     * @return
     */
    protected fun nmsAllClass(allRecognitions: ArrayList<Recognition>): ArrayList<Recognition> {
        val nmsRecognitions = ArrayList<Recognition>()

        val pq = PriorityQueue(100,
            Comparator<Recognition> { l, r ->
                // Intentionally reversed to put high confidence at the head of the queue.
                r.confidence.compareTo(l.confidence)
            })

        // 相同类别的过滤出来, 且obj要大于设定的阈值
        for (recognition in allRecognitions) {
            if (recognition.confidence > DETECT_THRESHOLD) {
                pq.add(recognition)
            }
        }

        while (pq.isNotEmpty()) {
            // 概率最大的先拿出来
            val detections = pq.toTypedArray()
            val max = detections[0]
            nmsRecognitions.add(max)
            pq.clear()

            for (k in 1 until detections.size) {
                val detection = detections[k]
                if (boxIou(max.location, detection.location) < IOU_CLASS_DUPLICATED_THRESHOLD) {
                    pq.add(detection)
                }
            }
        }
        return nmsRecognitions
    }

    protected fun boxIou(a: RectF, b: RectF): Float {
        val intersection = boxIntersection(a, b)
        val union = boxUnion(a, b)
        return if (union <= 0) 1f else intersection / union
    }

    protected fun boxIntersection(a: RectF, b: RectF): Float {
        val maxLeft = maxOf(a.left, b.left)
        val maxTop = maxOf(a.top, b.top)
        val minRight = minOf(a.right, b.right)
        val minBottom = minOf(a.bottom, b.bottom)
        val w = minRight - maxLeft
        val h = minBottom - maxTop

        return if (w < 0 || h < 0) 0f else w * h
    }

    protected fun boxUnion(a: RectF, b: RectF): Float {
        val i = boxIntersection(a, b)
        val u = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i
        return u
    }

    /**
     * 添加NNapi代理
     */
    fun addNNApiDelegate() {
        var nnApiDelegate: NnApiDelegate? = null
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            val nnApiOptions = NnApiDelegate.Options()
//            nnApiOptions.setAllowFp16(true)
//            nnApiOptions.setUseNnapiCpu(true)
            //ANEURALNETWORKS_PREFER_LOW_POWER：倾向于以最大限度减少电池消耗的方式执行。这种设置适合经常执行的编译。
            //ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER：倾向于尽快返回单个答案，即使这会耗费更多电量。这是默认值。
            //ANEURALNETWORKS_PREFER_SUSTAINED_SPEED：倾向于最大限度地提高连续帧的吞吐量，例如，在处理来自相机的连续帧时。
//            nnApiOptions.setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED)
//            nnApiDelegate = NnApiDelegate(nnApiOptions)
            nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
            Log.i("tfliteSupport", "using nnapi delegate.")
        }
    }

    /**
     * 添加GPU代理
     */
    fun addGPUDelegate() {
        val compatibilityList = CompatibilityList()
        if (compatibilityList.isDelegateSupportedOnThisDevice) {
            val delegateOptions = compatibilityList.bestOptionsForThisDevice
            val gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
            Log.i("tfliteSupport", "using gpu delegate.")
        } else {
            addThread(4)
        }
    }

    /**
     * 添加线程数
     * @param thread
     */
    fun addThread(thread: Int) {
        options.setNumThreads(thread)
    }

}