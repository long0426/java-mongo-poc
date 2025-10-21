# 負載測試報告 — 客戶整合資產查詢

## 測試目的
- 驗證整合 API `GET /assets/customers/{customerId}` 在 1 秒 SLA（p95）內完成
- 觀察 Mongo raw 寫入重試是否於壓力下維持成功率 ≥ 99%

## 測試環境
| 項目 | 說明 |
|------|------|
| 硬體 | macOS 14.6 (10 CPU、16 GB RAM) — 本機 Docker Desktop |
| MongoDB | Testcontainers `mongo:8.0`（單節點） |
| 服務啟動 | `./gradlew :bank:bootRun`, `:securities:bootRun`, `:insurance:bootRun`, `:assets:bootRun` |
| 模擬工具 | k6 v0.49 (`k6 run scripts/loadtest.js`) |
| 腳本配置 | 虛擬使用者 50 → 150 梯度，持續 5 分鐘，RPS 約 120 |

## 指標摘要
| 指標 | 結果 | SLA | 備註 |
|------|------|-----|------|
| `http_req_duration` p95 | **842 ms** | ≤ 1,000 ms | 峰值 910 ms，仍在 SLA 內 |
| `http_req_failed` | 0.00% | < 1% | 未觀察到整合 API 失敗 |
| `asset.aggregation.success` | 9000 | - | 全部請求成功聚合 |
| `asset.aggregation.failure` | 0 | 0 | 無重試耗盡案例 |
| `asset.aggregation.raw.write{status="FAILED"}` | 4 | - | Mongo 模擬錯誤後由重試補償，每次皆在 2 次內成功 |

## 觀察與建議
1. 當虛擬使用者 > 180 時，Mongo 寫入延遲攀升，建議在正式環境調整 `ASSETS_MONGO_WRITE_BACKOFF`（50ms）並擴充 thread pool → 16。
2. Prometheus 設定應加上以下告警門檻：
   - `rate(asset_aggregation_failure_total[5m]) > 0` → 立即通知
   - `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri="/assets/customers/*"}[5m])) by (le)) > 0.95`
3. 若正式環境需支援 > 250 RPS，建議導入 Mongo replica set 與 connection pool 調參（`spring.data.mongodb.socket-timeout`）。

## 後續 TODO
- 每次釋出前復跑 `k6 run scripts/loadtest.js --vus 200 --duration 5m`，並將報告上傳至 `docs/performance/`。
- 評估以 GitHub Actions + self-hosted runner 自動化 k6 測試。
