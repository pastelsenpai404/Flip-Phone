# Flip Deck

## Recent apps button

On the NP601SH, a short press of the End/Close key while Flip Deck is on screen now opens the phone's system recent-apps view. Use that view to switch to an app or dismiss its card. This uses the **Flip Deck recent apps** accessibility service; enable it in Settings > Accessibility after a fresh install. A long press still belongs to the phone's power controls.

Launcher สำหรับ SHARP NP601SH (Android 5.1.1) ออกแบบให้ใช้ปุ่มกดได้โดยไม่ต้องแตะจอ

- หน้าโชว์รูปเป็นหน้าแรก มีเพลง รูปภาพ และแอพทั้งหมดอยู่ด้านล่าง กด **\*** หรือปุ่มขึ้นเพื่อดูตารางทางลัด
- ในตารางทางลัด หรือขณะอยู่หน้าโชว์รูป กดเลข **1–9** เพื่อเปิด เพลง, รูปภาพ, ปฏิทิน, โน้ต, เครื่องคิดเลข, นาฬิกาปลุก, ไฟล์, ตั้งค่า และแอพทั้งหมด
- กด **0** หรือ **Menu** เพื่อเปิดเครื่องมือด่วน: Bluetooth, รูปพื้นหลัง, เสียง, หน้าจอ, พื้นที่เก็บข้อมูล, แบตเตอรี่, ตั้งค่าแอพ, วันเวลา และแอพทั้งหมด
- กด **#** เพื่อเปิดรายชื่อแอพทั้งหมด เลื่อนด้วยปุ่มทิศทาง และกด **OK** เพื่อเปิด
- กด **\*** เพื่อสลับหน้าโชว์รูปกับตารางทางลัด กด **Back** เพื่อกลับหน้าโชว์รูป
- เวลา วันที่ แบตเตอรี่ สถานะ Bluetooth และ Wi-Fi แสดงบนหน้าแรก
- รูปพื้นหลังแสดงเต็มจอหลังปุ่มและข้อความ เข้า **0 > 2 Wallpaper** เพื่อเลือกภาพจากเครื่อง, ใช้ภาพพื้นหลังเดิมของโทรศัพท์ หรือปิดรูปพื้นหลัง

ปุ่มจริงของเครื่องยังใช้ได้: ปุ่มโทรเปิดโทรศัพท์, ปุ่มจดหมายเปิดข้อความ, ปุ่มเว็บเปิดเบราว์เซอร์, ปุ่มกล้องเปิดกล้อง และปุ่มลงบนหน้าโชว์รูปเปิดรายชื่อ ปุ่มรายการโปรดเปิดหน้าแอพทั้งหมดของ Flip Deck ส่วน Wi-Fi เปิด/ปิดได้ด้วยปุ่มลัดของเครื่องตามคู่มือ

ติดตั้ง APK: [`dist/Flip-Deck-NP601SH.apk`](dist/Flip-Deck-NP601SH.apk)

สร้างและติดตั้งใหม่:

```powershell
.\Build.ps1 -Install
```

Flip Deck ถูกตั้งเป็น Home เริ่มต้นบนเครื่องทดสอบแล้ว หากต้องการกลับไปใช้ launcher เดิม ให้เข้า Settings > Other settings > Application > Flip Deck > Clear defaults แล้วกด Home เพื่อเลือกใหม่
