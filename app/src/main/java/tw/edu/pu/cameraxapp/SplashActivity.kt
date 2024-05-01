package tw.edu.pu.cameraxapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity


class SplashActivity : AppCompatActivity() {
    private lateinit var flashImageView: ImageView
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val flashImageView:ImageView = findViewById(R.id.flashImageView)
        // 設定快閃效果
        val fadeInImageAnimation = AlphaAnimation(1.0f, 0.0f)
        fadeInImageAnimation.interpolator = AccelerateInterpolator()
        fadeInImageAnimation.duration = 1000 // 一秒的淡入效果
        flashImageView.startAnimation(fadeInImageAnimation) // 將快閃效果應用到 imageView

        // 快閃效果完成後跳轉到新的 Activity
        Handler(Looper.getMainLooper()).postDelayed({
            val fadeOutTextAnimation = AlphaAnimation(1.0f, 0.0f)
            fadeOutTextAnimation.interpolator = AccelerateInterpolator()
            fadeOutTextAnimation.duration = 1000 // 一秒的淡出效果


            val fadeOutImageAnimation = AlphaAnimation(1.0f, 0.0f)
            fadeOutImageAnimation.interpolator = AccelerateInterpolator()
            fadeOutImageAnimation.duration = 1000 // 一秒的淡出效果
            flashImageView.startAnimation(fadeOutImageAnimation) // 將快閃效果應用到 imageView

            // 跳轉到新的 Activity
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, IdentityActivity::class.java)
                startActivity(intent)
                finish() // 結束當前 Activity，避免返回時再次看到快閃效果
            }, 1000) // 一秒後跳轉到新的 Activity
        }, 0) // 立即開始快閃效果
    }
}