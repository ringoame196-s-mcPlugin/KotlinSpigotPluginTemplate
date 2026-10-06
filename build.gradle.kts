import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import java.net.HttpURLConnection
import java.net.URL

plugins {
    kotlin("jvm") version "1.8.0"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1"
    id("com.github.ben-manes.versions") version "0.41.0"
    id("com.palantir.git-version") version "0.12.3"
    id("dev.s7a.gradle.minecraft.server") version "1.2.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jmailen.kotlinter") version "3.8.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

val mcVersion: String by project
val pluginVersion: String by project

// 表示用の最終バージョン
val fullVersion = "$mcVersion-$pluginVersion"
version = fullVersion

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/groups/public/")
    // Paper API
    maven("https://repo.papermc.io/repository/maven-public/")
}

val shadowImplementation: Configuration by configurations.creating
configurations["implementation"].extendsFrom(shadowImplementation)

dependencies {
    shadowImplementation(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:$mcVersion-R0.1-SNAPSHOT")
}

detekt {
    // 構文チェックの対象バージョンを設定（Kotlinのバージョンに合わせる）
    toolVersion = "1.23.6"

    // ソースファイルの指定
    source.setFrom("src/main/kotlin", "src/main/java")

    // デフォルトの設定ルールを使用
    buildUponDefaultConfig = true

    // カスタムルールファイルを指定する場合（後述で生成）
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))

    // 問題検出時にビルドを失敗させるかどうか（最初は false にして徐々に直すのがおすすめ）
    ignoreFailures = false
}

configure<BukkitPluginDescription> {
    main = "@group@.Main"
    version = fullVersion
    apiVersion = "1." + mcVersion.split(".")[1]
    author = "@author@"
    website = "@website@"
    /*
    コマンド追加用
    commands {
        register("test") {
            description = "This is a test command!"
            aliases = listOf("t")
            permission = "testplugin.test"
            usage = "Just run the command!"
        }
    }
    */

    /*
    パーミッション追加用
    permissions {
        register("test.test") {
            description = "This is a test permission!"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
    */
}

tasks.withType<ShadowJar> {
    configurations = listOf(shadowImplementation)
    archiveClassifier.set("")
    relocate("kotlin", "@group@.libs.kotlin")
    relocate("org.intellij.lang.annotations", "@group@.libs.org.intellij.lang.annotations")
    relocate("org.jetbrains.annotations", "@group@.libs.org.jetbrains.annotations")
}

val deployPlugin by tasks.registering {
    group = "deployment"
    description = "ビルドされたJARをサーバープラグインフォルダへコピーし、通知APIを呼び出します"

    // shadowJarの成果物出力後に実行する
    dependsOn("shadowJar")

    val copyDirPath = "Z:/minecraft/TwitterServer/plugins/"
    val targetDir = file(copyDirPath)

    // タスク実行時の処理
    doLast {
        if (!targetDir.exists() || !targetDir.isDirectory) {
            logger.warn("自動コピーをスキップしました: ディレクトリが存在しません (${targetDir.path})")
            return@doLast
        }

        // JARファイルのコピー
        val jarFile = layout.buildDirectory.file("libs/${project.name}-$fullVersion.jar").get().asFile
        if (jarFile.exists()) {
            copy {
                from(jarFile)
                into(targetDir)
            }
            logger.lifecycle("プラグインをコピーしました: ${jarFile.name} -> ${targetDir.path}")
        } else {
            logger.error("コピー対象のJARファイルが見つかりません: ${jarFile.path}")
            return@doLast
        }

        // サーバーへのリロード通知API呼び出し
        val port = 25585
        val ip = "ringoame-server"
        val apiUrl = "http://$ip:$port/plugin?name=${project.name}"

        try {
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                logger.lifecycle("API Response: $response")
            } else {
                logger.warn("Server responded with error code: ${connection.responseCode}")
            }
            connection.disconnect()
        } catch (e: java.net.ConnectException) {
            logger.warn("Warning: サーバーに接続できません（オフラインの可能性があります）")
        } catch (e: java.net.SocketTimeoutException) {
            logger.warn("Warning: 接続がタイムアウトしました")
        } catch (e: Exception) {
            logger.warn("Warning: API通信で予期しないエラーが発生しました: ${e.message}")
        }
    }
}

tasks.named("build") {
    finalizedBy(deployPlugin)
}

tasks.named("printVersion") {
    doLast {
        println(fullVersion)
    }
}

task<SetupTask>("setup")
