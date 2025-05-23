package com.youtube.spawnpk.models

data class Video(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val channelId: String,
    var isSubscribed: Boolean = false,
    var isLiked: Boolean = false,
    var hasCommented: Boolean = false
) 