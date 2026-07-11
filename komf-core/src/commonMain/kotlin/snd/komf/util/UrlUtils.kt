package snd.komf.util

import io.ktor.http.URLBuilder
import io.ktor.http.Url

// ponytail: Ktor URLBuilder.buildString() performs the URL encoding this file used to hand-roll
fun Url.toStingEncoded(): String = URLBuilder(this).buildString()
