package snd.komf.providers.german.model

enum class DataSource(val label: String, val priority: Int) {
    MANGAPASSION_DE("Manga Passion DE", 30),
    WIKIPEDIA_DE("Wikipedia DE", 50),
    MANGADEX_DE("MangaDex DE", 60),
}
