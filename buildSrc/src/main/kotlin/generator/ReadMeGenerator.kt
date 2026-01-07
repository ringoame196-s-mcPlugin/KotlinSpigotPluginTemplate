package generator

import java.io.File
import java.time.LocalDate

class ReadMeGenerator(private val projectDir:File, ctx:SetupContext) {
	private val projectName = ctx.projectName
	private val minecraftVersion = ctx.minecraftVersion
	private val projectPath = ctx.repoPath
	private val rawAccount = ctx.rawAccount

	fun generate() {
		val readMe = """
                # $projectName
                
                ## プラグイン説明
                
                ## プラグインダウンロード
                [ダウンロードリンク](https://github.com/$rawAccount/$projectName/releases/latest)
                
                ## コマンド
                | コマンド名   |     説明      | 権限 |
                | --- | ----------- | ------- |

                ## 使い方
                
                ## configファイル
                | key名   |     説明      | デフォルト値 |
                | --- | ----------- | ------- |
                 
                ## 開発環境
                - Minecraft Version : $minecraftVersion
                - Kotlin Version : 1.8.0
                
                ## プロジェクト情報
                - プロジェクトパス : $projectPath
                - 開発者名 : $rawAccount
                - 開発開始日 : ${LocalDate.now()}
            """.trimIndent()
		GeneratorUtil.makeFile(projectDir, "README.md", readMe)
	}
}