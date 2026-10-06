# 2026-10-06 Detekt 導入および CI ワークフローの最適化

## 概要
コード品質の自動検証とマージ作業の効率化を目的として、プロジェクトに **Detekt** によるコード静的解析を導入しました。また、GitHub Actions における Pull Request の自動マージ（Auto Merge）ロジックを再設計し、個人リポジトリおよび Organization リポジトリの両環境に対応させました。

---

## 🛠️ 主な変更点

### 1. Detekt によるコード静的解析の追加
* Gradle に Detekt タスクを追加し、CI 実行時にコード品質違反や潜在的な不具合を自動検出する仕組みを整備しました。
* プラグイン固有のコード記述（大文字を含むパッケージ名、長いイベントハンドラ等）に対応するため、`config/detekt/detekt.yml` のルールセット（パッケージ命名規則、1行の制限文字数、Return数など）を調整しました。
* ビルドパフォーマンス維持のため、`./gradlew detekt lintKotlin build --parallel --build-cache` を一括実行するように設定しました。

### 2. CI 自動マージロジックの汎用化・セキュリティ強化
* **アカウント直書きの廃止**:
  従来のアカウント名直書き判定（`github.event.pull_request.user.login == '...'`）を廃止し、GitHub の権限属性 (`author_association`) による判定へ移行しました。
* **マルチ環境対応**:
  PR 作成者が `OWNER`（所有者）、`MEMBER`（組織メンバー）、`COLLABORATOR`（協力者）のいずれかである場合に自動マージされるよう設定し、個人/Organization を問わず同一の設定で動作可能にしました。
* **不正実行の防止**:
  外部フォークからの PR (`head.repo.full_name != github.repository`) や下書き PR (`draft == true`) の場合は自動マージが発動しない安全設計にしています。

---

## 📋 定義済み CI ワークフロー (`.github/workflows/lint-build.yml`)

```yaml
name: Kotlin Lint, Detekt & Build Check

on:
  pull_request:
    branches:
      - main
      - master
    paths:
      - 'src/**/*.kt' # Kotlinファイル変更時のみ発動

jobs:
  build:
    if: github.event.pull_request.draft == false # Draft PRは除外
    runs-on: ubuntu-latest

    permissions:
      contents: write
      pull-requests: write

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Grant execute permission
        run: chmod +x ./gradlew

      # --- 🛡 静的解析・ビルドチェック ---
      - name: Detekt, Lint & Build
        run: |
          ./gradlew detekt lintKotlin build --parallel --build-cache --no-daemon

      # --- 🚀 Auto Merge ---
      - name: Auto Merge (Rebase / Merge)
        if: |
          success() && 
          github.event.pull_request.head.repo.full_name == github.repository && 
          (github.event.pull_request.author_association == 'OWNER' || github.event.pull_request.author_association == 'MEMBER' || github.event.pull_request.author_association == 'COLLABORATOR')
        run: |
          curl -L \
            -X PUT \
            -H "Authorization: Bearer ${{ secrets.GITHUB_TOKEN }}" \
            -H "Accept: application/vnd.github+json" \
            [https://api.github.com/repos/$](https://api.github.com/repos/$){{ github.repository }}/pulls/${{ github.event.pull_request.number }}/merge \
            -d '{"merge_method": "merge"}'
   ```
## 💻 開発者向けガイダンス
ローカルでの自動修正（Auto-correct）:
ローカル環境でスタイルの違反などを自動修正したい場合は、以下のコマンドを実行します。
```bash
  ./gradlew detekt --auto-correct
```
(※ IntelliJ IDEA の「実行/デバッグ構成」で Gradle タスクとして保存して使うことも可能です)