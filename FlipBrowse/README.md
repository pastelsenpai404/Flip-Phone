# Flip Browse — SHARP NP601SH

เบราว์เซอร์แบบหน้าเดียวสำหรับ Android 5.1.1 ใช้ WebView ที่ติดเครื่อง ไม่มีไลบรารีเสริมหรือบริการทำงานเบื้องหลัง ไม่เปิดแท็บหรือป๊อปอัปเพิ่ม และพักตัวเว็บเมื่อออกจากแอพ

## ปุ่มและการใช้งาน

- ปุ่มลูกโลกบนหน้า Home ของ FlipDeck: เปิด Flip Browse; ถ้าอยู่ในเบราว์เซอร์จะเปิดช่อง Address / Search
- ลูกศร + OK: เลือกปุ่มและลิงก์บนเว็บ
- Menu: ย้อนกลับ/ไปข้างหน้า, รีโหลด/หยุด, Home, บุ๊กมาร์ก, ซูม, ตั้งค่า และออก
- Back: ย้อนหน้าก่อนหน้า หรือออกเมื่ออยู่หน้าเริ่มต้น
- พิมพ์ชื่อเว็บไซต์หรือคำค้นใน Address / Search แล้วเลือก Go
- Data saver: เลือก Images หรือ JavaScript เพื่อสลับ ON/OFF และมีผลทันที บางเว็บไซต์ต้องเปิด JavaScript
- ดาวน์โหลดไฟล์ HTTP/HTTPS ผ่านระบบ Downloads หลังผู้ใช้ยืนยัน
- Clear browsing data: ล้างคุกกี้ แคช ประวัติ และข้อมูลเว็บไซต์ โดยเก็บบุ๊กมาร์กไว้

หน้าเริ่มต้นใช้งานออฟไลน์ได้ เว็บภายนอกต้องมีอินเทอร์เน็ต แอพใช้ WebView ของเครื่อง (เครื่องทดสอบเป็นรุ่น 39) เว็บไซต์สมัยใหม่บางแห่งอาจไม่รองรับ ไม่ข้ามข้อผิดพลาดใบรับรอง HTTPS

## สร้างและติดตั้ง

ใช้ SDK ใน `../.tools` และ signing key `../pocket-hearts.keystore` ร่วมกับแอพอื่น

```powershell
.\Build.ps1 -Install
```

APK: [dist/Flip-Browse-NP601SH.apk](dist/Flip-Browse-NP601SH.apk)

ต้องติดตั้ง FlipDeck รุ่นที่มีทางลัด Flip Browse ด้วย มีตัวรับปุ่มลูกโลกในบริการ Accessibility `Flip Deck shortcuts` ที่มีอยู่แล้วด้วย การรับปุ่มจากแอพอื่นขึ้นอยู่กับระบบของ SHARP หากบริการปิดอยู่ ปุ่มลูกโลกยังใช้งานได้จากหน้า Home ของ FlipDeck

## การตรวจบนเครื่อง

ติดตั้งบน NP601SH สำเร็จ ทดสอบหน้าเว็บ HTTP ผ่าน USB: โหลดหน้าเว็บ, JavaScript, เลือกลิงก์ด้วยลูกศร + OK, ย้อนกลับ/ไปข้างหน้า และตรวจการบันทึกสวิตช์ JavaScript ON/OFF แล้ว ทางลัดจาก Home ทดสอบด้วย key event F2 และตรวจไฟล์ keylayout ของเครื่องว่าแปลงปุ่มลูกโลกเป็น F2 จริง การเปิดเว็บไซต์ภายนอกยังไม่ได้ทดสอบเพราะ Wi-Fi ไม่เชื่อมต่อ

อ้างอิง API: [Android WebSettings](https://developer.android.com/reference/android/webkit/WebSettings), [WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient)
