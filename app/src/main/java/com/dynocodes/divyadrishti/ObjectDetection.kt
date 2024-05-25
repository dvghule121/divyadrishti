package com.dynocodes.divyadrishti

class ObjectDetection {


    private val positionMap = mapOf(
        "mid-left" to "to your left",
        "mid-center" to "in front of you",
        "mid-right" to "to your right",
        "top-left" to "to your top left",
        "top-center" to "above you",
        "top-right" to "to your top right",
        "bottom-left" to "below you to the left",
        "bottom-center" to "below you",
        "bottom-right" to "below you to the right"
    )

    private fun distanceCategory(w: Int, h: Int): String {
        val area = w * h
        return when {
            area < 5000 -> "very far from you"
            area < 10000 -> "far from you"
            area < 20000 -> "close to you"
            else -> "very close to you"
        }
    }

    fun generateSentence(detectedObjects: List<Detection>): String {
        val sentenceBuilder = StringBuilder("I detected ")
        detectedObjects.forEach { obj ->
            val label = obj.label
            val position = obj.position
            val bbox = obj.bbox
            val distanceDesc = distanceCategory(bbox[2], bbox[3])
            val positionDesc = positionMap[position] ?: "unknown position"
            sentenceBuilder.append("a $label $positionDesc, $distanceDesc, ")
        }
        return sentenceBuilder.toString().removeSuffix(", ") + "."
    }
}

fun main() {
    // Example usage
//    val objectDetection = ObjectDetection()
//    val detectedObjects = listOf(
//        Detection("horse", "mid-left", listOf(406, 1046, 358, 292)),
//        Detection("horse", "mid-center", listOf(797, 1063, 343, 550)),
//        Detection("horse", "mid-center", listOf(782, 1043, 369, 606)),
//        Detection("horse", "mid-center", listOf(794, 1068, 360, 642))
//    )
//    val sentence = objectDetection.generateSentence(detectedObjects)
//    println(sentence)
}
