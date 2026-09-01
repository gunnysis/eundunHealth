pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MSAL 의 전이 의존성 com.microsoft.device.display:display-mask(Surface Duo SDK)는
        // Maven Central·Google Maven 어디에도 없다(둘 다 404 실측). 이 피드에만 있다(200).
        // 저장소를 통째로 열지 않고 **이 그룹만** 여기서 찾도록 content 로 좁힌다 —
        // 나머지 의존성은 계속 mavenCentral 에서만 해석되므로 공급망 표면이 늘지 않는다.
        maven("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1") {
            content { includeGroup("com.microsoft.device.display") }
        }
    }
}

rootProject.name = "eundunHealth"
include(":app")
