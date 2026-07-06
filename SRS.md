# SRS - Đặc tả yêu cầu phần mềm chức năng Sổ tiết kiệm

## 1. Thông tin tài liệu

| Mục | Nội dung |
| --- | --- |
| Tên hệ thống | Core Banking |
| Module | Tiết kiệm có kỳ hạn |
| Chức năng | Mở sổ tiết kiệm và tất toán sổ tiết kiệm |
| Phiên bản | 1.1 |
| Ngày cập nhật | 06/07/2026 |

## 2. Tóm tắt yêu cầu nghiệp vụ

Phòng Khách hàng cá nhân yêu cầu bổ sung API xử lý nghiệp vụ "Tất toán sổ tiết kiệm" cho hệ thống Core Banking. Khách hàng có thể gửi tiết kiệm theo kỳ hạn 1 tháng, 6 tháng hoặc 12 tháng. Khi khách hàng bấm tất toán, hệ thống phải xác định khách hàng rút tiền trước hạn, đúng hạn hay sau hạn để áp dụng lãi suất phù hợp.

Điểm nghiệp vụ quan trọng nhất là tránh thất thoát tiền của ngân hàng:

- Nếu khách hàng rút đúng ngày đáo hạn hoặc sau ngày đáo hạn, khách hàng được hưởng nguyên lãi suất có kỳ hạn ban đầu.
- Nếu khách hàng rút trước ngày đáo hạn, dù chỉ trước 1 ngày, toàn bộ tiền lãi phải tính lại theo lãi suất không kỳ hạn mặc định 0.1%/năm.
- Nếu sổ đã tất toán rồi, hệ thống không được cho tất toán lại vì sẽ gây chi trả tiền hai lần.

## 3. Phân tích yêu cầu nghiệp vụ

### 3.1. Nguồn yêu cầu

Yêu cầu được hiểu từ nội dung email nghiệp vụ:

```text
Mảng Tiết kiệm đang thiếu API xử lý logic "Tất toán sổ tiết kiệm".
Khách hàng có thể gửi tiết kiệm với kỳ hạn 1 tháng, 6 tháng, 12 tháng cùng mức lãi suất tương ứng.
Khách hàng có quyền bấm nút "Tất toán" rút tiền bất cứ lúc nào.
Khi API tất toán được gọi, hệ thống phải so sánh ngày tất toán thực tế với ngày đáo hạn của sổ.
Nếu rút đúng hoặc sau ngày đáo hạn: hưởng nguyên lãi suất có kỳ hạn ban đầu.
Nếu rút trước ngày đáo hạn: toàn bộ lãi tính lại theo lãi suất không kỳ hạn 0.1%/năm.
Logic phải cẩn thận để tránh thất thoát tiền của ngân hàng.
```

### 3.2. Phân rã yêu cầu nghiệp vụ

| Mã | Yêu cầu từ khách hàng | Phân tích nghiệp vụ | Hướng cài đặt |
| --- | --- | --- | --- |
| BRQ-01 | Có sổ tiết kiệm | Cần entity riêng để lưu tiền gốc, lãi suất, ngày gửi, ngày đáo hạn, trạng thái | Tạo `TermDeposit` |
| BRQ-02 | Kỳ hạn 1, 6, 12 tháng | Chỉ cho phép 3 kỳ hạn hợp lệ, không nhận kỳ hạn tự do | Validate `termMonths in [1, 6, 12]` |
| BRQ-03 | Khách hàng rút bất kỳ lúc nào | API tất toán không yêu cầu phải đến hạn | Cho phép gọi `/settle` khi sổ đang `ACTIVE` |
| BRQ-04 | So sánh ngày tất toán với ngày đáo hạn | Đây là điểm quyết định nhánh lãi suất | Dùng `settlementDate.isBefore(maturityDate)` |
| BRQ-05 | Đúng/sau hạn hưởng lãi suất ban đầu | Không giảm lãi nếu đã đủ ngày đáo hạn | Dùng `annualInterestRate` |
| BRQ-06 | Trước hạn dùng lãi không kỳ hạn | Toàn bộ lãi tính lại, không chỉ phần ngày rút trước | Dùng `demandInterestRate = 0.001` cho toàn bộ số ngày gửi |
| BRQ-07 | Tránh thất thoát tiền | Không được trả lãi kỳ hạn cho sổ rút trước hạn, không tất toán 2 lần | Có `status`, exception HTTP 400 |

### 3.3. Đối tượng liên quan

| Đối tượng | Vai trò |
| --- | --- |
| Khách hàng cá nhân | Người mở sổ và yêu cầu tất toán |
| Giao dịch viên/API client | Gọi API mở sổ, tất toán, tra cứu sổ |
| Phòng Khách hàng cá nhân | Chủ sở hữu yêu cầu nghiệp vụ |
| Hệ thống Core Banking | Lưu dữ liệu, tính lãi, cập nhật trạng thái |
| Ngân hàng | Chịu rủi ro thất thoát nếu logic tính lãi sai |

### 3.4. Giả định nghiệp vụ

- Lãi suất đầu vào được nhập theo dạng thập phân: 6%/năm là `0.06`, 0.1%/năm là `0.001`.
- Lãi suất không kỳ hạn mặc định cố định là `0.001`, tương ứng 0.1%/năm.
- Một năm quy ước 365 ngày.
- Số ngày gửi thực tế được tính bằng thư viện ngày tháng của Java, không tự quy đổi mỗi tháng thành 30 ngày.
- Ngày đáo hạn được tính bằng `openedDate.plusMonths(termMonths)`.
- Ngày tất toán mặc định là ngày hiện tại nếu API không truyền `settlementDate`.
- Sổ đã tất toán là trạng thái cuối, không được quay lại trạng thái đang hoạt động.

### 3.5. Rủi ro nghiệp vụ nếu xử lý sai

| Rủi ro | Ví dụ | Hậu quả |
| --- | --- | --- |
| Tính sai nhánh lãi suất | Rút trước hạn nhưng vẫn dùng lãi suất 6%/năm | Ngân hàng trả thừa tiền lãi |
| Tính sai số ngày gửi | Tự lấy mỗi tháng 30 ngày thay vì ngày thực tế | Sai tiền lãi ở tháng 28, 29, 31 ngày |
| Cho tất toán lại | API `/settle` bị gọi 2 lần | Khách hàng có thể được chi trả 2 lần |
| Dùng `double` cho tiền | Sai số thập phân khi nhân chia tiền tệ | Lệch tiền khi giao dịch lớn |
| Không lưu lãi suất áp dụng | Sau tất toán không biết đã dùng lãi kỳ hạn hay không kỳ hạn | Khó đối soát và kiểm toán |

## 4. Mục tiêu hệ thống

Hệ thống cần đạt các mục tiêu sau:

- Có entity `TermDeposit` lưu đầy đủ thông tin sổ tiết kiệm.
- Có API mở sổ tiết kiệm.
- Có API tất toán sổ tiết kiệm.
- Tính tiền lãi dựa trên số ngày gửi thực tế.
- Rẽ nhánh đúng giữa lãi suất có kỳ hạn và không kỳ hạn.
- Lưu lại kết quả tất toán để phục vụ đối soát.
- Trả lỗi rõ ràng khi dữ liệu không hợp lệ.
- Chặn tất toán lần hai bằng HTTP 400.
- Tích hợp vào cấu trúc Spring Boot hiện có, không phá vỡ các API cũ.

## 5. Phạm vi

### 5.1. Trong phạm vi

- Thiết kế entity `TermDeposit`.
- Mapping `TermDeposit` với `Customer`.
- Mapping tùy chọn `TermDeposit` với `BankAccount`.
- Thiết kế DTO request/response.
- Viết repository JPA cho `TermDeposit`.
- Viết service xử lý mở sổ và tất toán.
- Viết controller REST API.
- Xử lý exception nghiệp vụ.
- Viết unit test cho logic lãi suất và lỗi tất toán lần hai.
- Viết tài liệu SRS và Prompt History.

### 5.2. Ngoài phạm vi

- Không xây dựng giao diện người dùng.
- Không gửi email/SMS sau khi mở sổ hoặc tất toán.
- Không tự động tất toán bằng scheduler.
- Không xử lý tái tục sổ tiết kiệm.
- Không tính thuế thu nhập cá nhân trên lãi.
- Không thay đổi nghiệp vụ đăng ký, đăng nhập, JWT sẵn có.

## 6. Bối cảnh kỹ thuật hệ thống hiện tại

Dự án base là Java Spring Boot, dùng Gradle, JPA, MySQL, Lombok, Spring Security JWT và đã có các thành phần:

| Thành phần | File | Vai trò |
| --- | --- | --- |
| Entity khách hàng | `Customer.java` | Lưu thông tin khách hàng |
| Entity tài khoản | `BankAccount.java` | Lưu số dư, loại tài khoản, trạng thái |
| Repository khách hàng | `CustomerRepository.java` | Truy vấn `Customer` |
| Repository tài khoản | `BankAccountRepository.java` | Truy vấn `BankAccount` |
| Response chuẩn | `ApiResponse.java` | Chuẩn hóa body response |
| Exception nghiệp vụ | `BusinessException.java` | Mang mã lỗi và message |
| Handler lỗi | `GlobalExceptionHandler.java` | Chuyển exception thành HTTP response |
| Security | `SecurityConfig.java` | Cấu hình route và JWT |

Chức năng mới được thiết kế theo đúng cấu trúc package hiện có:

- Entity đặt trong `com.banking.models.entities`.
- DTO đặt trong `com.banking.models.dto`.
- Repository đặt trong `com.banking.models.repositories`.
- Service đặt trong `com.banking.models.services`.
- Controller đặt trong `com.banking.controllers`.
- Enum trạng thái đặt trong `com.banking.models.constant`.

## 7. Thuật ngữ

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| Sổ tiết kiệm | Khoản tiền gửi có kỳ hạn của khách hàng |
| Tiền gốc | Số tiền khách hàng gửi ban đầu |
| Lãi suất có kỳ hạn | Lãi suất cam kết khi mở sổ |
| Lãi suất không kỳ hạn | Lãi suất áp dụng khi rút trước hạn, mặc định 0.1%/năm |
| Ngày gửi | Ngày mở sổ tiết kiệm |
| Ngày đáo hạn | Ngày kết thúc kỳ hạn |
| Ngày tất toán | Ngày khách hàng đóng sổ và nhận tiền |
| Tất toán trước hạn | Ngày tất toán nhỏ hơn ngày đáo hạn |
| Tất toán đúng hạn | Ngày tất toán bằng ngày đáo hạn |
| Tất toán sau hạn | Ngày tất toán lớn hơn ngày đáo hạn |
| Số ngày gửi thực tế | Số ngày giữa ngày gửi và ngày tất toán |

## 8. Mô hình nghiệp vụ

### 8.1. Vòng đời sổ tiết kiệm

```text
Mở sổ thành công
        |
        v
ACTIVE - khách hàng có thể tất toán bất cứ lúc nào
        |
        v
SETTLED - trạng thái cuối, không được tất toán lại
```

### 8.2. Luồng mở sổ tiết kiệm

```text
Nhận request mở sổ
    -> kiểm tra khách hàng tồn tại
    -> kiểm tra kỳ hạn hợp lệ
    -> nếu có tài khoản nguồn thì kiểm tra tài khoản và số dư
    -> tính ngày đáo hạn
    -> tạo mã sổ
    -> lưu TermDeposit trạng thái ACTIVE
    -> trả response
```

### 8.3. Luồng tất toán sổ tiết kiệm

```text
Nhận request tất toán
    -> tìm sổ tiết kiệm
    -> nếu không tồn tại thì lỗi 400
    -> nếu đã SETTLED thì lỗi 400
    -> xác định ngày tất toán
    -> tính số ngày gửi thực tế
    -> nếu ngày tất toán trước ngày đáo hạn thì dùng lãi không kỳ hạn
    -> nếu ngày tất toán đúng/sau ngày đáo hạn thì dùng lãi có kỳ hạn
    -> tính tiền lãi
    -> tính tổng tiền tất toán
    -> cập nhật trạng thái SETTLED
    -> nếu có tài khoản liên kết thì cộng tiền tất toán về tài khoản
    -> trả response
```

## 9. Yêu cầu chức năng

### FR-01: Mở sổ tiết kiệm

Hệ thống cho phép tạo sổ tiết kiệm mới cho khách hàng.

Đầu vào:

| Trường | Bắt buộc | Ràng buộc |
| --- | --- | --- |
| `customerId` | Có | Khách hàng phải tồn tại |
| `bankAccountId` | Không | Nếu truyền thì tài khoản phải thuộc khách hàng và đang `ACTIVE` |
| `principalAmount` | Có | Lớn hơn 0 |
| `annualInterestRate` | Có | Lớn hơn 0, tối đa 1.0 |
| `termMonths` | Có | Chỉ nhận 1, 6, 12 |
| `openedDate` | Không | Không được lớn hơn ngày hiện tại |

Xử lý chính:

- Nếu không truyền `openedDate`, hệ thống lấy `LocalDate.now()`.
- Tính `maturityDate = openedDate.plusMonths(termMonths)`.
- Gán `demandInterestRate = 0.001`.
- Gán `status = ACTIVE`.
- Nếu có tài khoản liên kết, trừ tiền gốc khỏi số dư tài khoản.

Đầu ra:

- Thông tin sổ tiết kiệm vừa mở.
- Mã sổ tiết kiệm duy nhất.
- Ngày đáo hạn.
- Trạng thái `ACTIVE`.

### FR-02: Tất toán sổ tiết kiệm

Hệ thống cho phép tất toán sổ tiết kiệm đang hoạt động.

Đầu vào:

| Trường | Bắt buộc | Ràng buộc |
| --- | --- | --- |
| `id` | Có | Sổ tiết kiệm phải tồn tại |
| `settlementDate` | Không | Không được trước ngày gửi, mặc định là ngày hiện tại |

Xử lý chính:

- Kiểm tra trạng thái sổ.
- Nếu `status = SETTLED`, trả HTTP 400.
- Tính số ngày gửi thực tế.
- Chọn lãi suất áp dụng.
- Tính tiền lãi.
- Tính tổng tiền tất toán.
- Lưu các giá trị đối soát.
- Cập nhật trạng thái `SETTLED`.

Đầu ra:

- Ngày tất toán.
- Số ngày gửi thực tế.
- Lãi suất áp dụng.
- Cờ `earlySettlement`.
- Tiền lãi.
- Tổng tiền tất toán.
- Trạng thái `SETTLED`.

### FR-03: Tra cứu chi tiết sổ tiết kiệm

Hệ thống cho phép tra cứu một sổ tiết kiệm theo `id`. Nếu không tìm thấy, trả HTTP 400.

### FR-04: Tra cứu danh sách sổ theo khách hàng

Hệ thống cho phép lấy danh sách sổ tiết kiệm theo `customerId`. Nếu khách hàng không tồn tại, trả HTTP 400.

### FR-05: Chặn tất toán lần hai

Nếu API tất toán được gọi lại với sổ đã `SETTLED`, hệ thống phải trả:

```text
HTTP 400 Bad Request
Message: Sổ tiết kiệm đã được tất toán trước đó
```

## 10. Quy tắc nghiệp vụ

### BR-01: Kỳ hạn hợp lệ

Chỉ hỗ trợ:

- 1 tháng.
- 6 tháng.
- 12 tháng.

Kỳ hạn khác bị từ chối bằng HTTP 400.

### BR-02: Tính ngày đáo hạn

```text
maturityDate = openedDate.plusMonths(termMonths)
```

Ví dụ:

| Ngày gửi | Kỳ hạn | Ngày đáo hạn |
| --- | --- | --- |
| 2026-01-01 | 1 tháng | 2026-02-01 |
| 2026-01-01 | 6 tháng | 2026-07-01 |
| 2026-01-01 | 12 tháng | 2027-01-01 |

### BR-03: Tính số ngày gửi thực tế

Hệ thống dùng thư viện Java:

```java
ChronoUnit.DAYS.between(openedDate, settlementDate)
```

Không dùng cách tính thủ công theo tháng 30 ngày vì có thể sai ở tháng 28, 29 hoặc 31 ngày.

### BR-04: Chọn lãi suất áp dụng

```text
Nếu settlementDate < maturityDate:
    appliedRate = demandInterestRate
    earlySettlement = true

Nếu settlementDate >= maturityDate:
    appliedRate = annualInterestRate
    earlySettlement = false
```

Giải thích:

- Trước hạn: mất quyền hưởng lãi suất kỳ hạn, toàn bộ thời gian gửi tính theo lãi không kỳ hạn.
- Đúng hạn: đủ điều kiện hưởng lãi suất kỳ hạn.
- Sau hạn: vẫn hưởng lãi suất kỳ hạn ban đầu theo yêu cầu đề bài.

### BR-05: Công thức tính tiền lãi

```text
interestAmount = principalAmount * appliedRate * actualDepositDays / 365
```

Trong đó:

- `principalAmount`: tiền gốc.
- `appliedRate`: lãi suất năm thực tế được áp dụng.
- `actualDepositDays`: số ngày gửi thực tế.
- `365`: số ngày quy ước trong năm.

### BR-06: Công thức tính tổng tiền tất toán

```text
settlementAmount = principalAmount + interestAmount
```

### BR-07: Làm tròn tiền

Tiền lãi và tổng tiền tất toán được làm tròn 2 chữ số thập phân bằng:

```java
RoundingMode.HALF_UP
```

### BR-08: Không tất toán lại

Sổ đã có `status = SETTLED` không được phép tất toán lần nữa.

Lỗi bắt buộc:

```text
HTTP 400
Sổ tiết kiệm đã được tất toán trước đó
```

## 11. Thuật toán tất toán

### 11.1. Pseudocode

```text
function settleTermDeposit(termDepositId, settlementDateInput):
    termDeposit = findById(termDepositId)
    if termDeposit does not exist:
        throw BusinessException(400, "Không tìm thấy sổ tiết kiệm")

    if termDeposit.status == SETTLED:
        throw BusinessException(400, "Sổ tiết kiệm đã được tất toán trước đó")

    settlementDate = settlementDateInput != null ? settlementDateInput : LocalDate.now()
    actualDays = DAYS.between(termDeposit.openedDate, settlementDate)

    if actualDays < 0:
        throw BusinessException(400, "Ngày tất toán không được trước ngày gửi")

    if settlementDate < termDeposit.maturityDate:
        appliedRate = termDeposit.demandInterestRate
        earlySettlement = true
    else:
        appliedRate = termDeposit.annualInterestRate
        earlySettlement = false

    interestAmount = principalAmount * appliedRate * actualDays / 365
    settlementAmount = principalAmount + interestAmount

    update termDeposit:
        settlementDate
        actualDepositDays
        interestAppliedRate
        interestAmount
        settlementAmount
        earlySettlement
        status = SETTLED

    if termDeposit.bankAccount exists:
        bankAccount.balance = bankAccount.balance + settlementAmount

    save and return response
```

### 11.2. Điều kiện rẽ nhánh cốt lõi

| Điều kiện | Nhánh | Lãi suất áp dụng |
| --- | --- | --- |
| `settlementDate.isBefore(maturityDate)` | Trước hạn | `demandInterestRate = 0.001` |
| `settlementDate.isEqual(maturityDate)` | Đúng hạn | `annualInterestRate` |
| `settlementDate.isAfter(maturityDate)` | Sau hạn | `annualInterestRate` |

## 12. Yêu cầu dữ liệu

### 12.1. Entity `TermDeposit`

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
| --- | --- | --- | --- |
| `id` | `Long` | Có | Khóa chính |
| `depositNumber` | `String` | Có | Mã sổ tiết kiệm duy nhất |
| `principalAmount` | `BigDecimal` | Có | Tiền gốc |
| `annualInterestRate` | `BigDecimal` | Có | Lãi suất có kỳ hạn theo năm |
| `demandInterestRate` | `BigDecimal` | Có | Lãi suất không kỳ hạn, mặc định `0.001` |
| `termMonths` | `Integer` | Có | Kỳ hạn 1, 6 hoặc 12 tháng |
| `openedDate` | `LocalDate` | Có | Ngày mở sổ |
| `maturityDate` | `LocalDate` | Có | Ngày đáo hạn |
| `settlementDate` | `LocalDate` | Không | Ngày tất toán |
| `actualDepositDays` | `Long` | Không | Số ngày gửi thực tế |
| `interestAppliedRate` | `BigDecimal` | Không | Lãi suất thực tế dùng khi tất toán |
| `interestAmount` | `BigDecimal` | Không | Tiền lãi |
| `settlementAmount` | `BigDecimal` | Không | Tổng tiền nhận khi tất toán |
| `earlySettlement` | `Boolean` | Không | Có rút trước hạn hay không |
| `status` | `TermDepositStatus` | Có | `ACTIVE` hoặc `SETTLED` |
| `customer` | `Customer` | Có | Khách hàng sở hữu sổ |
| `bankAccount` | `BankAccount` | Không | Tài khoản thanh toán liên kết |
| `createdAt` | `LocalDateTime` | Có | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | Có | Thời điểm cập nhật |

### 12.2. Quan hệ dữ liệu

| Quan hệ | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `Customer` - `TermDeposit` | 1 - N | Một khách hàng có nhiều sổ tiết kiệm |
| `TermDeposit` - `Customer` | N - 1 | Một sổ thuộc một khách hàng |
| `TermDeposit` - `BankAccount` | N - 1, tùy chọn | Một sổ có thể liên kết tài khoản thanh toán |

### 12.3. Enum trạng thái

```java
public enum TermDepositStatus {
    ACTIVE,
    SETTLED
}
```

Ý nghĩa:

- `ACTIVE`: sổ đang hoạt động, có thể tất toán.
- `SETTLED`: sổ đã tất toán, không được tất toán lại.

## 13. Đặc tả API

### 13.1. API mở sổ tiết kiệm

```http
POST /api/v1/term-deposits/open
Content-Type: application/json
```

Request:

```json
{
  "customerId": 1,
  "bankAccountId": 2,
  "principalAmount": 100000000,
  "annualInterestRate": 0.06,
  "termMonths": 6,
  "openedDate": "2026-01-01"
}
```

Response:

```json
{
  "data": {
    "id": 1,
    "depositNumber": "TD-ABC12345",
    "customerId": 1,
    "customerName": "Nguyen Van A",
    "bankAccountId": 2,
    "principalAmount": 100000000,
    "annualInterestRate": 0.06,
    "demandInterestRate": 0.001,
    "termMonths": 6,
    "openedDate": "2026-01-01",
    "maturityDate": "2026-07-01",
    "settlementDate": null,
    "actualDepositDays": null,
    "interestAppliedRate": null,
    "interestAmount": 0,
    "settlementAmount": 0,
    "earlySettlement": null,
    "status": "ACTIVE"
  },
  "message": "Mở sổ tiết kiệm thành công",
  "code": 200
}
```

Ghi chú: HTTP status là `201 Created`, body dùng `ApiResponse.success()` nên trường `code` trong body là `200` theo cấu trúc base code.

### 13.2. API tất toán sổ tiết kiệm

```http
POST /api/v1/term-deposits/{id}/settle
Content-Type: application/json
```

Request có thể truyền ngày tất toán để kiểm thử:

```json
{
  "settlementDate": "2026-07-01"
}
```

Nếu request body rỗng hoặc không truyền `settlementDate`, hệ thống dùng ngày hiện tại.

Response đúng hạn:

```json
{
  "data": {
    "id": 1,
    "depositNumber": "TD-ABC12345",
    "principalAmount": 100000000,
    "annualInterestRate": 0.06,
    "demandInterestRate": 0.001,
    "termMonths": 6,
    "openedDate": "2026-01-01",
    "maturityDate": "2026-07-01",
    "settlementDate": "2026-07-01",
    "actualDepositDays": 181,
    "interestAppliedRate": 0.06,
    "interestAmount": 2975342.47,
    "settlementAmount": 102975342.47,
    "earlySettlement": false,
    "status": "SETTLED"
  },
  "message": "Tất toán sổ tiết kiệm thành công",
  "code": 200
}
```

Response lỗi tất toán lần hai:

```json
{
  "data": null,
  "message": "Sổ tiết kiệm đã được tất toán trước đó",
  "code": 400
}
```

### 13.3. API xem chi tiết sổ

```http
GET /api/v1/term-deposits/{id}
```

### 13.4. API xem danh sách sổ theo khách hàng

```http
GET /api/v1/term-deposits/customers/{customerId}
```

## 14. Bảng lỗi nghiệp vụ

| Mã lỗi | Tình huống | Message |
| --- | --- | --- |
| 400 | Không tìm thấy khách hàng | `Không tìm thấy khách hàng` |
| 400 | Kỳ hạn không hợp lệ | `Kỳ hạn chỉ hỗ trợ 1 tháng, 6 tháng hoặc 12 tháng` |
| 400 | Không tìm thấy tài khoản thanh toán | `Không tìm thấy tài khoản thanh toán` |
| 400 | Tài khoản không thuộc khách hàng | `Tài khoản thanh toán không thuộc khách hàng này` |
| 400 | Tài khoản không hoạt động | `Tài khoản thanh toán không ở trạng thái hoạt động` |
| 400 | Số dư không đủ | `Số dư tài khoản không đủ để mở sổ tiết kiệm` |
| 400 | Không tìm thấy sổ tiết kiệm | `Không tìm thấy sổ tiết kiệm` |
| 400 | Ngày tất toán trước ngày gửi | `Ngày tất toán không được trước ngày gửi` |
| 400 | Tất toán lại | `Sổ tiết kiệm đã được tất toán trước đó` |

## 15. Ví dụ tính toán

### 15.1. Tất toán đúng hạn

Dữ liệu:

- Tiền gốc: 100,000,000 VND.
- Lãi suất kỳ hạn: 6%/năm = `0.06`.
- Ngày gửi: 01/01/2025.
- Kỳ hạn: 6 tháng.
- Ngày đáo hạn: 01/07/2025.
- Ngày tất toán: 01/07/2025.
- Số ngày gửi thực tế: 181 ngày.

Vì `settlementDate = maturityDate`, dùng lãi suất kỳ hạn:

```text
interestAmount = 100,000,000 * 0.06 * 181 / 365
               = 2,975,342.47 VND

settlementAmount = 100,000,000 + 2,975,342.47
                 = 102,975,342.47 VND
```

### 15.2. Tất toán sau hạn

Dữ liệu:

- Tiền gốc: 100,000,000 VND.
- Lãi suất kỳ hạn: 6%/năm = `0.06`.
- Ngày gửi: 01/01/2025.
- Ngày đáo hạn: 01/07/2025.
- Ngày tất toán: 15/07/2025.
- Số ngày gửi thực tế: 195 ngày.

Vì `settlementDate > maturityDate`, vẫn dùng lãi suất kỳ hạn ban đầu:

```text
interestAmount = 100,000,000 * 0.06 * 195 / 365
               = 3,205,479.45 VND

settlementAmount = 103,205,479.45 VND
```

### 15.3. Tất toán trước hạn

Dữ liệu:

- Tiền gốc: 100,000,000 VND.
- Lãi suất kỳ hạn ban đầu: 6%/năm = `0.06`.
- Lãi suất không kỳ hạn: 0.1%/năm = `0.001`.
- Ngày gửi: 01/01/2026.
- Ngày đáo hạn: 01/07/2026.
- Ngày tất toán: 01/03/2026.
- Số ngày gửi thực tế: 59 ngày.

Vì `settlementDate < maturityDate`, toàn bộ lãi tính lại theo lãi suất không kỳ hạn:

```text
interestAmount = 100,000,000 * 0.001 * 59 / 365
               = 16,164.38 VND

settlementAmount = 100,016,164.38 VND
```

So sánh rủi ro:

```text
Nếu tính sai theo lãi kỳ hạn:
100,000,000 * 0.06 * 59 / 365 = 969,863.01 VND

Số tiền ngân hàng có thể trả thừa:
969,863.01 - 16,164.38 = 953,698.63 VND
```

Đây là lý do logic rẽ nhánh trước hạn rất quan trọng.

## 16. Use case chi tiết

### UC-01: Mở sổ tiết kiệm

| Mục | Nội dung |
| --- | --- |
| Actor | API client hoặc giao dịch viên |
| Tiền điều kiện | Khách hàng tồn tại |
| Kích hoạt | Gửi request `POST /api/v1/term-deposits/open` |
| Hậu điều kiện thành công | Sổ được tạo trạng thái `ACTIVE` |

Luồng chính:

1. Actor gửi thông tin mở sổ.
2. Hệ thống kiểm tra khách hàng.
3. Hệ thống kiểm tra kỳ hạn.
4. Hệ thống kiểm tra tài khoản liên kết nếu có.
5. Hệ thống tính ngày đáo hạn.
6. Hệ thống tạo sổ tiết kiệm.
7. Hệ thống trả thông tin sổ.

Luồng ngoại lệ:

- Khách hàng không tồn tại: HTTP 400.
- Kỳ hạn không hợp lệ: HTTP 400.
- Tài khoản không đủ số dư: HTTP 400.

### UC-02: Tất toán sổ tiết kiệm trước hạn

| Mục | Nội dung |
| --- | --- |
| Actor | API client hoặc giao dịch viên |
| Tiền điều kiện | Sổ tồn tại và đang `ACTIVE` |
| Kích hoạt | Gửi request `POST /api/v1/term-deposits/{id}/settle` |
| Hậu điều kiện thành công | Sổ chuyển sang `SETTLED` |

Luồng chính:

1. Actor gửi yêu cầu tất toán.
2. Hệ thống tìm sổ.
3. Hệ thống thấy ngày tất toán trước ngày đáo hạn.
4. Hệ thống chọn lãi suất không kỳ hạn `0.001`.
5. Hệ thống tính lãi theo số ngày gửi thực tế.
6. Hệ thống cập nhật trạng thái `SETTLED`.
7. Hệ thống trả kết quả tất toán.

### UC-03: Tất toán đúng hạn hoặc sau hạn

Luồng chính:

1. Actor gửi yêu cầu tất toán.
2. Hệ thống tìm sổ.
3. Hệ thống thấy ngày tất toán bằng hoặc sau ngày đáo hạn.
4. Hệ thống chọn lãi suất kỳ hạn ban đầu.
5. Hệ thống tính tiền lãi.
6. Hệ thống cập nhật trạng thái `SETTLED`.
7. Hệ thống trả kết quả tất toán.

### UC-04: Tất toán lại sổ đã tất toán

Luồng lỗi:

1. Actor gọi API tất toán cho sổ đã `SETTLED`.
2. Hệ thống kiểm tra trạng thái.
3. Hệ thống từ chối xử lý.
4. Hệ thống trả HTTP 400 với message `Sổ tiết kiệm đã được tất toán trước đó`.

## 17. Yêu cầu phi chức năng

- Tính toán tiền tệ dùng `BigDecimal`.
- Ngày tháng dùng `LocalDate`.
- Số ngày gửi thực tế dùng `ChronoUnit.DAYS`.
- Không dùng `double` hoặc `float` cho tiền.
- API trả về theo `ApiResponse`.
- Exception nghiệp vụ dùng `BusinessException`.
- Code giữ cấu trúc package hiện có.
- Không làm hỏng entity `Customer` và `BankAccount` cũ.
- Có test tự động cho các nhánh quan trọng.

## 18. Tiêu chí nghiệm thu

| Mã | Tiêu chí | Kết quả mong đợi |
| --- | --- | --- |
| AC-01 | Mở sổ kỳ hạn 1 tháng | Tạo sổ `ACTIVE`, ngày đáo hạn cộng 1 tháng |
| AC-02 | Mở sổ kỳ hạn 6 tháng | Tạo sổ `ACTIVE`, ngày đáo hạn cộng 6 tháng |
| AC-03 | Mở sổ kỳ hạn 12 tháng | Tạo sổ `ACTIVE`, ngày đáo hạn cộng 12 tháng |
| AC-04 | Mở sổ kỳ hạn khác 1/6/12 | Trả HTTP 400 |
| AC-05 | Tất toán trước hạn | Dùng lãi suất `0.001`, `earlySettlement = true` |
| AC-06 | Tất toán đúng hạn | Dùng `annualInterestRate`, `earlySettlement = false` |
| AC-07 | Tất toán sau hạn | Dùng `annualInterestRate`, `earlySettlement = false` |
| AC-08 | Tất toán lại | Trả HTTP 400 đúng message |
| AC-09 | Sổ có tài khoản liên kết | Trừ tiền khi mở, cộng tiền khi tất toán |
| AC-10 | Chạy test | `gradlew test` thành công |

## 19. Test case đề xuất

| Test case | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- |
| TC-01 | `principal=100000000`, `rate=0.06`, mở 6 tháng | Tạo sổ `ACTIVE` |
| TC-02 | `termMonths=3` | HTTP 400 |
| TC-03 | Tất toán 01/03/2026 cho sổ mở 01/01/2026 đáo hạn 01/07/2026 | Lãi `16,164.38`, rate `0.001` |
| TC-04 | Tất toán 01/07/2025 cho sổ mở 01/01/2025 đáo hạn 01/07/2025 | Lãi `2,975,342.47`, rate `0.06` |
| TC-05 | Gọi tất toán lần hai | HTTP 400, message đúng |

## 20. Ma trận truy vết yêu cầu

| Yêu cầu đề bài | Thành phần đáp ứng |
| --- | --- |
| Thiết kế entity `TermDeposit` gồm tiền gốc, lãi suất, ngày gửi, ngày đáo hạn | `TermDeposit.java`, mục 12 |
| Viết thuật toán tính lãi theo số ngày gửi thực tế | `TermDepositService.calculateInterest()`, mục 10, 11 |
| Logic rẽ nhánh có kỳ hạn vs không kỳ hạn | `settlementDate.isBefore(maturityDate)`, mục 11.2 |
| Mapping với `Customer` hoặc `BankAccount` | `@ManyToOne` trong `TermDeposit.java`, mục 12.2 |
| API mở sổ | `POST /api/v1/term-deposits/open`, mục 13.1 |
| API tất toán | `POST /api/v1/term-deposits/{id}/settle`, mục 13.2 |
| Dùng `LocalDate` | Entity, DTO, Service, mục 10 và 11 |
| Chặn tất toán lần hai HTTP 400 | `BusinessException(400, ...)`, mục 14 |
| Không phá cấu trúc cũ | Bổ sung file mới theo package hiện có, mục 6 |
| Có test/debug | `TermDepositServiceTest`, mục 19 |

## 21. Rà soát theo tiêu chí chấm điểm

| Tiêu chí | Điểm | Bằng chứng trong bài |
| --- | --- | --- |
| Tư duy phân tích SRS | 20 | Mục 3 phân tích nghiệp vụ, mục 10 quy tắc, mục 15 ví dụ tính toán |
| Kỹ năng điều hướng AI | 30 | `Prompt_History.md` có prompt cung cấp context base code, prompt `LocalDate`, prompt debug |
| Chất lượng code AI sinh ra | 35 | Entity, repository, service, controller, DTO, test; logic if/else rõ |
| Xử lý lỗi và tối ưu | 15 | HTTP 400 khi tất toán lần hai, H2 test, unit test nghiệp vụ |

### 21.1. Checklist chi tiết theo rubric

| Câu hỏi chấm điểm | Câu trả lời trong bài | Vị trí bằng chứng |
| --- | --- | --- |
| SRS có công thức tính lãi không? | Có công thức `interestAmount = principalAmount * appliedRate * actualDepositDays / 365` | Mục 10, 11, 15 |
| SRS có logic rẽ nhánh trước hạn/đúng hạn không? | Có điều kiện `settlementDate < maturityDate` dùng lãi không kỳ hạn, `settlementDate >= maturityDate` dùng lãi kỳ hạn | Mục 10.4, 11.2 |
| Prompt có cho AI đọc hiểu base code không? | Có mô tả package, `Customer`, `BankAccount`, repository, `ApiResponse`, `BusinessException` | `Prompt_History.md`, Prompt 2 |
| Prompt có yêu cầu xử lý thư viện thời gian Java không? | Có yêu cầu dùng `LocalDate`, `ChronoUnit.DAYS`, không tính tháng 30 ngày | `Prompt_History.md`, Prompt 7 |
| Entity có map đúng không? | `TermDeposit` map `Customer` bắt buộc và `BankAccount` tùy chọn | `TermDeposit.java`, mục 12 |
| API cốt lõi có đủ không? | Có API mở sổ và tất toán | Mục 13 |
| Logic if/else nghiệp vụ có đúng không? | Trước hạn dùng `demandInterestRate`, đúng/sau hạn dùng `annualInterestRate` | `TermDepositService.java`, mục 11 |
| Có phá cấu trúc cũ không? | Không sửa nghiệp vụ auth/account cũ, chỉ thêm module mới theo package hiện có | Mục 6, 20 |
| Có HTTP 400 chặn tất toán hai lần không? | Có `BusinessException(400, "Sổ tiết kiệm đã được tất toán trước đó")` | Mục 14, 16, test case TC-05 |
| Có thể hiện nỗ lực debug/test không? | Có `gradlew test`, unit test nhánh trước hạn/đúng hạn/tất toán lại | Mục 19, `Prompt_History.md` |

## 22. Danh sách file cài đặt

- `src/main/java/com/banking/models/entities/TermDeposit.java`
- `src/main/java/com/banking/models/constant/TermDepositStatus.java`
- `src/main/java/com/banking/models/repositories/TermDepositRepository.java`
- `src/main/java/com/banking/models/dto/OpenTermDepositRequest.java`
- `src/main/java/com/banking/models/dto/SettleTermDepositRequest.java`
- `src/main/java/com/banking/models/dto/TermDepositResponse.java`
- `src/main/java/com/banking/models/services/TermDepositService.java`
- `src/main/java/com/banking/controllers/TermDepositController.java`
- `src/test/java/com/banking/models/services/TermDepositServiceTest.java`

## 23. Kết luận

Đặc tả này đã phân tích đầy đủ yêu cầu nghiệp vụ từ khách hàng, xác định rủi ro thất thoát tiền, mô tả entity, API, thuật toán tính lãi, logic rẽ nhánh trước hạn/đúng hạn/sau hạn, xử lý ngoại lệ và tiêu chí nghiệm thu. Phần cài đặt tương ứng đáp ứng yêu cầu bài tập và có test kiểm tra các nhánh quan trọng.
