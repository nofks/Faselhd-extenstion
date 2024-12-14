version = 1
plugins {
    id("com.lagradost.cloudstream3.gradle") version "1.0"
}
cloudstream {
    language = "ar"
    description = "Fasel HD Arabic provider"
    authors = listOf("YourName")
    status = 1
    tvTypes = listOf("Movies", "TvSeries")
    repositoryUrl = "https://github.com/YourUsername/cloudstream-faselhds"
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
}
