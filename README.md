# 客戶整合資產查詢 — 開發說明

## 專案概述
- Spring Boot 3 多模組（`bank`、`securities`、`insurance`、`assets`）模擬三個來源資產 API 與整合服務。
- 整合服務會並行呼叫三個來源、寫入 MongoDB raw collection，最後以基準貨幣（TWD）聚合至 `asset_staging`，並直接回傳新寫入的 `asset_staging` 文件（Mongo Document）。
- 具備共用錯誤契約、追蹤 ID、Micrometer 指標與 Mongo 寫入重試機制。

## 目錄結構
```
assets/     # 整合服務與共用元件
bank/       # 銀行模組
securities/ # 證券模組
insurance/  # 保險模組
docs/       # 快速開始、API 規格與補充文件
```

## 快速開始
- 建議依照 `docs/quickstart.md` 進行環境設定與驗證流程。
- 所有模組皆使用 Gradle，透過 `./gradlew :module:bootRun` 啟動；MongoDB 可使用 Docker 執行 `mongo:8.0`。

## 配置重點
| 變數 | 說明 | 預設值 |
|------|------|--------|
| `ASSETS_MONGODB_URI` | Mongo 連線字串 | `mongodb://localhost:27017/assetdb` |
| `ASSETS_MONGODB_DATABASE` | Mongo 資料庫名稱 | `assetdb` |
| `ASSETS_BANK_BASE_URL` / `ASSETS_SECURITIES_BASE_URL` / `ASSETS_INSURANCE_BASE_URL` | 下游 API 基底路徑 | `http://localhost:808{1,2,3}` |
| `ASSETS_THREADPOOL_SIZE` | 整合服務非同步執行緒池大小 | `8` |
| `ASSETS_AGGREGATION_TIMEOUT` | 聚合超時設定 | `3s` |
| `ASSETS_BASE_CURRENCY` | 聚合基準貨幣 | `TWD` |
| `ASSETS_MONGO_WRITE_MAX_ATTEMPTS` | Mongo raw 寫入最大重試次數 | `3` |
| `ASSETS_MONGO_WRITE_BACKOFF` | Mongo raw 寫入重試等待（支援 ms/s） | `100ms` |

> 範例 `assets/src/main/resources/application.yaml.example` 亦同步更新，可作為部署時的參考。

## 測試
- 單元與整合測試：`./gradlew :assets:test`
- 指定測試類別：
  - raw 寫入重試行為：`./gradlew :assets:test --tests "com.poc.svc.assets.service.MongoWriteFailureTest"`
  - 成功率指標驗證：`./gradlew :assets:test --tests "com.poc.svc.assets.service.AssetSuccessRateMetricsTest"`
- Testcontainers 會啟動 `mongo:8.0`，請確保 Docker 可用。

## 指標與監控
- 關鍵 Micrometer 指標：
  - `asset.fetch.latency`、`asset.aggregation.latency`、`asset.aggregation.staging.write.latency`
  - `asset.aggregation.success`、`asset.aggregation.failure`
  - `asset.aggregation.raw.write{source, status}` — 追蹤 raw 寫入成功/失敗/超時
- 建議在預備環境模擬下游錯誤，確認 `asset.aggregation.failure` 與 `asset.aggregation.raw.write{status="FAILED"}` 告警門檻設定。

## 相關文件
- 環境設定與驗證流程：`docs/quickstart.md`
- 依賴掃描摘要：`docs/dependency-review.md`
- 壓力測試報告：`docs/load-test-report.md`
- API 規格：`docs/swagger/*.yaml`
