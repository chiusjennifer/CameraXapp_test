package tw.edu.pu.cameraxapp

import android.graphics.RectF

data class Recognition(
    var labelId: Int =0,
    var labelName: String? = null,
    var labelScore: Float? = null,
    var confidence: Float,
    var location: RectF
) {
    /*
    fun getLabelId():Int{
        return labelId
    }
    fun getLabelName():String?{
        return labelName
    }
    fun getLabelScore():Float?{
        return labelScore
    }
    fun getConfidence():Float?{
        return confidence
    }
    fun getLocation(): RectF? {
        return location?.let { RectF(it) }
    }
    fun setLocation(location: RectF) {
        this.location = location
    }

    fun setLabelName(labelName: String) {
        this.labelName = labelName
    }

//    fun setLabelId(labelId: Int) {
//        this.labelId = labelId
//    }

    fun setLabelScore(labelScore: Float?) {
        this.labelScore = labelScore
    }

    fun setConfidence(confidence: Float) {
        this.confidence = confidence
    }
*/
    override fun toString(): String {
        var resultString = "$labelId "

        if (labelName != null) {
            resultString += "$labelName "
        }

        if (confidence != null) {
            resultString += String.format("(%.1f%%) ", confidence!! * 100.0f)
        }

        if (location != null) {
            resultString += "$location "
        }

        return resultString.trim()
    }
}