package com.youtube.spawnpk.views

import com.youtube.spawnpk.models.Settings
import com.youtube.spawnpk.models.Video
import com.youtube.spawnpk.services.YouTubeService
import com.youtube.spawnpk.services.VideoUpdatedEvent
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.stage.FileChooser
import kotlinx.coroutines.*
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class MainView : View("SpawnPK YouTube Giveaway Automater") {
    private val settings = Settings.load()
    private val searchQuery = SimpleStringProperty("spawnpk")
    private val videos = mutableListOf<Video>().asObservable()
    private val youtubeService: YouTubeService by inject()
    private val coroutine = CoroutineScope(Dispatchers.Main + Job())
    
    init {
        subscribe<VideoUpdatedEvent> { event ->
            val index = videos.indexOfFirst { it.id == event.video.id }
            if (index != -1) {
                videos[index] = event.video
            }
        }
    }
    
    override val root = borderpane {
        style {
            backgroundColor += Color.rgb(30, 30, 30)
            padding = box(10.px)
        }

        top = vbox(10) {
            style {
                padding = box(10.px)
                backgroundColor += Color.rgb(40, 40, 40)
            }

            if (settings.clientSecretsPath.isEmpty()) {
                hbox(10) {
                    alignment = Pos.CENTER
                    label("Select client_secrets.json file:") {
                        style { textFill = Color.WHITE }
                    }
                    button("Choose File") {
                        style {
                            backgroundColor += Color.rgb(70, 70, 200)
                            textFill = Color.WHITE
                        }
                        action {
                            val files = chooseFile(
                                "Select client_secrets.json",
                                arrayOf(FileChooser.ExtensionFilter("JSON files", "*.json")),
                                mode = FileChooserMode.Single
                            )
                            if (files.isNotEmpty()) {
                                settings.clientSecretsPath = files.first().absolutePath
                                settings.save()

                                coroutine.launch {
                                    try {
                                        val authUrl = youtubeService.initialize(settings.clientSecretsPath)
                                        hostServices.showDocument(authUrl)
                                        
                                        val dialog = Alert(Alert.AlertType.CONFIRMATION).apply {
                                            title = "Authorization Required"
                                            headerText = "Please enter the authorization code from the browser:"
                                            buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
                                            
                                            val codeInput = textfield()
                                            dialogPane.content = vbox {
                                                label("Please enter the authorization code:")
                                                add(codeInput)
                                                style {
                                                    padding = box(10.px)
                                                    backgroundColor += Color.rgb(40, 40, 40)
                                                    textFill = Color.WHITE
                                                }
                                            }
                                            
                                            dialogPane.lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION) { event ->
                                                if (codeInput.text.isNullOrBlank()) {
                                                    event.consume()
                                                }
                                            }
                                        }
                                        
                                        val result = dialog.showAndWait()
                                        if (result.isPresent && result.get() == ButtonType.OK) {
                                            val codeInput = (dialog.dialogPane.content as javafx.scene.layout.VBox)
                                                .children.filterIsInstance<javafx.scene.control.TextField>()
                                                .firstOrNull()?.text
                                            
                                            if (!codeInput.isNullOrBlank()) {
                                                youtubeService.completeAuth(codeInput, settings.clientSecretsPath)
                                                performSearch()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        alert(Alert.AlertType.ERROR, "Error", e.message ?: "An error occurred")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (settings.ingameName.isEmpty()) {
                form {
                    fieldset {
                        field("In-game Name") {
                            textfield {
                                promptText = "Enter your in-game name"
                                style {
                                    backgroundColor += Color.rgb(50, 50, 50)
                                    textFill = Color.WHITE
                                }
                                action {
                                    settings.ingameName = text
                                    settings.save()
                                    this@fieldset.removeFromParent()
                                    performSearch()
                                }
                            }
                        }
                    }
                }
            }

            hbox(10) {
                alignment = Pos.CENTER
                textfield(searchQuery) {
                    promptText = "Search videos..."
                    hgrow = Priority.ALWAYS
                    style {
                        backgroundColor += Color.rgb(50, 50, 50)
                        textFill = Color.WHITE
                    }
                }
                button("Search") {
                    style {
                        backgroundColor += Color.rgb(70, 70, 200)
                        textFill = Color.WHITE
                    }
                    action {
                        performSearch()
                    }
                }
            }
        }

        center = scrollpane {
            style {
                backgroundColor += Color.rgb(30, 30, 30)
            }
            isFitToWidth = true
            
            vbox(10) {
                padding = Insets(10.0)
                
                bindChildren(videos) { video ->
                    vbox(10) {
                        style {
                            padding = box(10.px)
                            backgroundColor += Color.rgb(40, 40, 40)
                        }
                        
                        hbox(10) {
                            imageview(video.thumbnailUrl) {
                                fitWidth = 120.0
                                fitHeight = 90.0
                                isPreserveRatio = true
                                setOnMouseClicked {
                                    hostServices.showDocument("https://www.youtube.com/watch?v=${video.id}")
                                }
                            }
                            
                            vbox(5) {
                                hgrow = Priority.ALWAYS
                                
                                label(video.title) {
                                    style {
                                        textFill = Color.WHITE
                                        fontSize = 14.px
                                        fontWeight = FontWeight.BOLD
                                    }
                                    setOnMouseClicked {
                                        hostServices.showDocument("https://www.youtube.com/watch?v=${video.id}")
                                    }
                                }
                                
                                label(video.channelTitle) {
                                    style {
                                        textFill = Color.LIGHTGRAY
                                        fontSize = 12.px
                                    }
                                }
                                
                                hbox(10) {
                                    label("Subscribed: ") {
                                        style { textFill = Color.LIGHTGRAY }
                                    }
                                    label(if (video.isSubscribed) "✓" else "✗") {
                                        style {
                                            textFill = if (video.isSubscribed) Color.GREEN else Color.RED
                                        }
                                    }
                                    
                                    label("Liked: ") {
                                        style { textFill = Color.LIGHTGRAY }
                                    }
                                    label(if (video.isLiked) "✓" else "✗") {
                                        style {
                                            textFill = if (video.isLiked) Color.GREEN else Color.RED
                                        }
                                    }
                                    
                                    label("Commented: ") {
                                        style { textFill = Color.LIGHTGRAY }
                                    }
                                    label(if (video.hasCommented) "✓" else "✗") {
                                        style {
                                            textFill = if (video.hasCommented) Color.GREEN else Color.RED
                                        }
                                    }
                                }
                                
                                button("Enter Giveaway") {
                                    style {
                                        backgroundColor += Color.rgb(70, 70, 200)
                                        textFill = Color.WHITE
                                    }
                                    isDisable = video.isSubscribed && video.isLiked && video.hasCommented
                                    action {
                                        coroutine.launch {
                                            try {
                                                youtubeService.enterGiveaway(video)
                                            } catch (e: Exception) {
                                                alert(Alert.AlertType.ERROR, "Error", e.message ?: "An error occurred")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        titledpane("Description") {
                            isExpanded = false
                            style {
                                backgroundColor += Color.rgb(40, 40, 40)
                                textFill = Color.WHITE
                            }
                            
                            textarea(video.description) {
                                isEditable = false
                                isWrapText = true
                                style {
                                    backgroundColor += Color.rgb(50, 50, 50)
                                    textFill = Color.WHITE
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun performSearch() {
        if (settings.ingameName.isEmpty() || settings.clientSecretsPath.isEmpty()) {
            return
        }

        coroutine.launch {
            try {
                videos.setAll(youtubeService.searchVideos(searchQuery.value))
            } catch (e: Exception) {
                alert(Alert.AlertType.ERROR, "Error", e.message ?: "An error occurred")
            }
        }
    }
    
    override fun onDelete() {
        super.onDelete()
        coroutine.cancel()
    }
} 