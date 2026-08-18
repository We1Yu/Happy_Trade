# 0001. 初始系統架構

* 狀態：accepted
* 日期：2026-08-17

## 背景與問題陳述

個人量化交易儀表板需要一套乾淨、好維護的架構，能同時支撐看盤、AI 訊號，以及手動／模擬下單。

## 決策結果

選定方案：前後端分離的單頁應用（SPA），後端採 Java Spring Boot。

* 前端：React + TypeScript + Vite
* 後端：Java 21 + Spring Boot
* 資料庫：PostgreSQL
* 部署：Docker Compose
