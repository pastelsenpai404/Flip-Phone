# Pocket Hearts สำหรับ SHARP NP601SH

> แอพเพลงและทางลัด Bluetooth อยู่ใน [`PocketMusic`](PocketMusic/README.md)
> Launcher ใหม่อยู่ใน [`FlipDeck`](FlipDeck/README.md)

เกมรับหัวใจขนาดเล็กสำหรับมือถือฝาพับ Android 5.1.1 เล่นออฟไลน์และไม่ขอสิทธิ์ใด ๆ

- กด **4 / ซ้าย** และ **6 / ขวา** เพื่อเลื่อนถาดรับหัวใจ
- กด **5 / OK** เพื่อเริ่ม หยุดพัก หรือเล่นใหม่
- รับหัวใจสีทองได้ 3 คะแนน พลาดครบ 3 ดวงจบเกม
- คะแนนสูงสุดบันทึกไว้ในเครื่อง

ไฟล์ติดตั้ง: [`dist/Pocket-Hearts-NP601SH.apk`](dist/Pocket-Hearts-NP601SH.apk)

## สร้างและติดตั้งใหม่

ต้องมี JDK, Android SDK `platforms;android-22`, `build-tools;30.0.3` และ `platform-tools` ใน `.tools` จากนั้นเรียก:

```powershell
.\Build.ps1 -Install
```

ถ้าไม่ต้องการติดตั้ง ให้เรียก `.\Build.ps1` เฉย ๆ แอพใช้ Java และ Android Canvas โดยไม่มีไลบรารีภายนอก
