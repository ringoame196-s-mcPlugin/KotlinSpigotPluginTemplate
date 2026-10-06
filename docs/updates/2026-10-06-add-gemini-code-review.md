# Gemini AI Code Review Integration

- **Date**: 2026-10-06
- **Status**: Implemented
- **Author**: Development Team

---

## 概要

Pull Request 時の自動チェックフローにおいて、Detekt / Lint / Build チェックをパスしたコードに対し、Gemini API (`gemini-3.8-flash`) を用いた「ひとくち AI コードレビュー」を自動投稿する機能を `.github/workflows/lint-build.yml` に組み込みました。

---

## 導入背景・目的

1. **開発モチベーションの向上**: 自動レビューにおいてコードの良い点や Paper API の適切な使用方法を褒めるポジティブフィードバックを保証。
2. **質の高いワンポイント指摘**: 静的解析ツール（Detekt/Lint）では検知できない、Kotlin らしい書き方（Idiomatic Kotlin）や可読性・パフォーマンス向上のアドバイスを 1 点に絞って提示。
3. **無駄な CI リソースの削減**: ビルドや静的解析が成功した場合のみ AI レビューを発動させる設計とし、不必要な API 呼び出しを防止。

---

## ワークフロー構成詳細

### 実行タイミング (`.github/workflows/lint-build.yml`)

1. `Detekt, Lint & Build` ステップで静的解析とビルドを実行。
2. 上記が成功（`if: success()`）した場合のみ、`AI Code Review` ステップを実行。
3. `git diff` を取得し、環境変数 `DIFF` を介して Python スクリプト経由で Gemini API (`gemini-3.8-flash`) を呼び出し。
4. レビュー結果を `review.txt` に保存し、`gh pr comment` を用いて PR に自動コメント投稿。
5. 全ステップ成功後に `Auto Merge (Rebase)` を実行。

---

## 使用技術・環境変数

| 項目 | 使用内容 | 役割 |
| :--- | :--- | :--- |
| **モデル** | `gemini-3.8-flash` | 高速かつ低コストなレスポンス生成 |
| **CLI ツール** | `gh` (GitHub CLI) | PR へのコメント書き込み |
| **Secrets** | `GEMINI_API_KEY` | Gemini API 認証用 |
| **Tokens** | `GITHUB_TOKEN` | GitHub API / CLI 認証用 (`pull-requests: write`) |

---

## 設定されたプロンプト

```text
あなたは親切で頼もしいシニア Kotlin エンジニアです。
以下は Detekt と Lint の自動チェックをすべてクリアした綺麗な Minecraft プラグイン (Paper) のコード変更差分（Diff）です。

【レビューのルール】
1. **必ず1つ以上、コードの良い部分や工夫されている点（Paper API の適切な使用、ロジックの美しさなど）を具体的に褒めてください。**
2. 構文エラーやスタイルチェックは既にパスしているため、基本的な指摘は不要です。
3. さらに「こう書くともっと Kotlin らしさ（Idiomatic Kotlin）や Paper らしさが出る」「可読性やパフォーマンスが少し上がる」といった【ひとくちアドバイス】があれば 1 点だけ添えてください。（無ければ褒め言葉だけでOKです）
```

---

## 補足仕様 (ワークフロー設定全文)

```yaml
name: Kotlin Lint, Detekt & Build Check

on:
  pull_request:
    branches:
      - main
      - master
    paths:
      - 'src/**/*.kt' # Kotlinファイルに変更があった場合のみ発動

jobs:
  build:
    if: github.event.pull_request.draft == false # Draft PR（下書き）の場合は実行しない
    runs-on: ubuntu-latest

    permissions:
      contents: write
      pull-requests: write

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0 # Git Diff を取得するために全履歴を取得

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

      # --- 🤖 Gemini AI ひとくちレビュー ---
      - name: AI Code Review
        if: success()
        env:
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          BASE_SHA: ${{ github.event.pull_request.base.sha }}
          HEAD_SHA: ${{ github.event.pull_request.head.sha }}
        run: |
          # 1. 変更された .kt ファイルの Diff を取得
          DIFF=$(git diff $BASE_SHA..$HEAD_SHA -- '*.kt')

          # 変更差分がない場合はスキップ
          if [ -z "$DIFF" ]; then
            echo "No Kotlin diff found."
            exit 0
          fi

          # 2. Python環境変数に DIFF をセットして実行
          export DIFF="$DIFF"
          python - << 'EOF' > review.txt
          import os
          import json
          import urllib.request

          api_key = os.environ.get("GEMINI_API_KEY")
          diff = os.environ.get("DIFF", "")

          if not api_key:
              print("⚠️ GEMINI_API_KEY が設定されていません。")
              exit(0)

          url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key={api_key}"
          
          prompt = f"""
          あなたは親切で頼もしいシニア Kotlin エンジニアです。
          以下は Detekt と Lint の自動チェックをすべてクリアした綺麗な Minecraft プラグイン (Paper) のコード変更差分（Diff）です。

          【レビューのルール】
          1. **必ず1つ以上、コードの良い部分や工夫されている点（Paper API の適切な使用、ロジックの美しさなど）を具体的に褒めてください。**
          2. 構文エラーやスタイルチェックは既にパスしているため、基本的な指摘は不要です。
          3. さらに「こう書くともっと Kotlin らしさ（Idiomatic Kotlin）や Paper らしさが出る」「可読性やパフォーマンスが少し上がる」といった【ひとくちアドバイス】があれば 1 点だけ添えてください。（無ければ褒め言葉だけでOKです）

          【レビュー対象の Diff】
          ```diff
          {diff[:8000]}
          ```
          """

          payload = {
              "contents": [{"parts": [{"text": prompt}]}]
          }

          headers = {"Content-Type": "application/json"}
          req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers, method='POST')

          try:
              with urllib.request.urlopen(req) as res:
                  data = json.loads(res.read().decode('utf-8'))
                  review_text = data['candidates'][0]['content']['parts'][0]['text']
                  print("### 🤖 Gemini AI ひとくちレビュー\n\n" + review_text)
          except Exception as e:
              print(f"レビューの生成に失敗しました: {e}")
          EOF

          # 3. 生成されたレビューを PR にコメント投稿
          if [ -s review.txt ]; then
            gh pr comment $PR_NUMBER --body-file review.txt
          fi

      # --- 🚀 Auto Merge (Rebase) ---
      - name: Auto Merge (Rebase)
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
