package com.youtube.spawnpk.services

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import com.youtube.spawnpk.models.Settings
import com.youtube.spawnpk.models.Video
import javafx.application.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tornadofx.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class YouTubeService : Controller() {
    private val settings = Settings.load()
    private val JSON_FACTORY = GsonFactory.getDefaultInstance()
    private val TOKENS_DIRECTORY_PATH = "tokens"
    private val SCOPES = listOf(YouTubeScopes.YOUTUBE_FORCE_SSL)

    private var youtubeService: YouTube? = null
    
    suspend fun initialize(clientSecretsPath: String): String {
        return withContext(Dispatchers.IO) {
            val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
            val clientSecrets = loadClientSecrets(clientSecretsPath)
            
            val flow = GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
            )
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build()

            flow.newAuthorizationUrl().setRedirectUri("urn:ietf:wg:oauth:2.0:oob").build()
        }
    }

    suspend fun completeAuth(authCode: String, clientSecretsPath: String) {
        withContext(Dispatchers.IO) {
            val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
            val clientSecrets = loadClientSecrets(clientSecretsPath)
            
            val flow = GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
            )
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build()

            val tokenResponse = flow.newTokenRequest(authCode)
                .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
                .execute()
            
            val credential = flow.createAndStoreCredential(tokenResponse, "user")
            
            youtubeService = YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName("SpawnPK Giveaway Automater")
                .build()
        }
    }

    private fun loadClientSecrets(clientSecretsPath: String): GoogleClientSecrets {
        return GoogleClientSecrets.load(
            JSON_FACTORY,
            InputStreamReader(FileInputStream(clientSecretsPath))
        )
    }

    suspend fun searchVideos(query: String): List<Video> {
        return withContext(Dispatchers.IO) {
            val service = youtubeService ?: throw Exception("YouTube service not initialized")
            
            val publishedAfter = DateTime(System.currentTimeMillis() - 7L * 86400000L)
            val searchResults = mutableListOf<Video>()
            val seenVideoIds = mutableSetOf<String>()
            
            val queries = listOf(
                "$query giveaway",
                "$query ga",
                "giveaway $query",
                "ga $query"
            )
            
            for (searchQuery in queries) {
                val request = service.search().list("snippet")
                val response = request.setQ(searchQuery)
                    .setType("video")
                    .setPublishedAfter(publishedAfter)
                    .setMaxResults(50L)
                    .execute()
                
                for (item in response.items) {
                    if (seenVideoIds.add(item.id.videoId)) {
                        val video = Video(
                            id = item.id.videoId,
                            title = item.snippet.title,
                            description = item.snippet.description,
                            thumbnailUrl = item.snippet.thumbnails.default.url,
                            channelTitle = item.snippet.channelTitle,
                            channelId = item.snippet.channelId
                        )
                        
                        // Check subscription status
                        val subscriptionStatus = service.subscriptions().list("snippet")
                            .setMine(true)
                            .setForChannelId(video.channelId)
                            .execute()
                        video.isSubscribed = subscriptionStatus.items?.isNotEmpty() ?: false
                        
                        // Check like status
                        val likeStatus = service.videos().getRating(video.id).execute()
                        video.isLiked = likeStatus.items.firstOrNull()?.rating == "like"
                        
                        // Check comment status
                        val commentThreads = service.commentThreads().list("snippet")
                            .setVideoId(video.id)
                            .setTextFormat("plainText")
                            .execute()
                        
                        video.hasCommented = commentThreads.items?.any {
                            it.snippet.topLevelComment.snippet.textDisplay.contains(
                                "ign: ${settings.ingameName}",
                                ignoreCase = true
                            )
                        } ?: false
                        
                        searchResults.add(video)
                    }
                }
            }
            
            searchResults
        }
    }

    suspend fun enterGiveaway(video: Video) {
        withContext(Dispatchers.IO) {
            val service = youtubeService ?: throw Exception("YouTube service not initialized")
            
            if (!video.isSubscribed) {
                val subscription = com.google.api.services.youtube.model.Subscription().apply {
                    snippet = com.google.api.services.youtube.model.SubscriptionSnippet().apply {
                        resourceId = com.google.api.services.youtube.model.ResourceId().apply {
                            kind = "youtube#channel"
                            channelId = video.channelId
                        }
                    }
                }
                service.subscriptions().insert("snippet", subscription).execute()
                video.isSubscribed = true
            }
            
            if (!video.isLiked) {
                service.videos().rate(video.id, "like").execute()
                video.isLiked = true
            }
            
            if (!video.hasCommented) {
                val commentSnippet = com.google.api.services.youtube.model.CommentSnippet().apply {
                    textOriginal = "ign: ${settings.ingameName}"
                }
                val commentThread = com.google.api.services.youtube.model.CommentThread().apply {
                    snippet = com.google.api.services.youtube.model.CommentThreadSnippet().apply {
                        videoId = video.id
                        topLevelComment = com.google.api.services.youtube.model.Comment().apply {
                            snippet = commentSnippet
                        }
                    }
                }
                service.commentThreads().insert("snippet", commentThread).execute()
                video.hasCommented = true
            }
            
            Platform.runLater {
                // Notify UI of changes
                FX.eventbus.fire(VideoUpdatedEvent(video))
            }
        }
    }
}

class VideoUpdatedEvent(val video: Video) : FXEvent() 