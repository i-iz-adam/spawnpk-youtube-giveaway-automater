# YouTube Automation Project

This project is a Kotlin-based application that interacts with the YouTube Data API. It can search for videos with specific keywords, like videos, post comments, and subscribe to channels automatically. This README will guide you through setting up the project, configuring your own Google Cloud project, and running the application.

## Features

- 🔍 **Search YouTube** for videos based on specific keywords.
- 👍 **Automatically like** videos.
- 💬 **Post comments** on videos.
- 📺 **Subscribe** to YouTube channels.
- 🛡️ **Automatic OAuth 2.0 authentication** (no more copy-paste).
- 🗃️ **Token storage** for seamless future runs.

## Prerequisites

- Java 8 or later
- Kotlin 1.9 or later
- Gradle 6.0 or later
- A Google account with access to [Google Cloud Console](https://console.cloud.google.com/)

## Setup

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/your-repository.git
cd your-repository
```

### 2. Create a Google Cloud Project

1. **Go to Google Cloud Console**: Visit [Google Cloud Console](https://console.cloud.google.com/).

2. **Create a New Project**:
   - Click on the project drop-down at the top of the page.
   - Select "New Project."
   - Give your project a name and click "Create."

3. **Enable the YouTube Data API**:
   - From the left-hand menu, navigate to "APIs & Services" > "Library."
   - Search for "YouTube Data API v3."
   - Click on it and then click "Enable."

4. **Create OAuth 2.0 Credentials**:
   - Go to "APIs & Services" > "Credentials" in the left-hand menu.
   - Click "Create Credentials" and select "OAuth 2.0 Client IDs."
   - You may need to configure the consent screen first. Follow the instructions to set it up.
   - For the application type, select "Desktop app."
   - After creating the credentials, click "Download" to obtain the `client_secrets.json` file.

5. **Place the `client_secrets.json` File**:
   - Move the downloaded `client_secrets.json` file into the `src/main/resources` directory of your project.

### 3. Configure OAuth 2.0 Credentials

The project now uses an automatic OAuth 2.0 flow. When you run the application, it will open your browser for authentication. After you grant access, you can simply close the browser tab. No need to copy-paste any code.

- The authorization tokens will be saved for future use in the `tokens` directory.

### 4. Build and Run the Project

1. **Build the Project**:

   ```bash
   ./gradlew build
   ```

2. **Run the Project**:

   ```bash
   ./gradlew run
   ```

### 5. How the Application Works

- On the first run, the application will open your browser to authorize the app. After granting access, you can close the browser tab.
- The application will search for videos based on predefined keywords, like them, comment on them, and subscribe to channels if you're not already subscribed.

## Troubleshooting

- **Authorization Issues**: If you encounter issues with OAuth authorization, try deleting the `tokens` directory and rerunning the application to generate a new authorization token.
- **API Quotas**: Ensure that your usage of the YouTube Data API stays within the free tier limits. Monitor your quota usage in the Google Cloud Console.

## Security

- **Sensitive Information**: Ensure that your `client_secrets.json` and OAuth tokens are not committed to your repository. The `.gitignore` file included in this project excludes these files by default.

## Contributing

If you wish to contribute to this project, feel free to open a pull request or submit issues for any bugs or feature requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
