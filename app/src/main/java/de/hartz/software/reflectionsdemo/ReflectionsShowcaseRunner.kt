package de.hartz.software.reflectionsdemo

import de.hartz.software.reflectionsdemo.showcase.DemoAction
import de.hartz.software.reflectionsdemo.showcase.DemoPlugin
import de.hartz.software.reflectionsdemo.showcase.DemoTag
import de.hartz.software.reflectionsdemo.showcase.ShowcaseFeature
import de.hartz.software.reflectionsdemo.showcase.ShowcaseNoteDocument
import io.jsondb.JsonDBConfig
import io.jsondb.JsonDBTemplate
import org.reflections.Reflections
import org.reflections.serializers.JsonSerializer
import org.reflections.serializers.XmlSerializer
import org.reflections.util.FilterBuilder
import java.io.File
import java.nio.file.Files

data class ShowcaseResult(
    val jsonSubtypes: Set<String>,
    val jsonAnnotatedTypes: Set<String>,
    val xmlSubtypes: Set<String>,
    val xmlAnnotatedMethods: Set<String>,
    val xmlAnnotatedFields: Set<String>,
    val instantiatedDescriptions: List<String>,
    val directRuntimeScanSubtypesCount: Int,
    val jsonDbCollectionDiscovered: Boolean,
    val jsonDbStoredNotes: List<String>
) {
    val isAllWorking: Boolean
        get() = jsonSubtypes == setOf(
            "de.hartz.software.reflectionsdemo.showcase.AlphaFeature",
            "de.hartz.software.reflectionsdemo.showcase.BetaFeature"
        ) &&
            xmlSubtypes == jsonSubtypes &&
            jsonAnnotatedTypes.size == 3 &&
            xmlAnnotatedMethods.size == 2 &&
            xmlAnnotatedFields.size == 2 &&
            instantiatedDescriptions.size == 2 &&
            jsonDbCollectionDiscovered &&
            jsonDbStoredNotes.isNotEmpty()

    fun formatReport(): String = buildString {
        appendLine("Reflections Android Showcase")
        appendLine("Status: ${if (isAllWorking) "ALL BUILD-TIME COLLECT & JSONDB CHECKS PASSED" else "FAILED"}")
        appendLine()
        appendLine("1. Pre-scanned JSON collect(context.classLoader):")
        appendLine("   SubTypesOf(ShowcaseFeature): ${jsonSubtypes.sorted()}")
        appendLine("   TypesAnnotatedWith(@DemoPlugin): ${jsonAnnotatedTypes.sorted()}")
        appendLine()
        appendLine("2. Pre-scanned XML collect(context.classLoader):")
        appendLine("   SubTypesOf(ShowcaseFeature): ${xmlSubtypes.sorted()}")
        appendLine("   MethodsAnnotatedWith(@DemoAction): ${xmlAnnotatedMethods.sorted()}")
        appendLine("   FieldsAnnotatedWith(@DemoTag): ${xmlAnnotatedFields.sorted()}")
        appendLine()
        appendLine("3. Instantiated via reflection (even with R8/ProGuard):")
        instantiatedDescriptions.forEach { appendLine("   - $it") }
        appendLine()
        appendLine("4. Direct runtime scan new Reflections(pkg, classLoader):")
        appendLine("   SubTypesOf(ShowcaseFeature) found = $directRuntimeScanSubtypesCount")
        appendLine("   (0 on Android ART/Dalvik because APKs contain .dex, not .class files)")
        appendLine()
        appendLine("5. JsonDB (@Document auto-discovery via gradle-reflections-plugin + JsonDBTemplate):")
        appendLine("   Collection 'showcaseNotes' discovered = $jsonDbCollectionDiscovered")
        jsonDbStoredNotes.forEach { appendLine("   - $it") }
    }
}

object ReflectionsShowcaseRunner {

    fun run(classLoader: ClassLoader, dbDirectory: File? = null): ShowcaseResult {
        val jsonReflections = Reflections.collect(
            "META-INF/reflections/",
            FilterBuilder().includePattern(".*-reflections\\.json"),
            JsonSerializer(),
            classLoader
        )

        val xmlReflections = Reflections.collect(
            "META-INF/reflections/",
            FilterBuilder().includePattern(".*-reflections\\.xml"),
            XmlSerializer(),
            classLoader
        )

        val jsonSubtypes = jsonReflections.getSubTypesOf(ShowcaseFeature::class.java)
            .mapTo(linkedSetOf()) { it.name }
        val jsonAnnotatedTypes = jsonReflections.getTypesAnnotatedWith(DemoPlugin::class.java)
            .mapTo(linkedSetOf()) { it.name }

        val xmlSubtypesClass = xmlReflections.getSubTypesOf(ShowcaseFeature::class.java)
        val xmlSubtypes = xmlSubtypesClass.mapTo(linkedSetOf()) { it.name }
        val xmlAnnotatedMethods = xmlReflections.getMethodsAnnotatedWith(DemoAction::class.java)
            .mapTo(linkedSetOf()) { "${it.declaringClass.simpleName}.${it.name}" }
        val xmlAnnotatedFields = xmlReflections.getFieldsAnnotatedWith(DemoTag::class.java)
            .mapTo(linkedSetOf()) { "${it.declaringClass.simpleName}.${it.name}" }

        val descriptions = xmlSubtypesClass
            .sortedBy { it.name }
            .map { clazz -> clazz.getDeclaredConstructor().newInstance().describe() }

        val directScanCount = runCatching {
            Reflections("de.hartz.software.reflectionsdemo.showcase", classLoader)
                .getSubTypesOf(ShowcaseFeature::class.java)
                .size
        }.getOrDefault(0)

        val ownsTempDir = dbDirectory == null
        val targetDbDir = dbDirectory ?: Files.createTempDirectory("jsondb-showcase").toFile()
        var collectionDiscovered = false
        val storedNotes = mutableListOf<String>()
        try {
            targetDbDir.mkdirs()
            val dbConfig = JsonDBConfig(
                targetDbDir.absolutePath,
                "de.hartz.software.reflectionsdemo.showcase",
                null,
                false,
                null,
                null,
                classLoader
            )
            val jsonDb = JsonDBTemplate(dbConfig)
            if (!jsonDb.collectionExists(ShowcaseNoteDocument::class.java)) {
                jsonDb.createCollection(ShowcaseNoteDocument::class.java)
            }
            collectionDiscovered = jsonDb.collectionExists(ShowcaseNoteDocument::class.java)
            jsonDb.upsert<ShowcaseNoteDocument>(
                ShowcaseNoteDocument(
                    "showcase-1",
                    "JsonDB @Document discovered on Android",
                    "gradle-reflections-plugin + Hatzen/jsondb-core"
                )
            )
            jsonDb.findAll(ShowcaseNoteDocument::class.java).forEach { note ->
                storedNotes += "${note.id}: ${note.title} (${note.source})"
            }
        } finally {
            if (ownsTempDir) {
                targetDbDir.deleteRecursively()
            }
        }

        return ShowcaseResult(
            jsonSubtypes = jsonSubtypes,
            jsonAnnotatedTypes = jsonAnnotatedTypes,
            xmlSubtypes = xmlSubtypes,
            xmlAnnotatedMethods = xmlAnnotatedMethods,
            xmlAnnotatedFields = xmlAnnotatedFields,
            instantiatedDescriptions = descriptions,
            directRuntimeScanSubtypesCount = directScanCount,
            jsonDbCollectionDiscovered = collectionDiscovered,
            jsonDbStoredNotes = storedNotes
        )
    }
}
