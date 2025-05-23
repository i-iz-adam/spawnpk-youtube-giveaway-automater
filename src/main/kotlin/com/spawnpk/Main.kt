package com.youtube.spawnpk

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import com.google.api.services.youtube.model.*
import java.io.File
import java.io.InputStreamReader
import java.util.*

object Main {
    private const val APPLICATION_NAME = "YouTube Automation"
    private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
    private const val TOKENS_DIRECTORY_PATH = "tokens"
    private val SCOPES = listOf(YouTubeScopes.YOUTUBE_FORCE_SSL)
    private const val CREDENTIALS_FILE_PATH = "/client_secrets_2nd.json"


    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
        val inputStream = Main::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
            ?: throw Exception("Resource not found: $CREDENTIALS_FILE_PATH")
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inputStream))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()

        // Try to load existing credentials
        val credential = flow.loadCredential("user")
        if (credential != null && credential.refreshToken() && credential.accessToken != null) {
            return credential
        }

        // Use a local redirect URI for automatic browser-based OAuth
        val redirectUri = "http://localhost:8888/callback"
        val authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build()
        println("Opening the browser for authorization...")
        java.awt.Desktop.getDesktop().browse(java.net.URI(authorizationUrl))

        // Start a simple HTTP server to listen for the OAuth callback
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(8888), 0)
        var code: String? = null
        val lock = Object()
        server.createContext("/callback") { exchange ->
            val query = exchange.requestURI.query
            val params = query?.split("&")?.associate {
                val (k, v) = it.split("=")
                k to v
            } ?: emptyMap()
            code = params["code"]
            val response = "Authorization successful! You can close this window."
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
            synchronized(lock) { lock.notify() }
        }
        server.start()
        synchronized(lock) {
            while (code == null) lock.wait()
        }
        server.stop(0)

        val tokenResponse = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute()
        return flow.createAndStoreCredential(tokenResponse, "user")
    }

    private fun searchVideos(youtubeService: YouTube, query: String, publishedAfter: DateTime): List<SearchResult> {
        val request = youtubeService.search().list("snippet")
        val response = request.setQ(query)
            .setType("video")
            .setPublishedAfter(publishedAfter)
            .setMaxResults(50L) // You can increase this, but YouTube API limits to 50
            .execute()

        return response.items
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()

        val youtubeService = YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
            .setApplicationName(APPLICATION_NAME)
            .build()

        // Set the date range for the search to the last week
        val publishedAfter = DateTime(System.currentTimeMillis() - 7L * 86400000L)

        // Perform multiple queries
        val queries = listOf(
            "spawnpk giveaway",
            "spawnpk ga",
            "giveaway spawnpk",
            "ga spawnpk",
            "spawnpk collection log",
            "collection log spawnpk"
        )

        val allVideos = mutableListOf<SearchResult>()
        val seenVideoIds = mutableSetOf<String>()

        for (query in queries) {
            val videos = searchVideos(youtubeService, query, publishedAfter)
            for (video in videos) {
                if (seenVideoIds.add(video.id.videoId)) {
                    allVideos.add(video)
                }
            }
        }

        println("Found ${allVideos.size} unique videos")

        for (video in allVideos) {
            try {
                val videoId = video.id.videoId
                val videoTitle = video.snippet.title
                val channelId = video.snippet.channelId

                if (!videoTitle.contains("spawnpk", true) && !videoTitle.contains(
                        "spawn pk",
                        true
                    ) && !videoTitle.contains("spawnpk", true)
                )
                    continue;
                println("Checking video: $videoTitle")

                val subscriptionStatus = youtubeService.subscriptions().list("snippet,contentDetails")
                    .setMine(true)
                    .setForChannelId(channelId)
                    .execute()

                if (subscriptionStatus.items.isEmpty()) {
                    val subscription = Subscription().apply {
                        snippet = SubscriptionSnippet().apply {
                            resourceId = ResourceId().apply {
                                kind = "youtube#channel"
                                this.channelId = channelId
                            }
                        }
                    }
                    youtubeService.subscriptions().insert("snippet", subscription).execute()
                    println("Subscribed to channel: ${video.snippet.channelTitle}")
                } else {
                    println("Already subscribed to channel: ${video.snippet.channelTitle}")
                }

                // Check if the video has been liked or commented on
                val likeStatus = youtubeService.videos().getRating(videoId).execute().items
                    .firstOrNull { it.rating == "like" }

                if (likeStatus == null) {
                    // Like the video
                    youtubeService.videos().rate(videoId, "like").execute()
                    println("Liked video: $videoTitle")

                    // Comment on the video
                    val commentSnippet = CommentSnippet().apply {
                        textOriginal = "ign: rsps guru"
                    }
                    val commentThread = CommentThread().apply {
                        snippet = CommentThreadSnippet().apply {
                            this.videoId = videoId // Ensure the videoId is set here
                            topLevelComment = Comment().apply {
                                snippet = commentSnippet
                            }
                        }
                    }
                    youtubeService.commentThreads().insert("snippet", commentThread).execute()
                    println("Commented on video: $videoTitle")
                } else {
                    println("Video already liked/commented: $videoTitle")
                }
                println(" - ")
                println(" - ")
            } catch (ex: Exception) {
                println("Error on video ${video.snippet.title}, ${ex.message}")
            }
        }
    }
}
