package com.example.caloriedetector.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.caloriedetector.R
import com.example.caloriedetector.models.DetectedItem

class DetectedItemAdapter(private val detectedItems: List<DetectedItem>) :
    RecyclerView.Adapter<DetectedItemAdapter.DetectedItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectedItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detected_item, parent, false)
        return DetectedItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetectedItemViewHolder, position: Int) {
        val detectedItem = detectedItems[position]
        holder.tvItemName.text = detectedItem.clsName
        holder.tvCalorieValue.text = "${detectedItem.calorieValue} kcal"
    }

    override fun getItemCount(): Int = detectedItems.size

    inner class DetectedItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
        val tvCalorieValue: TextView = itemView.findViewById(R.id.tvCalorieValue)
    }
}
