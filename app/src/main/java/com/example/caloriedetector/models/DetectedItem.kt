package com.example.caloriedetector.models

import android.os.Parcel
import android.os.Parcelable

data class DetectedItem(
    val clsName: String,
    val calorieValue: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(clsName)
        parcel.writeInt(calorieValue)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<DetectedItem> {
        override fun createFromParcel(parcel: Parcel): DetectedItem {
            return DetectedItem(parcel)
        }

        override fun newArray(size: Int): Array<DetectedItem?> {
            return arrayOfNulls(size)
        }
    }
}
