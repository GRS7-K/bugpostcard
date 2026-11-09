# BugPostcard｜問題回報卡

給網站和手機 App 加上一個「回報問題」入口。使用者自行開啟診斷，重現問題後就能檢查操作步驟、錯誤和環境資訊，逐筆刪除，再決定是否匯出。可選擇附上截圖，整個整理過程在裝置上完成。

- **直接試用**。開啟 [線上示範](https://grs7-k.github.io/bugpostcard/)，勾選記錄、點「Allow camera」與「Save settings」，再檢查模擬失敗的回報。
- **接到你的專案**。使用 [網站接入指南](docs/web.md)、[Android 接入指南](native/android/README.md) 或 [iOS 接入指南](native/ios/README.md)。
- **看別人寄來的回報**。HTML 用瀏覽器離線開啟。Android 和 iOS 匯出的 JSON，可用示範頁的 **Import report JSON** 開啟、檢查並轉成離線 HTML，或複製 GitHub Issue 文字。

## 在自己的電腦試用

從 [GitHub 版本頁](https://github.com/GRS7-K/bugpostcard/releases) 下載並解壓縮。用 Node.js 22 以上在專案資料夾執行 `npm run demo`，開啟畫面顯示的本機網址。核心測試可執行 `npm test`。

## 資料怎麼處理

診斷預設關閉。這個工具不會自行上傳，使用者可逐筆移除紀錄或截圖。請在分享前檢查手寫文字、接入專案提供的標籤及圖片，避免把個人資料附進公開 Issue。工具只處理有接入的網站或 App。各平台的支援範圍及測試方式請見各自的接入指南。

採用 [MIT 授權](LICENSE)。
