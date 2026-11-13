# J2ME Cloud Player

Game J2ME chạy trên server, mỗi phiên một JVM riêng. Trình duyệt chỉ là màn hình và bàn phím.
Đóng tab thì phiên vẫn chạy, mở lại là vào đúng phiên.

```
web (React+Vite+Tailwind) <-> WebSocket/REST <-> gateway (Node) <-> worker (JVM headless, 1 process/phiên)
```

## Chạy

```bash
# 1. worker (JDK 21, không cần Maven): build cả hai lõi
cd worker && ./build.sh

# 2. web
cd ../web && npm install && npm run build

# 3. gateway (phục vụ luôn web/dist), mở http://localhost:8080
cd ../gateway && npm install && npm start

# kiểm tra phiên sống khi ngắt kết nối (game thử nghiệm, không cần jar)
npm test
```

Game đặt trong `games/*.jar` (hoặc kéo thả/chọn file .jar ở Trang chủ). Dev giao diện: `cd web && npm run dev`.

## Hai lõi MIDP

Hai lõi cùng dùng tên gói `javax.microedition.*` nên mỗi lõi có classpath riêng (`worker/build/own`, `worker/build/microemu`).
Chọn bằng `CORE=own|microemu` cho gateway (mặc định `microemu`), hoặc `--core` cho worker.

- **microemu** (mặc định): MicroEmulator thật, chạy headless qua `MicroEmuEmulator` (DisplayComponent giữ ảnh màn hình thay vì
  vẽ cửa sổ Swing). Mã nguồn nằm trong `worker/vendor/microemu` (Apache 2.0 / LGPL 2.1, kèm file giấy phép), biên dịch bằng
  `javac` chứ không dùng Maven. Gồm cldc, midp, javase, javase-swing và các phần mở rộng JSR-120 (SMS), JSR-135 (media), NokiaUI.
  Có **một bản vá nhỏ**, xem `vendor/microemu/PATCHES.md`: vài game Teamobi ghi đè `Canvas.getWidth()/getHeight()` trả về -1,
  làm MicroEmulator vẽ lại vùng rỗng nên màn hình đen.
- **own**: bản MIDP 2.0 tối giản tự viết (`worker/src/own`), dùng làm đối chứng và dự phòng.

Chưa có: âm thanh phát ra trình duyệt, M3G. SMS của MicroEmulator chỉ là bản rỗng, không gửi gì. Nhập chữ (`T` trong giao thức)
mới nối vào lõi `own`, giao diện hiện không có ô gõ chữ.

`worker/attic/Screens.java` là một file cũ không biên dịch được, đã thay bằng `Skin.java`. Giữ lại để tham khảo.

## Mạng

Mặc định TẮT: mọi socket đi qua một SOCKS proxy không tồn tại nên bị từ chối ngay, game báo "Máy chủ tắt hoặc mất sóng".
Đây chỉ là công tắc tiện dụng, không phải sandbox. Bật: chạy gateway với `ALLOW_NET=1` và tạo phiên với `net: true`
(hoặc worker `--net on`).

## Giao thức worker <-> gateway

| Hướng | Kênh | Nội dung |
|---|---|---|
| gateway → worker | stdin | `K <1\|0> <keycode>`, `P <0\|1\|2> <x> <y>`, `T <text>`, `W <1\|0>`, `Q` |
| worker → gateway | stdout | `[uint32 độ dài][JPEG]`, chỉ gửi khi có người xem và hình thay đổi |
| worker → gateway | stderr | `STATE k=v ...` mỗi giây, còn lại là log (`GET /api/sessions/:id/log`) |

Mã phím kiểu J2ME: chữ số `48..57`, `*`=42, `#`=35, lên/xuống/trái/phải `-1..-4`, OK `-5`, soft trái/phải `-6/-7`.
Dữ liệu RMS (lưu game) nằm ở `data/rms/<tên game>/` và giữ nguyên giữa các phiên.

Chế độ gỡ lỗi không cần gateway (ảnh PNG sau 6 giây, có thể chèn phím/chạm):
`java -cp build/common:build/microemu:vendor/microemu/lib/asm-3.2.jar Worker --core microemu --jar games/X.jar --snapshot out.png --after 6000 --script "4000:K 1 -5;4100:K 0 -5"`

## Việc cần làm trước khi mở cho người khác dùng

- Chạy worker trong container (không root, giới hạn CPU/RAM/disk): jar là mã không tin cậy, công tắc mạng ở trên không ngăn được nó.
- Xác thực REST/WebSocket, tự dừng phiên không ai xem, giới hạn phiên mỗi người.
- Âm thanh: gửi dữ liệu từ `Manager/Player` xuống trình duyệt, phát bằng Web Audio.
- Chưa thử với server thật của game (sandbox dựng không có mạng ra ngoài).

## Giao diện

- `/` Trang chủ: tải .jar lên (kéo thả, nhiều file), danh sách game, "Chơi" hoặc "Tiếp tục" nếu game đang chạy.
- `/play/:id` Trang điều khiển: ← về trang chủ (game vẫn chạy), nút bàn phím (chỉ hiện trên điện thoại), Reset, Đóng (dừng hẳn; hai bước xác nhận).
  Dưới màn hình luôn có: Mềm trái | 4 hướng + OK | Mềm phải (cả máy tính lẫn điện thoại); không có số/`*`/`#`.
  Chuột/cảm ứng chạm thẳng lên màn hình game (con trỏ giữ nguyên hình mũi tên).
  Gõ chữ: bàn phím vật lý gửi thẳng ký tự (mã ký tự; Backspace = -8, Enter = OK, F1/F2 = phím mềm), không còn WASD/QE.
  Nút bàn phím (chỉ hiện trên điện thoại) mở bàn phím của điện thoại qua một ô ẩn không hiển thị gì, chữ gõ được chuyển ngay cho game.
  Khi game mở ô nhập (TextBox) thì chữ được chèn thẳng (số vẫn là số, không multi-tap); khi game tự vẽ ô nhập trên Canvas thì nhận mã phím.
  Bàn phím vật lý, phím ảo và bàn phím điện thoại là ba nguồn độc lập: một phím chỉ nhả khi cả ba đều đã nhả.

## Khi game không chạy được

Worker kiểm tra jar trước khi nạp (có phải zip, có `MANIFEST.MF`, có `MIDlet-1`, lớp chính có trong jar, có dùng API chưa hỗ trợ như
M3G/Bluetooth không) rồi báo lỗi bằng dòng `ERROR fatal|warn <base64>` trên stderr. Gateway gom lại thành `errors` trong
`GET /api/sessions/:id`; trang điều khiển hiện:

- **lỗi nặng** (không khởi chạy được, tiến trình thoát bất thường, máy chủ không có Java): màn hình giải thích, kèm gợi ý, chi tiết kỹ thuật,
  nút "Xem log" và "Sao chép lỗi";
- **cảnh báo** (một luồng của game chết vì ngoại lệ, game dùng API chưa hỗ trợ, 25 giây chưa vẽ gì): dải màu cam phía trên màn hình, bấm để xem chi tiết.

## Lỗi phía máy chủ và mất kết nối

- Trang điều khiển hiện dải đỏ "Mất kết nối" sau 3 giây và tự dò nguyên nhân: thiết bị không có mạng, máy chủ không trả lời,
  hoặc máy chủ vẫn sống nhưng kết nối trực tiếp tới game (WebSocket) bị ngắt. Nếu máy chủ khởi động lại thì phiên mất và trang giải thích điều đó.
- `GET /api/health` (và trang `/status`): thời gian chạy, số phiên, RAM mỗi phiên, RAM/CPU máy chủ và 20 lỗi gần nhất của gateway
  (ngoại lệ không bắt, lỗi 500…). Gateway ghi lại rồi chạy tiếp để một lỗi không kéo sập mọi game; nếu có hơn 20 lỗi trong một phút thì tự thoát.
  Chưa có xác thực nên đừng mở trang này ra Internet.
- Worker cảnh báo khi: không ghi được thư mục lưu game, ổ đĩa còn dưới 20 MB, không mã hóa được hình ảnh.

## Chạy bằng một lệnh (từ thư mục gốc)

```bash
npm install     # cài web + gateway (postinstall)
npm start       # tự build web và worker nếu chưa có, rồi chạy ở http://localhost:3000 (đổi bằng PORT)
```

Cần Node 20+ và JDK 21 (có `javac`). Không có JDK thì trang vẫn mở được nhưng bấm "Chơi" sẽ báo lỗi thiếu Java.
`npm run build` chỉ build web; `npm test` chạy bài kiểm tra phiên giữ sống của gateway.
