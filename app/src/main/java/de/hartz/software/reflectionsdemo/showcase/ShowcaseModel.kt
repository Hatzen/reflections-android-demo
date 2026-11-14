package de.hartz.software.reflectionsdemo.showcase

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DemoPlugin(val id: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DemoAction(val label: String)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DemoTag(val tag: String)

interface ShowcaseFeature {
    fun id(): String
    fun describe(): String
}

@DemoPlugin(id = "alpha")
class AlphaFeature : ShowcaseFeature {
    @DemoTag(tag = "primary")
    @JvmField
    var category: String = "core"

    override fun id(): String = "alpha"

    @DemoAction(label = "execute-alpha")
    override fun describe(): String = "AlphaFeature loaded & instantiated (category=$category)"
}

@DemoPlugin(id = "beta")
class BetaFeature : ShowcaseFeature {
    @DemoTag(tag = "secondary")
    @JvmField
    var category: String = "extension"

    override fun id(): String = "beta"

    @DemoAction(label = "execute-beta")
    override fun describe(): String = "BetaFeature loaded & instantiated (category=$category)"
}

@DemoPlugin(id = "extra")
class ExtraAnnotatedComponent

