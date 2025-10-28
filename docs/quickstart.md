# Quickstart — 客戶整合資產查詢

## Prerequisites

- Java 17
- Docker（用於啟動 MongoDB 或 Testcontainers）
- MongoDB URI（例如 `mongodb://localhost:27017/assetdb`）
- 可用的 Postman/cURL 或 `httpie` 驗證 REST API

## Setup Steps

1. **啟動 MongoDB**
   ```bash
   docker run -d --name mongo -p 27017:27017 mongo:8.0
   ```

2. **設定環境變數（以 assets 模組為例）**
   ```bash
   export ASSETS_MONGODB_URI="mongodb://mongo:mongo@localhost:27017/assetdb?authSource=admin"
   export ASSETS_BANK_BASE_URL="http://localhost:8081"
   export ASSETS_SECURITIES_BASE_URL="http://localhost:8082"
   export ASSETS_INSURANCE_BASE_URL="http://localhost:8083"
   export ASSETS_THREADPOOL_SIZE=8
   export ASSETS_MONGO_WRITE_MAX_ATTEMPTS=3
   export ASSETS_MONGO_WRITE_BACKOFF=100ms
   ```

3. **啟動四個 Spring Boot 應用（可使用不同 port）**
   ```bash
   ./gradlew :bank:bootRun --args='--server.port=8081'
   ./gradlew :securities:bootRun --args='--server.port=8082'
   ./gradlew :insurance:bootRun --args='--server.port=8083'
   ./gradlew :assets:bootRun --args='--server.port=8080'
   ```

4. **驗證個別資產 API**
   ```bash
   curl http://localhost:8081/bank/customers/123/assets | jq
   curl http://localhost:8082/securities/customers/123/assets | jq
   curl http://localhost:8083/insurance/customers/123/assets | jq
   ```

5. **呼叫整合資產 API**
   ```bash
   curl http://localhost:8080/assets/customers/123 | jq
   ```

6. **檢視 Swagger UI**
   - Bank: `http://localhost:8081/swagger-ui.html`
   - Securities: `http://localhost:8082/swagger-ui.html`
   - Insurance: `http://localhost:8083/swagger-ui.html`
   - Assets: `http://localhost:8080/swagger-ui.html`

7. **驗證 MongoDB 寫入**
   ```bash
   docker exec -it mongo mongosh assetdb
   db.bank_raw.find({ customerId: "123" }).pretty()
   db.asset_staging.find({ customerId: "123" }).pretty()
   ```

## Testing Workflow (TDD)

1. 編寫並執行寫入邏輯單元測試（預期失敗）  
   `./gradlew :assets:test --tests "com.poc.svc.assets.repository.BankAssetRepositoryTest"`

2. 實作 repository/service 使測試通過，再開發 controller 測試。

3. 在 assets 模組撰寫聚合流程測試（Testcontainers Mongo）。  
   `./gradlew :assets:test --tests "com.poc.svc.assets.service.AssetAggregationServiceTest"`

4. 覆蓋超時、缺漏來源與整合 API 行為。  
   `./gradlew :assets:test --tests "com.poc.svc.assets.service.AssetAggregationTimeoutTest"`  
   `./gradlew :assets:test --tests "com.poc.svc.assets.service.AssetSourceMissingDataTest"`  
   `./gradlew :assets:test --tests "com.poc.svc.assets.controller.AssetIntegrationControllerTest"`

4. 透過 `./gradlew check` 執行整體測試與靜態檢查。

## Monitoring & Metrics

- Micrometer 指標（Assets 模組）
  - `asset.fetch.latency` — 下游資產 API 呼叫耗時（tag: source）
  - `asset.aggregation.latency` — 整合流程總耗時
  - `asset.aggregation.staging.write.latency` — Mongo staging 寫入耗時
  - `asset.aggregation.success` / `asset.aggregation.failure` — 聚合流程成功/失敗計數
  - `asset.aggregation.raw.write` — 依來源與狀態分組的 raw 寫入結果（tag: `source`, `status=SUCCESS|FAILED|TIMEOUT|MISSING`），可用來觀察 Mongo 重試成效
- 建議搭配 Prometheus / Grafana 監控，確保 p95 <= 1 秒。

## Currency Configuration & Deployment Checks

- `assets.currency.rates` 提供靜態匯率對照，例如 `USD:TWD`, `JPY:TWD`。上線前請依實際匯率更新設定檔或環境變數，必要時導入外部匯率服務。
- 建議於預備環境執行整合測試流程：
  1. 啟動三個來源 API (bank/securities/insurance) 與 assets 模組。
  2. 以真實下游系統或模擬器產生 404、超時與故障情境，驗證 `AssetIntegrationController` 回傳錯誤碼 `ASSET_AGGREGATION_FAILED` 及 `failedSources` 明細。
  3. 監控上述聚合指標，確認成功率與 staging 寫入耗時符合 99% SLA，並針對 `asset.aggregation.failure` 與 `asset.aggregation.raw.write{status="FAILED"}` 建立告警。
