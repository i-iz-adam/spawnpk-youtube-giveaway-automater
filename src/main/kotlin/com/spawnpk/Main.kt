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
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.Image
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.InputStreamReader
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*

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

        // Manual Authorization: Redirect to a browser and have the user paste the code
        val authorizationUrl = flow.newAuthorizationUrl().setRedirectUri("urn:ietf:wg:oauth:2.0:oob").build()
        println("Please open the following URL in your browser to authorize the application:")
        println(authorizationUrl)

        // Prompt user to enter the authorization code manually
        print("Enter the authorization code: ")
        val code = readLine()

        val tokenResponse = flow.newTokenRequest(code).setRedirectUri("urn:ietf:wg:oauth:2.0:oob").execute()
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
        runBlocking {
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

            createAndShowGUI(youtubeService, allVideos)
        }
    }

    private suspend fun createAndShowGUI(youtubeService: YouTube, videos: List<SearchResult>) = withContext(Dispatchers.Main) {
        val frame = JFrame("YouTube Automation")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = BorderLayout()

        val panel = JPanel(GridLayout(videos.size, 1))
        val scrollPane = JScrollPane(panel)

        for (video in videos) {
            val videoPanel = JPanel(BorderLayout())
            val thumbnailLabel = JLabel()
            val videoTitleLabel = JLabel(video.snippet.title)
            val channelLabel = JLabel("Channel: ${video.snippet.channelTitle}")
            val actionButton = JButton("Process")

            // Fetch the thumbnail asynchronously
            withContext(Dispatchers.IO) {
                val thumbnailUrl = video.snippet.thumbnails.default.url
                val image = ImageIO.read(URL(thumbnailUrl)).getScaledInstance(120, 90, Image.SCALE_SMOOTH)
                thumbnailLabel.icon = ImageIcon(image)
            }

            thumbnailLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    println("Video URL: https://www.youtube.com/watch?v=${video.id.videoId}")
                }
            })

            // Check if actions are needed (like, comment, subscribe)
            val isAlreadyProcessed = checkVideoProcessed(youtubeService, video)

            if (isAlreadyProcessed) {
                actionButton.text = "Done"
                actionButton.isEnabled = false
            } else {
                actionButton.addActionListener {
                    runBlocking {
                        processVideo(youtubeService, video)
                        actionButton.text = "Done"
                        actionButton.isEnabled = false
                    }
                }
            }

            videoPanel.add(thumbnailLabel, BorderLayout.WEST)
            videoPanel.add(videoTitleLabel, BorderLayout.CENTER)
            videoPanel.add(channelLabel, BorderLayout.SOUTH)
            videoPanel.add(actionButton, BorderLayout.EAST)

            panel.add(videoPanel)
        }

        frame.add(scrollPane, BorderLayout.CENTER)
        frame.size = Dimension(600, 800)
        frame.isVisible = true
    }

    private suspend fun checkVideoProcessed(youtubeService: YouTube, video: SearchResult): Boolean = withContext(Dispatchers.IO) {
        val videoId = video.id.videoId
        val channelId = video.snippet.channelId

        // Check if subscribed to the channel
        val subscriptionStatus = youtubeService.subscriptions().list("snippet,contentDetails")
            .setMine(true)
            .setForChannelId(channelId)
            .execute()

        val isSubscribed = subscriptionStatus.items.isNotEmpty()

        // Check if the video is liked
        val likeStatus = youtubeService.videos().getRating(videoId).execute().items
            .firstOrNull { it.rating == "like" }

        val isLiked = likeStatus != null

        // Retrieve the authenticated user's channel ID
        val userChannelId = youtubeService.channels().list("id")
            .setMine(true)
            .execute()
            .items
            .firstOrNull()
            ?.id ?: return@withContext false

        // Check if the authenticated user has commented on the video
        val commentsList = youtubeService.commentThreads().list("snippet")
            .setVideoId(videoId)
            .setTextFormat("plainText")
            .execute()

        val hasComment = commentsList.items.any {
            it.snippet.topLevelComment.snippet.authorChannelId == userChannelId
        }

        isSubscribed && isLiked && hasComment
    }


    private suspend fun processVideo(youtubeService: YouTube, video: SearchResult) = withContext(Dispatchers.IO) {
        val videoId = video.id.videoId
        val channelId = video.snippet.channelId

        // Subscribe to the channel if not already subscribed
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

        // Like the video if not already liked
        val likeStatus = youtubeService.videos().getRating(videoId).execute().items
            .firstOrNull { it.rating == "like" }

        if (likeStatus == null) {
            youtubeService.videos().rate(videoId, "like").execute()
            println("Liked video: ${video.snippet.title}")
        } else {
            println("Video already liked: ${video.snippet.title}")
        }

        // Post a comment on the video
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
        println("Commented on video: ${video.snippet.title}")
    }
}
