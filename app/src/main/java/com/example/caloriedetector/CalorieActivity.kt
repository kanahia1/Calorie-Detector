package com.example.caloriedetector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caloriedetector.databinding.ActivityCalorieBinding
import com.example.caloriedetector.models.DetectedItem
import com.example.caloriedetector.utils.DetectedItemAdapter

class CalorieActivity : AppCompatActivity() {

    var totalCalories = 0

    private lateinit var binding: ActivityCalorieBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalorieBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val detectedItems = intent.getParcelableArrayListExtra<DetectedItem>("detectedItems")
        val servingSize = intent.getIntExtra("servingSize", 1)

        if (detectedItems != null) {
            for (item in detectedItems) {
                totalCalories = totalCalories + item.calorieValue
            }
        }

        binding.caloriesTextView.text = (totalCalories * servingSize).toString()
        binding.recyclerViewDetectedItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewDetectedItems.adapter = detectedItems?.let { DetectedItemAdapter(it) }
    }

}