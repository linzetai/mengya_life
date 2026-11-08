package com.mengyalife.app.data.care

/** 通知投递门：恢复维护中或代次变化时不得投递。 */
object CareDeliveryGate {
    fun canDeliver(barrierReady: Boolean, startGeneration: Long, currentGeneration: Long): Boolean =
        barrierReady && startGeneration == currentGeneration
}
