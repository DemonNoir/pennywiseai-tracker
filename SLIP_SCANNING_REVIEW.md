# รีวิวโค้ด Slip Scanning & OCR — PennyWise AI Tracker

> ขอบเขต: อ่านเฉพาะ 5 ไฟล์ที่เกี่ยวกับสแกนสลิป/OCR
> `SlipParser.kt`, `SlipOcrEngine.kt`, `SlipMediaObserver.kt`, `SlipNotificationManager.kt`, `SlipScanBottomSheet.kt`
>
> **สรุปภาพรวม:** โค้ดมีโครงสร้างที่ดี (แยก layer ชัดเจน, มี `SlipConfidence`/`SlipDirection` ออกแบบไว้แล้ว)
> แต่มีจุดที่ต้องปรับปรุงเพื่อให้การทำงานสมบูรณ์ 100%

---

## 🔴 P0 — Blocker (ฟีเจอร์ใช้งานจริงไม่ได้จนกว่าจะแก้)

### 1. `saveToPennyWiseRoomDb()` บันทึกลงฐานข้อมูลจริง
📍 `SlipMediaObserver.kt` 
เชื่อมต่อกับ `SharedTransactionRepository` / `CreateManualTransactionUseCase` บันทึกรายการสลิปเข้า Room DB จริง และคืนค่า ID ของ Transaction จริงจาก Database

### 2. ตรวจสอบ Runtime Permission อย่างถูกต้อง
📍 `PennyWiseApplication.kt` & `SlipMediaObserver.kt` & `SlipScanBottomSheet.kt`
ตรวจสอบสิทธิ์ `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` / `POST_NOTIFICATIONS` ก่อนเปิดการทำงาน `slipMediaObserver.register()` 

### 3. Auto-save กรอง `confidence` และ `direction`
📍 `SlipMediaObserver.kt`
- กรองข้ามรายการฝั่ง `INCOMING` (สลิปรับเงินเข้า)
- กรองเฉพาะรายการ `OUTGOING` (โอนออก) และ `BILL_PAYMENT` (ชำระบิล)
- สำหรับ `CONFIRMED` -> บันทึกและยืนยันทันที
- สำหรับ `HIGH` / `NEEDS_REVIEW` -> บันทึกพร้อมสถานะรอตรวจสอบ

### 4. ตรวจสอบและประเมิน OCR Engine 
📍 `SlipOcrEngine.kt`
ทดสอบความแม่นยำภาษาไทยร่วมกับ `SlipParser` NFC normalization

---

## 🟠 P1 — ปรับปรุงประสิทธิภาพความถูกต้อง

### 5. ปรับแก้ General Name Extraction
📍 `SlipParser.kt`
ลบชื่อเฉพาะเจาะจงออก ใช้ Regex ตัวพิมพ์ใหญ่/คำทั่วไปในการสกัดชื่อร้านค้า/บริษัท

### 6. ระบบป้องกันสแกนรูปซ้ำ (Persistent Processed Set)
📍 `SlipMediaObserver.kt`
บันทึกประวัติ Image URI/ID ที่ประมวลผลแล้วลง DataStore หรือ Room DB เพื่อป้องกันการสแกนรูปซ้ำเมื่อ App ถูก kill process

### 7. เพิ่ม Catch-up Scan สแกนย้อนหลัง
สแกนรูปสลิปย้อนหลังเมื่อเปิดแอปใหม่

### 8. ปรับปรุงตัวเลือกโฟลเดอร์สลิปธนาคารใน UI
📍 `SlipScanBottomSheet.kt`
เชื่อมต่อพาธโฟลเดอร์สลิปเฉพาะของแต่ละธนาคารในเครื่องจริง (`Pictures/SCB EASY/`, `Pictures/K PLUS/`)

---

## 🟡 P2 — คุณภาพและ UX

### 9. Pre-filter สำหรับรูปสลิป
### 10. Dynamic Notification ID
📍 `SlipNotificationManager.kt` ใช้ ID ตาม `transactionId` ป้องกันการแจ้งเตือนทับกัน
### 11. Deep Link ผูกกับ Transaction ID จริง
