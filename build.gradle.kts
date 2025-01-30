plugins {
    java
    idea
    id("elasticsearch.esplugin")
    id("nebula.ospackage")
}

configure<org.elasticsearch.gradle.plugin.PluginPropertiesExtension> {
    name = "merge-script"
    description = "Adds rescorer for mixing up search hits inside their groups."
    classname = "dev.evo.elasticsearch.plugin.MergeScriptPlugin"
    extendedPlugins = listOf("lang-painless")
}

extraConfiguration()

version = Versions.project

dependencies {
    compileOnly("org.elasticsearch.plugin:elasticsearch-scripting-painless-spi:${Versions.elasticsearch}")
}
