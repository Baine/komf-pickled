package snd.komf.providers.specyaml

import java.io.File

class SpecYAMLFileReader {
    fun readText(filePath: String): String? {
        val file = File(filePath)
        return if (file.exists() && file.isFile) file.readText() else null
    }

    fun exists(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && file.isFile
    }

    fun listYamlFiles(dirPath: String): List<String> {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { _, name -> name.endsWith(".yaml") || name.endsWith(".yml") }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    fun nameWithoutExtension(filePath: String): String {
        val name = File(filePath).name
        return name.substringBeforeLast(".")
    }
}