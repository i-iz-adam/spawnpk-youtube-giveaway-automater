package com.youtube.spawnpk

import com.youtube.spawnpk.views.MainView
import tornadofx.App
import tornadofx.launch

class SpawnPKApp : App(MainView::class)

fun main(args: Array<String>) {
    launch<SpawnPKApp>(args)
} 