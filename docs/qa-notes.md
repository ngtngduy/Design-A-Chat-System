# Tài liệu Ôn tập Bản chất Kỹ thuật & Câu hỏi Phản biện (Deep Q&A)

Tài liệu này giúp bạn nắm vững **bản chất kỹ thuật ("tại sao lại thiết kế như vậy")** và sẵn sàng trả lời các câu hỏi đào sâu của giảng viên khi thuyết trình.

---

## Bộ 10 Câu hỏi Phản biện Thường gặp & Cách Trả lời Ăn điểm

### Câu 1: Tại sao đã dùng WebSocket rồi mà vẫn cần HTTP REST (cổng 8080) cho Login và Sync?
* **Trả lời bản chất:**
  * WebSocket là kết nối **Stateful** (giữ chặt tài nguyên trên một máy chủ cụ thể). Trước khi biết người dùng là ai và nên nối vào máy chủ Chat nào, client chưa thể mở WebSocket ngay được.
  * Các tác vụ như **Login, lấy danh sách bạn bè, hỏi Service Discovery** chỉ diễn ra ngắn hạn theo mô hình Request–Response → đặt trên cụm **Stateless HTTP (`8080`)** giúp dễ cân bằng tải (Load Balancing), dễ scale độc lập và không làm tốn slot kết nối trên các Chat Server.
* **Dẫn chứng trong code:** [`ApiServer.kt`](../server/src/main/kotlin/com/chatdemo/server/ApiServer.kt) tách riêng ở cổng `8080`, còn [`ChatWsServer.kt`](../server/src/main/kotlin/com/chatdemo/server/ChatWsServer.kt) chạy ở `8081` và `8082`.

---

### Câu 2: Sách nói Long Polling cũng giữ kết nối chờ tin mới, vậy tại sao không dùng Long Polling cho khỏe mà phải lên WebSocket?
* **Trả lời bản chất (3 lý do sâu):**
  1. **Lệch máy chủ:** Trong Long Polling, mỗi khi nhận xong 1 tin nhắn, kết nối HTTP đóng lại và client mở request mới — Load Balancer có thể đẩy request mới sang một server khác. Server nhận tin từ người gửi không biết người nhận đang treo request ở server nào.
  2. **Mù trạng thái ngắt mạng:** Server giữ Long Polling rất khó phân biệt giữa *"client đang yên lặng chờ tin"* và *"client đã rớt mạng Wi-Fi/4G"*.
  3. **Chi phí tái lập kết nối:** Nếu hai người đang nhắn tin liên tục, Long Polling vẫn phải đóng/mở lại HTTP request sau mỗi tin nhắn, tốn HTTP Header hơn nhiều so với 1 frame WebSocket vài byte.

---

### Câu 3: Trên điện thoại Android thật, giữ kết nối WebSocket 24/7 có khả thi không?
* **Trả lời bản chất (Câu hỏi ăn điểm môn Lập trình thiết bị di động):**
  * **Không!** Trên Android (từ Android 6.0+), khi người dùng tắt màn hình hoặc để máy nghỉ, hệ điều hành kích hoạt **Doze Mode & App Standby** để tiết kiệm pin — các kết nối mạng ngầm của app sẽ bị đóng băng hoặc ngắt.
  * Ngoài ra nếu 20 ứng dụng trên máy đều tự giữ 20 kết nối WebSocket ngầm 24/7 thì radio 4G/Wi-Fi không bao giờ ngủ được, làm tụt pin cực nhanh.
  * **Giải pháp thực tế:**
    * Khi người dùng **đang mở app trên màn hình (Foreground):** Dùng **WebSocket** để chat tốc độ cao.
    * Khi người dùng **thoát ra ngoài / khóa máy (Background):** Đóng WebSocket; khi có tin nhắn mới, Server thấy user không còn WebSocket nên gọi **FCM (Firebase Cloud Messaging)** — hệ điều hành Android dùng chung **1 kết nối duy nhất của Google Play Services** để đánh thức thông báo cho mọi app trên máy!
* **Dẫn chứng trong code:** Khi user không còn session WebSocket trong [`MessageRouter.kt`](../server/src/main/kotlin/com/chatdemo/server/MessageRouter.kt), hệ thống lập tức gọi [`PushNotifier.kt`](../server/src/main/kotlin/com/chatdemo/server/PushNotifier.kt) (`[PUSH -> APNS/FCM]`).

---

### Câu 4: Tại sao lưu tin nhắn lại chọn Key-Value Store (Cassandra/HBase) mà không dùng MySQL/PostgreSQL?
* **Trả lời bản chất:**
  * Không phải vì "NoSQL hiện đại hơn", mà do **cấu trúc dữ liệu trên đĩa**:
    * MySQL (InnoDB) dùng **B-Tree**: khi chèn dữ liệu vào bảng hàng tỷ dòng có nhiều index, đĩa phải đọc/ghi ngẫu nhiên (Random I/O) để cân bằng cây → độ trễ tăng mạnh khi dữ liệu lớn.
    * Cassandra/HBase dùng **LSM-Tree (Log-Structured Merge-Tree)**: mọi thao tác ghi đều là **ghi nối tiếp tuần tự (Append-only)** vào Commit Log và MemTable trên RAM rồi mới xả tuần tự xuống đĩa (SSTable) → chịu tải ghi (write throughput) cực kỳ cao, đúng đặc thù của hệ thống chat.
  * Hơn nữa, tin nhắn chat luôn được đọc theo từng cuộc hội thoại (`channel_id`) và sắp theo thời gian (`message_id`). Khóa ghép `(channel_id, message_id)` trong [`MessageStore.kt`](../server/src/main/kotlin/com/chatdemo/server/MessageStore.kt) giúp gom toàn bộ tin nhắn cùng kênh vào chung một phân vùng có thứ tự sẵn.

---

### Câu 5: Tại sao phải sinh `message_id` bằng thuật toán Snowflake mà không lấy luôn `System.currentTimeMillis()`?
* **Trả lời bản chất:**
  * Nếu dùng `System.currentTimeMillis()` làm ID:
    1. **Hai tin gửi trong cùng 1 mili-giây** sẽ bị trùng ID.
    2. **Hiện tượng lệch đồng hồ vật lý (Clock Skew):** Đồng hồ của Chat Server 1 có thể nhanh hơn Chat Server 2 khoảng 15ms. Alice hỏi ở Server 1 lúc `t = 100`, Bob trả lời ngay ở Server 2 nhưng đồng hồ Server 2 mới chỉ `t = 90` → câu trả lời bị sắp xếp nằm **trước** câu hỏi!
  * Trong [`IdGenerator.kt`](../server/src/main/kotlin/com/chatdemo/server/IdGenerator.kt), bộ sinh mã kiểu **Snowflake 64-bit** ghép `41 bit timestamp + 10 bit nodeId + 12 bit sequence` (kèm cơ chế không cho đồng hồ lùi `if (now < lastTimestamp) now = lastTimestamp`) giúp ID luôn duy nhất và giữ đúng thứ tự tăng dần.

---

### Câu 6: Alice ở Chat Server 1 (`8081`), Bob ở Chat Server 2 (`8082`). Chuyện gì xảy ra bên dưới khi Alice bấm Gửi?
* **Trả lời theo đúng luồng code trong [`MessageRouter.kt`](../server/src/main/kotlin/com/chatdemo/server/MessageRouter.kt):**
  1. Gói `WsPacket.SendMessage` đi qua WebSocket của Alice lên `ChatWsServer` cổng `8081`.
  2. Server xin `messageId` mới từ `IdGenerator` và lưu ngay vào `MessageStore` (KV Store).
  3. Server trả gói `WsPacket.MessageAck` về cho thiết bị vừa gửi của Alice.
  4. `MessageRouter` kiểm tra danh bạ `userSessions["bob"]` → thấy Bob đang kết nối tại `chat-server-2 (8082)`.
  5. `MessageRouter` chuyển gói `WsPacket.DeliverMessage` sang node `chat-server-2`, và `chat-server-2` đẩy xuống ống WebSocket của Bob.

---

### Câu 7: Tại sao biến `cur_max_message_id` lại lưu ở từng thiết bị (Client) mà không lưu 1 biến chung cho cả tài khoản trên Server?
* **Trả lời bản chất:**
  * Một người dùng có thể có **Điện thoại** (đang bật mạng suốt, đã nhận tới tin `#105`) và **Laptop** (tắt từ hôm qua, mới nhận tới tin `#80`).
  * Nếu Server chỉ lưu 1 con số chung `#105` cho tài khoản Alice, thì khi mở Laptop lên, Laptop sẽ bị thiếu mất các tin từ `#81` đến `#105`!
  * Vì vậy mỗi thiết bị phải tự giữ `cur_max_message_id` của riêng mình (trong [`ChatController.kt`](../composeApp/src/commonMain/kotlin/com/chatdemo/app/ChatController.kt)) và gọi `/api/sync?userId=alice&afterId=<con_số_của_riêng_thiết_bị_đó>`.

---

### Câu 8: Tại sao Heartbeat không gửi mỗi 1 giây / timeout 2 giây để báo Offline tức thì?
* **Trả lời bản chất (Trade-off):**
  * **Quá tải mạng & hao pin:** 50 triệu người dùng gửi mỗi giây 1 gói = 50 triệu gói/giây chỉ để báo trạng thái, làm tốn tài nguyên server và hao pin thiết bị di động.
  * **Hiện tượng chớp tắt (Flapping):** Mạng di động 4G khi di chuyển thường xuyên bị rớt gói tin trong 1–2 giây. Nếu timeout quá ngắn (2s), chấm xanh của người dùng sẽ nhấp nháy Online/Offline liên tục gây trải nghiệm xấu.
  * Sách chọn **5s / 30s** (bản demo rút xuống **3s / 10s** trong [`ChannelHelper.kt`](../shared/src/commonMain/kotlin/com/chatdemo/shared/ChannelHelper.kt) để kịp trình chiếu trên lớp).

---

### Câu 9: Nếu một người nổi tiếng có 100.000 bạn bè, mỗi lần họ Online/Offline mà phát Pub/Sub cho 100.000 người thì sao?
* **Trả lời bản chất (Fan-out Storm):**
  * Nếu phát Pub/Sub cho 100.000 người mỗi lần trạng thái thay đổi sẽ gây **bão sự kiện (Event Storm)**.
  * Sách Alex Xu giải quyết bằng cách: chỉ dùng Pub/Sub đẩy real-time cho các nhóm/danh sách bạn bè nhỏ; với nhóm lớn hoặc tài khoản có quá nhiều người theo dõi, ứng dụng chỉ **kiểm tra trạng thái Online theo yêu cầu (Fetch on-demand)** khi người dùng mở màn hình chat hoặc làm mới danh sách.

---

### Câu 10: Tại sao trên máy ảo Android phải gõ Host là `10.0.2.2` còn trên Desktop lại là `127.0.0.1`?
* **Trả lời bản chất:**
  * Máy ảo Android (Android Emulator) chạy một hệ điều hành Linux riêng bên trong máy ảo QEMU. Đối với nó, `127.0.0.1` (`localhost`) là chính bản thân chiếc điện thoại ảo đó, không phải chiếc MacBook bên ngoài.
  * Android Emulator cung cấp địa chỉ IP ảo đặc biệt **`10.0.2.2`** làm cổng ánh xạ (loopback alias) trỏ thẳng ra `127.0.0.1` của máy tính chủ (Host Mac). Nhờ đó cả ứng dụng Android ảo (`10.0.2.2`) và ứng dụng Desktop (`127.0.0.1`) đều kết nối chung vào cùng một cụm Server Ktor đang chạy trên Mac.
