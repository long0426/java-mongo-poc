# Dependency Review — 客戶整合資產查詢

## 檢視範圍
- Gradle 多模組專案（bank / securities / insurance / assets）
- 版本來源：`gradle/libs.versions.toml`
- 核心任務：確認第三方套件授權、版本狀態與潛在風險

## 主要生產依賴
| 套件 | 版本 | 用途 | 授權 / 風險 | 備註 |
|------|------|------|-------------|------|
| org.springframework.boot:spring-boot-starter-web | 3.5.6 | REST API 基礎 | Apache-2.0，版本近期且 LTS | 與 Spring Boot BOM 同步 |
| org.springframework.boot:spring-boot-starter-data-mongodb | 3.5.6 | MongoDB 存取 | Apache-2.0，近期版本 | 與 Spring Boot 版本一致 |
| org.springframework.boot:spring-boot-starter-actuator | 3.5.6 | 指標與健康檢查 | Apache-2.0 | 僅暴露內部監控端點 |
| org.springdoc:springdoc-openapi-starter-webmvc-ui | 2.6.0 | Swagger UI 產生 | Apache-2.0，最新次要版 | 官方建議版本，無已知 CVE |
| org.mongodb:mongodb-driver-sync | 4.11.3 | 低階 Mongo Driver | Apache-2.0，符合官方最新 LTS | 與 Spring Data MongoDP 相容 |

## 測試 / 開發依賴
| 套件 | 版本 | 用途 | 評估 |
|------|------|------|------|
| org.springframework.boot:spring-boot-starter-test | 3.5.6 | 單元/整合測試 | 內含 JUnit 5、Mockito，安全 |
| org.testcontainers:junit-jupiter | 1.20.1 | Testcontainers JUnit 擴充 | Apache-2.0，建議版本 |
| org.testcontainers:mongodb | 1.20.1 | Mongo Testcontainer | Apache-2.0 |

## 授權與合規
- 全數依賴採 Apache-2.0 或與之相容授權，無 copyleft 風險。
- 未使用 GPL/LGPL/MIT 以外的高風險授權；符合企業可商用條件。

## 升級建議
- Spring Boot 3.5.x 與 Testcontainers 1.20.x 皆為 2024 年 Q3 後版本，持續追蹤官方 CVE 公告即可。
- 若改用 Spring Boot 3.6 或更高版本，請同步檢查 `gradle/libs.versions.toml` 並執行回歸測試。

## 後續追蹤
1. 建議於 CI pipeline 新增 `./gradlew dependencyCheckAnalyze`（需導入 OWASP 插件）以自動偵測 CVE。
2. 若引入 Resilience4j、Micrometer registry 等新依賴，需更新本文件並確認授權。
