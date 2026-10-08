# BaseCommand と SubCommand によるコマンド設計の導入

## 概要
プラグインのコマンド拡張性と保守性を向上させるため、親コマンドによるルーティングとサブコマンド単位のクラス分離を行う設計パターンを導入しました。
これに伴い、`SrcGenerator` に `BaseCommand` および `SubCommand` インターフェースの自動生成ロジックを追加しました。

---

## 主な変更点

### 1. `BaseCommand` の追加
* `CommandExecutor` と `TabCompleter` を実装した抽象クラス。
* サブコマンドの実行ルーティング処理を一元化。
* 入力中の引数（`args.last()`）に対する前方一致（`startsWith`）でのタブ補完フィルタリングを一括適用。

### 2. `SubCommand` インターフェースの導入
* サブコマンドごとの独立したクラス化（1サブコマンド = 1クラス）を規定。
* `execute`（実行処理）と `tabComplete`（補完候補リスト返却）を定義。
* サブコマンド側ではフィルタリングを意識せず、単純に全候補リストを返すだけの設計に簡略化。

### 3. `SrcGenerator` への統合
プロジェクト生成時に以下の構造が自動的にセットアップされるよう変更：
* `commands/SubCommand.kt`（インターフェース）
* `commands/BaseCommand.kt`（共通基底クラス）
* `commands/MainCommand.kt`（親コマンドクラス実装）
* `commands/sub/ExampleSubCommand.kt`（サンプルサブコマンド実装）
* `Main.kt` での `MainCommand` の自動登録設定

---

## ディレクトリ構造

```text
src/main/kotlin/com/example/
├── Main.kt
├── commands/
│   ├── SubCommand.kt
│   ├── BaseCommand.kt
│   ├── MainCommand.kt
│   └── sub/
│       └── ExampleSubCommand.kt
```

---

## サブコマンド追加手順

1. `commands/sub/` 内に `SubCommand` を実装した新クラスを作成する。
2. `tabComplete` では対象引数位置における補完候補のリストを返す（フィルター処理は不要）。
3. `MainCommand`（または対象の親コマンド）の `subCommands` マップに登録する。

```kotlin
class MainCommand : BaseCommand() {
    override val subCommands: Map<String, SubCommand> = listOf(
        ExampleSubCommand(),
        NewSubCommand() // 追加
    ).associateBy { it.name }
}
```