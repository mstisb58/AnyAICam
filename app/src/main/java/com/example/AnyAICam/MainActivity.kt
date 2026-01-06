//MainActivity.kt
package com.example.AnyAICam

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.AnyAICam.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, CameraFragment())
                .commitNow()
        }
    }

    fun navigateToPreview() {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, PreviewFragment())
            .addToBackStack(null) // バックキーでカメラ画面に戻れるようにする
            .commit()
    }

    fun navigateBackToCamera() {
        supportFragmentManager.popBackStack()
    }
}