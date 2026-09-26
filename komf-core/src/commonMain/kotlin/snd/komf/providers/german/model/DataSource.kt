package snd.komf.providers.german.model

enum class DataSource(val label: String, val priority: Int) {
    MANGAPASSION_DE("Manga Passion DE", 30),
    MANGADEX_DE("MangaDex DE", 60),
}
