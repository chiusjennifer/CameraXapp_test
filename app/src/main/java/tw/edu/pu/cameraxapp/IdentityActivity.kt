package tw.edu.pu.cameraxapp

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast

class IdentityActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity)
        val button:Button=findViewById(R.id.button)
        val spinnerIdentity:Spinner=findViewById(R.id.spinner)
        val lunch = arrayListOf("邱家妤","吳唯硯","李欣遙","鍾依真")
        ArrayAdapter.createFromResource(
            this,
            R.array.identity,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerIdentity.adapter=adapter
        }
        spinnerIdentity.onItemSelectedListener = object :AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)=  // 在这里处理选中项的逻辑
                Toast.makeText(this@IdentityActivity, "你選擇的是:" + lunch[pos], Toast.LENGTH_LONG).show()

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }
        button.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}