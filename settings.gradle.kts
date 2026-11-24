pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // COUI 17 不在 Maven Central：优先本机 ../coui/build/repository/local，
        // 外部环境或指定 -Pcoui.useRemote=true 时自动回退到 Costben/coui17 的 maven-repo 孤儿分支。
        val couiRepoProp = providers.gradleProperty("couiLocalRepo").orNull
        val forceRemoteCoui = providers.gradleProperty("coui.useRemote").orNull.equals("true", ignoreCase = true)
        val localCouiRepo = couiRepoProp?.let(::file) ?: file("../coui/build/repository/local")
        exclusiveContent {
            forRepository {
                maven {
                    if ((couiRepoProp != null || !forceRemoteCoui) && localCouiRepo.isDirectory) {
                        name = "localCouiRepo"
                        url = uri(localCouiRepo)
                    } else {
                        name = "coui17MavenRepo"
                        url = uri("https://raw.githubusercontent.com/Costben/coui17/maven-repo/coui-repo")
                    }
                }
            }
            filter {
                includeGroup("io.github.suqi8.coui.kmp")
            }
        }
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
    }
}
rootProject.name = "Xiaobu-HA-Bridge"
include(":app")
