# 2026-10-06 Detekt 導入・CI ワークフロー最適化およびバージョン自動生成の修正

## 概要
コード品質の自動検証とマージ作業の効率化を目的として、プロジェクトに **Detekt** によるコード静的解析を導入しました。また、Pull Request の自動マージ（Auto Merge）ロジックの汎用化および、`ver.txt` 自動生成ワークフローのトリガー最適化を実施しました。

---

## 🛠️ 主な変更点

### 1. Detekt によるコード静的解析の追加
* Gradle に Detekt タスクを追加し、CI 実行時にコード品質違反や潜在的な不具合を自動検出する仕組みを整備しました。
* プラグイン固有のコード記述に対応するため、`config/detekt/detekt.yml` のルールセット（パッケージ命名規則、1行の制限文字数、Return数など）を調整しました。
* `./gradlew detekt lintKotlin build --parallel --build-cache` を一括実行し、ビルド検証を効率化しました。

### 2. CI 自動マージロジックの汎用化・セキュリティ強化
* **アカウント直書きの廃止**:
  従来のアカウント名直書き判定を廃止し、GitHub の権限属性 (`author_association`) による判定へ移行しました。
* **マルチ環境対応**:
  PR 作成者が `OWNER`（所有者）、`MEMBER`（組織メンバー）、`COLLABORATOR`（協力者）のいずれかである場合に自動マージされるよう設定し、個人/Organization を問わず同一設定で動作可能にしました。
* **フォーク保護**:
  外部フォークからの PR (`head.repo.full_name != github.repository`) や下書き PR (`draft == true`) では自動マージが発動しない安全設計にしています。

### 3. バージョンテキスト自動生成ワークフロー (`generate-version.yml`) の修正
* **トリガーおよび判定の最適化**:
  トリガーを `on: push`（`main`/`master` ブランチへの push および PR マージ時）に変更しました。
* **スキップバグの解消**:
  `push` イベント発生時に `github.event.pull_request` オブジェクトが存在しないことでジョブが `Skipped` になる問題を防ぐため、不要な `if: github.event.pull_request.merged == true` 条件を削除しました。
* **フォーク時の 403 エラー回避**:
  フォーク経由の PR 時に `GITHUB_TOKEN` の書き込み権限が制限されて発生していた Git Push（403 Forbidden）エラーを構造的に回避しました。

---

## 📋 修正済み CI ワークフロー定義

### ① `.github/workflows/lint-build.yml` (Lint, Detekt & Build)
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
      - name: Auto Merge
        if: |
          success() && 
          github.event.pull_request.head.repo.full_name == github.repository && 
          (github.event.pull_request.author_association == 'OWNER' || github.event.pull_request.author_association == 'MEMBER' || github.event.pull_request.author_association == 'COLLABORATOR')
        run: |
          curl -L \
            -X PUT \
            -H "Authorization: Bearer ${{ secrets.GITHUB_TOKEN }}" \
            -H "Accept: application/vnd.github+json" \
            https://api.github.com/repos/${{ github.repository }}/pulls/${{ github.event.pull_request.number }}/merge \
            -d '{"merge_method": "merge"}'
```

### ② `.github/workflows/generate-version.yml` (Generate ver.txt)
```yaml
name: Generate ver.txt

on:
  push:
    branches:
      - main
      - master

permissions:
  contents: write
  pull-requests: write

jobs:
  generate-version:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Generate ver.txt
        run: |
          VERSION=$(./gradlew -q printVersion | tail -n 1)
          echo "$VERSION" > ver.txt

      - name: Commit ver.txt
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add ver.txt
          git diff --cached --quiet || (git commit -m "chore: update ver.txt" && git push)
```

---

## 💻 開発者向けガイダンス

* **ローカルでの Detekt 自動修正（Auto-correct）**:
  ローカル環境でスタイルの違反などを自動修正したい場合は、以下のコマンドを実行します。
  ```bash
  ./gradlew detekt --auto-correct
  ```
