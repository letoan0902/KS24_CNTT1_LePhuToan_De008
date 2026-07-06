# Báo cáo Prompt History - Chức năng Sổ tiết kiệm Core Banking

## 1. Tổng quan bài làm

Bài làm sử dụng AI như một trợ lý phân tích nghiệp vụ và kỹ thuật để bổ sung module sổ tiết kiệm có kỳ hạn cho dự án Core Banking Java Spring Boot.

Yêu cầu chính của đề bài:

- Phân tích và đặc tả yêu cầu trong `SRS.md`.
- Thiết kế entity `TermDeposit` gồm tiền gốc, lãi suất, ngày gửi, ngày đáo hạn.
- Viết thuật toán tính lãi theo số ngày gửi thực tế.
- Rẽ nhánh lãi suất:
  - Đúng hạn hoặc sau hạn: dùng lãi suất có kỳ hạn ban đầu.
  - Trước hạn: dùng lãi suất không kỳ hạn 0.1%/năm.
- Tạo API mở sổ tiết kiệm và tất toán sổ tiết kiệm.
- Mapping entity mới với `Customer` hoặc `BankAccount`.
- Dùng thư viện ngày tháng Java như `LocalDate`, `ChronoUnit.DAYS`.
- Xử lý exception HTTP 400 khi tất toán sổ đã tất toán.

Em không dùng một prompt duy nhất để yêu cầu AI sinh toàn bộ bài. Em chia quá trình thành nhiều lượt:

1. Phân tích nghiệp vụ từ yêu cầu khách hàng.
2. Cung cấp context base code hiện tại cho AI.
3. Thiết kế SRS trước khi viết code.
4. Sinh entity, DTO, repository, service, controller theo cấu trúc dự án.
5. Review điểm AI sinh ra còn thiếu hoặc chưa tối ưu.
6. Prompt lại để sửa logic ngày tháng, response DTO và exception.
7. Viết test và chạy build.
8. Rà soát theo rubric chấm điểm.

### 1.1. Cấu trúc file sau khi hoàn thiện

| Đường dẫn | Nội dung |
| --- | --- |
| `SRS.md` | Đặc tả yêu cầu, phân tích nghiệp vụ, công thức tính lãi, use case, test case |
| `Prompt_History.md` | Nhật ký prompt theo chuỗi, có đánh giá và điều hướng lại AI |
| `src/main/java/com/banking/models/entities/TermDeposit.java` | Entity sổ tiết kiệm |
| `src/main/java/com/banking/models/constant/TermDepositStatus.java` | Enum trạng thái `ACTIVE`, `SETTLED` |
| `src/main/java/com/banking/models/repositories/TermDepositRepository.java` | Repository JPA cho sổ tiết kiệm |
| `src/main/java/com/banking/models/dto/OpenTermDepositRequest.java` | DTO mở sổ |
| `src/main/java/com/banking/models/dto/SettleTermDepositRequest.java` | DTO tất toán |
| `src/main/java/com/banking/models/dto/TermDepositResponse.java` | DTO response, không trả entity trực tiếp |
| `src/main/java/com/banking/models/services/TermDepositService.java` | Logic nghiệp vụ mở sổ và tất toán |
| `src/main/java/com/banking/controllers/TermDepositController.java` | REST API mở sổ, tất toán, tra cứu |
| `src/test/java/com/banking/models/services/TermDepositServiceTest.java` | Unit test nhánh trước hạn, đúng hạn, tất toán lại |

### 1.2. Đối chiếu nhanh với tiêu chí chấm điểm

| Tiêu chí | Cách bài làm đáp ứng |
| --- | --- |
| Tư duy Phân tích SRS - 20 điểm | `SRS.md` có phân tích yêu cầu nghiệp vụ, rủi ro, công thức lãi, nhánh trước hạn/đúng hạn/sau hạn, ví dụ số cụ thể |
| Kỹ năng Điều hướng AI - 30 điểm | Prompt cung cấp context base code hiện tại, entity có sẵn, package, `ApiResponse`, `BusinessException`, yêu cầu `LocalDate`, `ChronoUnit.DAYS`, debug/test |
| Chất lượng Code AI sinh ra - 35 điểm | Có entity map với `Customer` và `BankAccount`, API mở sổ/tất toán, service tính lãi bằng `BigDecimal`, logic if/else đúng nghiệp vụ |
| Xử lý Lỗi & Tối ưu - 15 điểm | Có HTTP 400 khi tất toán lần hai, dùng `GlobalExceptionHandler`, có unit test chứng minh exception và nhánh lãi suất |

## 2. Phân tích yêu cầu nghiệp vụ bằng AI

### Prompt 1 - Yêu cầu AI phân tích nghiệp vụ trước khi viết code

```text
Bạn hãy đóng vai Senior Business Analyst kiêm Java Spring Boot Backend Engineer cho hệ thống Core Banking.

Bối cảnh nghiệp vụ:
Module Tiết kiệm có kỳ hạn đang thiếu API xử lý "Tất toán sổ tiết kiệm". Khách hàng cá nhân có thể gửi tiết kiệm với kỳ hạn 1 tháng, 6 tháng hoặc 12 tháng. Khi khách hàng bấm "Tất toán", hệ thống phải so sánh ngày tất toán thực tế với ngày đáo hạn của sổ.

Quy tắc nghiệp vụ bắt buộc:
1. Nếu ngày tất toán bằng hoặc sau ngày đáo hạn, khách hàng hưởng nguyên lãi suất có kỳ hạn ban đầu.
2. Nếu ngày tất toán trước ngày đáo hạn, dù chỉ trước 1 ngày, toàn bộ tiền lãi phải tính lại theo lãi suất không kỳ hạn mặc định 0.1%/năm.
3. Khách hàng có thể bấm tất toán bất cứ lúc nào, nhưng một sổ chỉ được tất toán một lần.
4. Nếu API tất toán bị gọi lại cho sổ đã tất toán, hệ thống phải trả HTTP 400 với message "Sổ tiết kiệm đã được tất toán trước đó".
5. Logic tính tiền phải cẩn thận để tránh thất thoát tiền của ngân hàng.

Nhiệm vụ:
- Phân rã yêu cầu thành business rule.
- Chỉ ra rủi ro nghiệp vụ nếu tính sai.
- Đề xuất dữ liệu cần lưu trong entity TermDeposit.
- Đề xuất API cốt lõi.
- Đề xuất test case cần có.

Chưa viết code ở bước này. Trả lời theo hướng có thể đưa vào SRS.
```

**AI trả lời tóm tắt:**

AI phân tích được các business rule chính:

- Kỳ hạn hợp lệ là 1, 6, 12 tháng.
- Ngày đáo hạn được tính từ ngày gửi cộng số tháng kỳ hạn.
- Nếu tất toán trước hạn thì dùng lãi suất không kỳ hạn `0.001`.
- Nếu tất toán đúng hạn hoặc sau hạn thì dùng `annualInterestRate`.
- Cần trạng thái sổ để chặn tất toán lại.
- Cần lưu số ngày gửi thực tế, lãi suất áp dụng, tiền lãi và tổng tiền tất toán để đối soát.

**Đánh giá của em sau Prompt 1:**

Câu trả lời đúng hướng về nghiệp vụ, nhưng mới dừng ở mức phân tích. Em cần prompt tiếp theo để AI hiểu base code cụ thể của dự án, vì nếu không cung cấp package/entity/repository hiện có, AI dễ sinh cấu trúc không khớp với project.

## 3. Cung cấp context base code cho AI

### Prompt 2 - Đưa AI đọc đúng cấu trúc dự án hiện tại

```text
Hãy phân tích cách thêm module TermDeposit dựa trên base code Spring Boot hiện tại. Không đề xuất kiến trúc mới ngoài cấu trúc đang có.

Thông tin project:
- Java 17.
- Spring Boot 3.2.4.
- Gradle.
- Spring Data JPA.
- Spring Web.
- Spring Security JWT.
- Lombok.
- MySQL ở production.

Package đang có:
- com.banking.models.entities
- com.banking.models.dto
- com.banking.models.repositories
- com.banking.models.services
- com.banking.controllers
- com.banking.advice
- com.banking.exceptions
- com.banking.security

Entity Customer hiện có:
- id: Long
- fullName: String
- email: String
- password: String
- phoneNumber: String
- identityNumber: String
- dateOfBirth: LocalDate
- address: String
- status: CustomerStatus
- role: String
- dùng JPA annotation và Lombok

Entity BankAccount hiện có:
- id: Long
- accountNumber: String
- balance: BigDecimal
- currency: String
- accountType: enum CHECKING, SAVINGS, CREDIT
- status: enum ACTIVE, LOCKED, CLOSED
- createdAt: LocalDateTime
- customer: ManyToOne Customer

Repository hiện có:
- CustomerRepository extends JpaRepository<Customer, Long>, có findByEmail
- BankAccountRepository extends JpaRepository<BankAccount, Long>

Response và exception chuẩn:
- ApiResponse<T> có data, message, code
- BusinessException(int code, String message)
- GlobalExceptionHandler bắt BusinessException và trả HTTP status theo code

Yêu cầu:
1. Đề xuất danh sách file cần thêm cho TermDeposit.
2. Chỉ ra file cũ cần sửa ít nhất có thể.
3. Không trả entity JPA trực tiếp ra API nếu có nguy cơ lazy loading hoặc lộ dữ liệu Customer.password.
4. Mapping TermDeposit với Customer và có thể mapping thêm BankAccount.
5. Logic ngày tháng bắt buộc dùng LocalDate/ChronoUnit, không tính thủ công theo tháng 30 ngày.
6. Tính tiền bắt buộc dùng BigDecimal.
```

**AI trả lời tóm tắt:**

AI đề xuất thêm các file:

- `TermDeposit.java`
- `TermDepositStatus.java`
- `TermDepositRepository.java`
- `OpenTermDepositRequest.java`
- `SettleTermDepositRequest.java`
- `TermDepositResponse.java`
- `TermDepositService.java`
- `TermDepositController.java`

AI đề xuất chỉ sửa nhẹ:

- `SecurityConfig.java` để cho phép gọi endpoint mới khi test API.
- `build.gradle` để thêm H2 cho test.
- `CoreBankingApplicationTests.java` để test không phụ thuộc MySQL.

**Đánh giá của em sau Prompt 2:**

AI đã hiểu đúng cấu trúc base code. Điểm tốt là AI không đề xuất tạo module hoặc package lạ. Tuy nhiên em cần kiểm soát kỹ phần response, vì nếu trả thẳng entity `TermDeposit`, JPA có thể serialize sang `Customer`, làm lộ các field nhạy cảm như `password`.

**Prompt điều hướng thêm sau khi đánh giá:**

```text
Không trả entity JPA trực tiếp trong controller. Hãy dùng TermDepositResponse để chỉ trả các field cần thiết:
- id
- depositNumber
- customerId
- customerName
- bankAccountId
- principalAmount
- annualInterestRate
- demandInterestRate
- termMonths
- openedDate
- maturityDate
- settlementDate
- actualDepositDays
- interestAppliedRate
- interestAmount
- settlementAmount
- earlySettlement
- status

Giải thích trong báo cáo rằng DTO response giúp tránh lazy loading và tránh lộ password từ Customer.
```

**Kết quả sau prompt điều hướng:**

AI chốt phương án dùng `TermDepositResponse`, không trả entity trực tiếp. Đây là điểm giúp code an toàn và phù hợp cấu trúc API.

## 4. Thiết kế SRS chi tiết

### Prompt 3 - Yêu cầu AI viết SRS theo tư duy phân tích

```text
Dựa trên yêu cầu nghiệp vụ và context base code đã phân tích, hãy viết SRS cho module TermDeposit.

SRS phải thể hiện tư duy phân tích chứ không chỉ liệt kê chức năng.

Cấu trúc bắt buộc:
1. Tóm tắt nghiệp vụ.
2. Phân tích yêu cầu từ email khách hàng.
3. Bảng phân rã yêu cầu nghiệp vụ: yêu cầu gốc, phân tích, hướng cài đặt.
4. Đối tượng liên quan: khách hàng, API client/giao dịch viên, phòng khách hàng cá nhân, hệ thống Core Banking, ngân hàng.
5. Giả định nghiệp vụ:
   - lãi suất dạng thập phân, ví dụ 0.06 cho 6%/năm.
   - 365 ngày/năm.
   - ngày đáo hạn = openedDate.plusMonths(termMonths).
6. Rủi ro nếu xử lý sai:
   - trả thừa lãi khi rút trước hạn.
   - tính sai ngày gửi thực tế.
   - tất toán hai lần.
   - dùng double cho tiền.
7. Yêu cầu chức năng FR cho mở sổ, tất toán, tra cứu, chặn tất toán lại.
8. Business rule BR cho kỳ hạn, ngày đáo hạn, số ngày thực tế, chọn lãi suất, công thức lãi.
9. Pseudocode thuật toán tất toán.
10. API request/response.
11. Bảng lỗi nghiệp vụ.
12. Ví dụ tính toán trước hạn, đúng hạn, sau hạn.
13. Use case chi tiết.
14. Traceability matrix nối yêu cầu đề bài với file code.
15. Rà soát theo rubric 100 điểm.

Công thức bắt buộc:
- actualDepositDays = ChronoUnit.DAYS.between(openedDate, settlementDate)
- nếu settlementDate < maturityDate thì appliedRate = demandInterestRate = 0.001
- nếu settlementDate >= maturityDate thì appliedRate = annualInterestRate
- interestAmount = principalAmount * appliedRate * actualDepositDays / 365
- settlementAmount = principalAmount + interestAmount
```

**AI trả lời tóm tắt:**

AI tạo bản SRS có đủ phần phân tích nghiệp vụ, business rule, thuật toán, API, bảng lỗi, ví dụ số, use case và ma trận truy vết.

**Điểm AI trả lời ban đầu còn thiếu:**

AI ban đầu có mô tả công thức nhưng chưa nhấn mạnh rủi ro "rút trước hạn dù chỉ 1 ngày vẫn phải tính lại toàn bộ lãi theo lãi không kỳ hạn". Đây là chi tiết quan trọng trong đề.

**Prompt sửa lại:**

```text
Hãy bổ sung vào SRS phần rủi ro nghiệp vụ bằng ví dụ số:
- Tiền gốc 100,000,000 VND.
- Rút trước hạn sau 59 ngày.
- Nếu tính sai theo lãi suất kỳ hạn 6%/năm thì lãi là 969,863.01 VND.
- Nếu tính đúng theo lãi không kỳ hạn 0.1%/năm thì lãi là 16,164.38 VND.
- Chênh lệch ngân hàng có thể trả thừa là 953,698.63 VND.

Mục tiêu là chứng minh vì sao nhánh trước hạn quan trọng để tránh thất thoát tiền.
```

**Kết quả sau prompt sửa:**

AI bổ sung ví dụ so sánh rủi ro trả thừa tiền lãi. Phần này được đưa vào `SRS.md` để tăng điểm tư duy phân tích.

## 5. Thiết kế entity và dữ liệu

### Prompt 4 - Sinh entity `TermDeposit`

```text
Hãy sinh entity JPA TermDeposit theo đúng style entity hiện có trong project.

Ràng buộc style:
- Package: com.banking.models.entities
- Dùng jakarta.persistence.*
- Dùng Lombok: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor, @Builder
- Dùng BigDecimal cho tiền và lãi suất
- Dùng LocalDate cho ngày nghiệp vụ
- Dùng LocalDateTime cho createdAt, updatedAt
- Không dùng double/float cho tiền

Field bắt buộc:
- id
- depositNumber unique, nullable=false
- principalAmount precision 19, scale 4
- annualInterestRate precision 10, scale 6
- demandInterestRate precision 10, scale 6
- termMonths
- openedDate
- maturityDate
- settlementDate
- actualDepositDays
- interestAppliedRate
- interestAmount
- settlementAmount
- earlySettlement
- status enum TermDepositStatus lưu EnumType.STRING
- customer ManyToOne LAZY, nullable=false
- bankAccount ManyToOne LAZY, nullable=true
- createdAt
- updatedAt

Lifecycle:
- @PrePersist set createdAt và updatedAt
- @PreUpdate set updatedAt

Ngoài ra tạo enum TermDepositStatus trong package com.banking.models.constant gồm ACTIVE và SETTLED.
```

**AI trả lời tóm tắt:**

AI tạo entity `TermDeposit` và enum `TermDepositStatus`.

**Đánh giá của em:**

Entity đáp ứng đúng yêu cầu đề bài vì có tiền gốc, lãi suất, ngày gửi, ngày đáo hạn. Em giữ thêm các field `actualDepositDays`, `interestAppliedRate`, `earlySettlement`, `settlementAmount` vì đây là dữ liệu cần thiết để đối soát sau tất toán.

### Prompt 5 - Sinh repository

```text
Hãy tạo TermDepositRepository theo Spring Data JPA.

Package:
- com.banking.models.repositories

Yêu cầu:
- extends JpaRepository<TermDeposit, Long>
- Optional<TermDeposit> findByDepositNumber(String depositNumber)
- boolean existsByDepositNumber(String depositNumber)
- List<TermDeposit> findByCustomerId(Long customerId)

Mục đích:
- existsByDepositNumber dùng khi sinh mã sổ duy nhất.
- findByCustomerId dùng API tra cứu danh sách sổ theo khách hàng.
```

**AI trả lời tóm tắt:**

AI tạo repository đúng chuẩn Spring Data JPA, không viết query thủ công vì tên method đủ rõ để JPA tự sinh truy vấn.

## 6. Thiết kế DTO và tránh lỗi serialize entity

### Prompt 6 - Sinh DTO request/response

```text
Hãy tạo DTO cho module TermDeposit theo package com.banking.models.dto.

OpenTermDepositRequest:
- customerId: Long, @NotNull
- bankAccountId: Long, optional
- principalAmount: BigDecimal, @NotNull, @DecimalMin("0.01")
- annualInterestRate: BigDecimal, @NotNull, @DecimalMin("0.0001"), @DecimalMax("1.0")
- termMonths: Integer, @NotNull
- openedDate: LocalDate, optional, @PastOrPresent

SettleTermDepositRequest:
- settlementDate: LocalDate, optional, @PastOrPresent

TermDepositResponse:
- id
- depositNumber
- customerId
- customerName
- bankAccountId
- principalAmount
- annualInterestRate
- demandInterestRate
- termMonths
- openedDate
- maturityDate
- settlementDate
- actualDepositDays
- interestAppliedRate
- interestAmount
- settlementAmount
- earlySettlement
- status

Ràng buộc:
- annualInterestRate nhập dạng thập phân, ví dụ 0.06 cho 6%/năm.
- Không trả Customer entity trong response.
- Không trả BankAccount entity trong response.
- Response phải đủ thông tin để người chấm thấy nhánh lãi suất đã áp dụng.
```

**AI trả lời tóm tắt:**

AI tạo `OpenTermDepositRequest`, `SettleTermDepositRequest`, `TermDepositResponse` với Jakarta Validation.

**Điểm AI có thể làm chưa tối ưu nếu không ràng buộc:**

Nếu không yêu cầu rõ DTO response, AI có thể để controller trả `TermDeposit` entity trực tiếp. Điều đó không tốt vì:

- Có thể kéo theo lazy loading `Customer`.
- Có nguy cơ lộ field `password`.
- Response khó kiểm soát.
- Dữ liệu trả ra không tập trung vào kết quả tính lãi.

**Cách em khắc phục:**

Em prompt rõ các field response cần trả, đặc biệt là `interestAppliedRate`, `actualDepositDays`, `earlySettlement` để chứng minh logic rẽ nhánh.

## 7. Viết service nghiệp vụ và kiểm soát công thức ngày tháng

### Prompt 7 - Sinh `TermDepositService`

```text
Hãy viết TermDepositService trong package com.banking.models.services.

Dependency cần inject:
- TermDepositRepository
- CustomerRepository
- BankAccountRepository

Hằng số:
- DEFAULT_DEMAND_INTEREST_RATE = new BigDecimal("0.001")
- DAYS_IN_YEAR = new BigDecimal("365")
- SUPPORTED_TERMS = Set.of(1, 6, 12)

Method openTermDeposit(OpenTermDepositRequest request):
1. validate termMonths chỉ thuộc 1, 6, 12.
2. tìm Customer theo customerId, không có thì throw BusinessException(400, "Không tìm thấy khách hàng").
3. nếu bankAccountId != null:
   - tìm BankAccount, không có thì lỗi 400.
   - kiểm tra bankAccount.customer.id bằng customerId.
   - kiểm tra status ACTIVE.
   - kiểm tra balance >= principalAmount.
   - trừ principalAmount khỏi balance.
4. openedDate = request.openedDate hoặc LocalDate.now().
5. maturityDate = openedDate.plusMonths(termMonths).
6. tạo TermDeposit status ACTIVE, demandInterestRate = 0.001.
7. sinh depositNumber dạng TD-XXXXXXXX và đảm bảo không trùng.
8. save và trả TermDepositResponse.

Method settleTermDeposit(Long id, SettleTermDepositRequest request):
1. tìm TermDeposit, không có thì BusinessException(400, "Không tìm thấy sổ tiết kiệm").
2. nếu status SETTLED thì BusinessException(400, "Sổ tiết kiệm đã được tất toán trước đó").
3. settlementDate = request.settlementDate hoặc LocalDate.now().
4. actualDays = ChronoUnit.DAYS.between(openedDate, settlementDate).
5. nếu actualDays < 0 thì lỗi "Ngày tất toán không được trước ngày gửi".
6. nếu settlementDate.isBefore(maturityDate) thì:
   appliedRate = demandInterestRate, earlySettlement = true.
7. ngược lại:
   appliedRate = annualInterestRate, earlySettlement = false.
8. interestAmount = principal * appliedRate * actualDays / 365, scale 2, HALF_UP.
9. settlementAmount = principal + interestAmount, scale 2, HALF_UP.
10. cập nhật các field tất toán và status SETTLED.
11. nếu có bankAccount thì cộng settlementAmount vào balance.
12. save và trả TermDepositResponse.

Yêu cầu bắt buộc:
- Dùng @Transactional.
- Dùng BigDecimal.divide(..., 2, RoundingMode.HALF_UP).
- Không dùng double.
- Không dùng Period.getMonths() để tính số ngày.
- Không tự giả định 1 tháng = 30 ngày.
- Không trả entity trực tiếp.
```

**AI trả lời tóm tắt:**

AI sinh `TermDepositService` có logic mở sổ và tất toán. Đoạn rẽ nhánh cốt lõi:

```java
boolean earlySettlement = settlementDate.isBefore(termDeposit.getMaturityDate());
BigDecimal appliedRate = earlySettlement
        ? termDeposit.getDemandInterestRate()
        : termDeposit.getAnnualInterestRate();
```

Công thức tính lãi:

```java
principalAmount
        .multiply(annualRate)
        .multiply(BigDecimal.valueOf(actualDays))
        .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);
```

**Điểm AI có thể sai nếu prompt không đủ chặt:**

AI thường có thể tính ngày bằng cách lấy số tháng nhân 30 hoặc dùng `Period.getMonths()`. Cách đó không đáp ứng đề bài vì yêu cầu tính theo số ngày gửi thực tế và dùng thư viện thời gian Java.

**Cách em khắc phục:**

Em ràng buộc rõ trong prompt:

- Dùng `ChronoUnit.DAYS.between(openedDate, settlementDate)`.
- Không dùng quy ước tháng 30 ngày.
- Không dùng `double` cho tiền.

Kết quả service đã dùng đúng `LocalDate`, `ChronoUnit.DAYS` và `BigDecimal`.

## 8. Kiểm tra exception tất toán lần hai

### Prompt 8 - Review riêng tiêu chí HTTP 400

```text
Hãy rà lại riêng yêu cầu exception vì đây là tiêu chí chấm điểm.

Base code đã có:
- BusinessException(int code, String message)
- GlobalExceptionHandler bắt BusinessException và trả ResponseEntity.status(ex.getCode()).body(ApiResponse.error(...))

Yêu cầu nghiệp vụ:
- Khi gọi API tất toán một sổ đã SETTLED, không được tính lãi lại.
- Phải dừng ngay trước khi cộng tiền vào BankAccount.
- Phải trả HTTP 400.
- Message chính xác: "Sổ tiết kiệm đã được tất toán trước đó".

Hãy chỉ ra đoạn code cần có trong TermDepositService và giải thích vì sao đặt check này trước phần tính lãi.
```

**AI trả lời tóm tắt:**

AI đề xuất đoạn code:

```java
if (TermDepositStatus.SETTLED.equals(termDeposit.getStatus())) {
    throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Sổ tiết kiệm đã được tất toán trước đó");
}
```

**Đánh giá của em:**

Đây là đoạn quan trọng cho tiêu chí "Xử lý Lỗi & Tối ưu". Check này phải đặt ngay sau khi tìm thấy sổ, trước khi tính lãi và trước khi cộng tiền vào tài khoản. Nếu đặt muộn, API có thể gây lỗi chi trả hai lần.

**Điểm AI làm đúng sau prompt này:**

AI không tạo exception mới mà tái sử dụng `BusinessException` và `GlobalExceptionHandler` có sẵn, phù hợp với cấu trúc base code.

## 9. Viết controller REST API

### Prompt 9 - Sinh controller theo style dự án

```text
Hãy tạo TermDepositController theo style BankAccountController hiện có.

Package:
- com.banking.controllers

Base path:
- /api/v1/term-deposits

Endpoint:
1. POST /open
   - Request body: @Valid OpenTermDepositRequest
   - Gọi termDepositService.openTermDeposit
   - HTTP status 201
   - Body: ApiResponse.success(data, "Mở sổ tiết kiệm thành công")

2. POST /{id}/settle
   - PathVariable id
   - Request body optional: @Valid SettleTermDepositRequest
   - Gọi termDepositService.settleTermDeposit
   - HTTP status 200
   - Body: ApiResponse.success(data, "Tất toán sổ tiết kiệm thành công")

3. GET /{id}
   - Gọi getTermDeposit

4. GET /customers/{customerId}
   - Gọi getTermDepositsByCustomer

Yêu cầu:
- Không trả entity JPA trực tiếp.
- Dùng ApiResponse giống project.
- Dùng @RequiredArgsConstructor.
```

**AI trả lời tóm tắt:**

AI tạo `TermDepositController` với các API:

- `POST /api/v1/term-deposits/open`
- `POST /api/v1/term-deposits/{id}/settle`
- `GET /api/v1/term-deposits/{id}`
- `GET /api/v1/term-deposits/customers/{customerId}`

**Đánh giá của em:**

Controller đủ API cốt lõi theo yêu cầu đề bài. Hai API `GET` không bắt buộc nhưng giúp kiểm tra dữ liệu dễ hơn và không làm hỏng cấu trúc cũ.

## 10. Review code AI sinh ra và điều hướng sửa điểm chưa tối ưu

### 10.1. Vấn đề 1 - Response entity trực tiếp

**Điểm chưa tối ưu có thể xảy ra:**

AI có thể để controller trả `TermDeposit` entity trực tiếp.

**Vì sao chưa ổn:**

- Entity có quan hệ lazy với `Customer`, `BankAccount`.
- Response có thể kéo theo dữ liệu không cần thiết.
- Có nguy cơ lộ dữ liệu nhạy cảm từ `Customer`.

**Prompt sửa:**

```text
Hãy sửa controller/service để không trả TermDeposit entity trực tiếp.
Tạo method toResponse(TermDeposit termDeposit) trong service.
Response chỉ gồm các field cần thiết cho nghiệp vụ tất toán, đặc biệt:
- actualDepositDays
- interestAppliedRate
- interestAmount
- settlementAmount
- earlySettlement
- status
```

**Kết quả:**

AI dùng `TermDepositResponse`, controller chỉ trả DTO.

### 10.2. Vấn đề 2 - Thiếu dữ liệu đối soát sau tất toán

**Điểm chưa tối ưu có thể xảy ra:**

AI chỉ tính tiền lãi để trả response nhưng không lưu `interestAppliedRate`, `actualDepositDays`, `earlySettlement`.

**Vì sao chưa ổn:**

Sau khi tất toán, ngân hàng cần biết sổ đã áp dụng lãi suất nào, gửi bao nhiêu ngày, có rút trước hạn hay không. Nếu không lưu lại thì khó đối soát.

**Prompt sửa:**

```text
Hãy bổ sung vào entity và service các field phục vụ đối soát:
- actualDepositDays
- interestAppliedRate
- interestAmount
- settlementAmount
- earlySettlement

Khi tất toán, phải set đầy đủ các field này trước khi save.
```

**Kết quả:**

Entity và response lưu đủ dữ liệu sau tất toán.

### 10.3. Vấn đề 3 - Check tất toán lại phải đặt trước mọi tính toán

**Điểm chưa tối ưu có thể xảy ra:**

AI có thể đặt check `SETTLED` sau khi tính lãi.

**Vì sao chưa ổn:**

Logic an toàn phải dừng ngay nếu sổ đã tất toán, tránh ghi đè dữ liệu hoặc cộng tiền lần hai.

**Prompt sửa:**

```text
Hãy đặt điều kiện kiểm tra status SETTLED ngay sau khi findById.
Nếu đã SETTLED thì throw BusinessException(400, "Sổ tiết kiệm đã được tất toán trước đó").
Không được tính actualDays, interestAmount hoặc cập nhật BankAccount trong trường hợp này.
```

**Kết quả:**

Service dừng ngay khi sổ đã `SETTLED`.

## 11. Code chính sau khi hoàn thiện

### 11.1. Nhánh lãi suất trong `TermDepositService`

```java
long actualDays = ChronoUnit.DAYS.between(termDeposit.getOpenedDate(), settlementDate);
if (actualDays < 0) {
    throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Ngày tất toán không được trước ngày gửi");
}

boolean earlySettlement = settlementDate.isBefore(termDeposit.getMaturityDate());
BigDecimal appliedRate = earlySettlement
        ? termDeposit.getDemandInterestRate()
        : termDeposit.getAnnualInterestRate();
BigDecimal interestAmount = calculateInterest(termDeposit.getPrincipalAmount(), appliedRate, actualDays);
BigDecimal settlementAmount = termDeposit.getPrincipalAmount().add(interestAmount).setScale(2, RoundingMode.HALF_UP);
```

Ý nghĩa:

- `settlementDate.isBefore(maturityDate)` là điều kiện trước hạn.
- Nếu trước hạn, dùng `demandInterestRate = 0.001`.
- Nếu đúng hạn hoặc sau hạn, dùng `annualInterestRate`.

### 11.2. Chặn tất toán lần hai

```java
if (TermDepositStatus.SETTLED.equals(termDeposit.getStatus())) {
    throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Sổ tiết kiệm đã được tất toán trước đó");
}
```

Ý nghĩa:

- Đáp ứng yêu cầu HTTP 400.
- Tránh tính lãi lại.
- Tránh cộng tiền vào tài khoản lần hai.

### 11.3. Hàm tính lãi bằng `BigDecimal`

```java
private BigDecimal calculateInterest(BigDecimal principalAmount, BigDecimal annualRate, long actualDays) {
    if (actualDays <= 0) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return principalAmount
            .multiply(annualRate)
            .multiply(BigDecimal.valueOf(actualDays))
            .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);
}
```

Ý nghĩa:

- Không dùng `double`.
- Làm tròn 2 chữ số thập phân.
- Tính theo số ngày gửi thực tế.

## 12. Kiểm thử bằng AI

### Prompt 10 - Viết unit test cho service

```text
Hãy viết unit test cho TermDepositService bằng JUnit 5 và Mockito, không cần khởi động toàn bộ Spring context.

Class test:
- com.banking.models.services.TermDepositServiceTest

Mock:
- TermDepositRepository
- CustomerRepository
- BankAccountRepository

Test 1: settleTermDepositBeforeMaturityUsesDemandInterestRate
- principal = 100000000
- annualInterestRate = 0.06
- demandInterestRate = 0.001
- openedDate = 2026-01-01
- maturityDate = 2026-07-01
- settlementDate = 2026-03-01
- actualDays = 59
- expected interest = 16164.38
- expected appliedRate = 0.001
- expected earlySettlement = true
- expected status = SETTLED

Test 2: settleTermDepositOnMaturityUsesOriginalTermInterestRate
- openedDate = 2025-01-01
- maturityDate = 2025-07-01
- settlementDate = 2025-07-01
- actualDays = 181
- expected interest = 2975342.47
- expected appliedRate = 0.06
- expected earlySettlement = false

Test 3: settleTermDepositTwiceThrowsBadRequestBusinessException
- termDeposit.status = SETTLED
- expected exception code = 400
- expected message = "Sổ tiết kiệm đã được tất toán trước đó"

Yêu cầu:
- Dùng when(repository.findById(1L)).thenReturn(Optional.of(termDeposit)).
- Dùng when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0)).
- Assert bằng BigDecimal đúng scale.
```

**AI trả lời tóm tắt:**

AI tạo 3 unit test:

- Tất toán trước hạn dùng `0.001`.
- Tất toán đúng hạn dùng `0.06`.
- Tất toán lại ném `BusinessException` code 400.

**Đánh giá của em:**

Ba test này bám sát rubric vì chứng minh được:

- Logic rẽ nhánh lãi suất.
- Dùng số ngày thực tế.
- Exception HTTP 400 cho tất toán lần hai.

## 13. Debug build/test

### Prompt 11 - Cấu hình test không phụ thuộc MySQL

```text
Project production đang dùng MySQL trong application.properties:
- spring.datasource.url=jdbc:mysql://localhost:3306/core_banking?createDatabaseIfNotExist=true
- username=root
- password=123456
- ddl-auto=update

Khi chấm bài, máy chấm có thể không có MySQL. Hãy cấu hình test để gradlew test chạy được độc lập.

Ràng buộc:
- Không sửa application.properties production.
- Chỉ thêm dependency testRuntimeOnly H2.
- Chỉ override datasource trong @SpringBootTest properties của CoreBankingApplicationTests.
- H2 chạy MODE=MySQL để tương thích schema JPA.
- ddl-auto=create-drop cho test.
```

**AI trả lời tóm tắt:**

AI đề xuất thêm:

```gradle
testRuntimeOnly 'com.h2database:h2'
```

và override datasource trong `CoreBankingApplicationTests`.

**Đánh giá của em:**

Đây là cải thiện giúp bài chấm ổn định hơn vì `gradlew test` không phụ thuộc MySQL local.

### Prompt 12 - Chạy compile

```text
Hãy chạy .\gradlew.bat compileJava tại root project.

Nếu compile lỗi:
1. Đọc chính xác file và dòng lỗi.
2. Không sửa lan man.
3. Ưu tiên sửa import, annotation, package hoặc kiểu dữ liệu.
4. Sau khi sửa chạy lại compileJava.

Mục tiêu: chứng minh code mới không lỗi biên dịch trước khi viết tài liệu nộp bài.
```

**AI trả lời tóm tắt:**

AI chạy:

```powershell
.\gradlew.bat compileJava
```

Kết quả:

```text
BUILD SUCCESSFUL
```

### Prompt 13 - Chạy test

```text
Hãy chạy .\gradlew.bat test.

Khi đọc kết quả, hãy xác nhận 4 điểm:
1. Spring context có load được không.
2. Unit test TermDepositService có chạy không.
3. Có lỗi database do MySQL local không.
4. BUILD SUCCESSFUL hay không.

Nếu fail, hãy sửa nguyên nhân nhỏ nhất có thể và chạy lại test.
```

**AI trả lời tóm tắt:**

AI chạy:

```powershell
.\gradlew.bat test
```

Kết quả:

```text
BUILD SUCCESSFUL
```

AI xác nhận:

- Spring context load được.
- Unit test nghiệp vụ chạy thành công.
- Không phụ thuộc MySQL local trong test.

## 14. Rà soát theo rubric chấm điểm

### Prompt 14 - Review như giảng viên chấm bài

```text
Hãy rà soát bài làm theo rubric dưới đây và chỉ ra bằng chứng cụ thể trong file.

Rubric:
1. Tư duy phân tích SRS - 20 điểm:
   SRS phải chỉ ra công thức tính tiền lãi và logic rẽ nhánh trước hạn/đúng hạn hợp lý.

2. Kỹ năng điều hướng AI - 30 điểm:
   Prompt_History phải thể hiện AI được cung cấp context base code hiện tại, prompt đúng để xử lý thư viện thời gian Java LocalDate, và prompt debug/test.

3. Chất lượng code AI sinh ra - 35 điểm:
   Entity map đúng với Customer hoặc BankAccount. API core mở sổ/tất toán đúng phép tính và logic if/else theo yêu cầu nghiệp vụ. Không phá hỏng cấu trúc cũ.

4. Xử lý lỗi và tối ưu - 15 điểm:
   Có HTTP 400 chặn tất toán hai lần hoặc thể hiện nỗ lực dùng AI debug lỗi phát sinh.

Hãy kiểm tra:
- SRS có phần phân tích yêu cầu nghiệp vụ chưa.
- SRS có công thức interestAmount = principalAmount * appliedRate * actualDepositDays / 365 chưa.
- SRS có ví dụ tính toán trước hạn/đúng hạn/sau hạn chưa.
- Prompt có đủ context base code chưa.
- Prompt có yêu cầu LocalDate/ChronoUnit.DAYS chưa.
- Code có dùng BigDecimal không.
- Entity TermDeposit có mapping Customer hoặc BankAccount không.
- API mở sổ và tất toán có chưa.
- Exception tất toán lần hai có message đúng chưa.
- Test có chứng minh exception 400 không.
```

**AI trả lời tóm tắt:**

AI xác nhận:

- `SRS.md` có phân tích nghiệp vụ, công thức, ví dụ và ma trận truy vết.
- `Prompt_History.md` có context base code đầy đủ.
- Code dùng `LocalDate`, `ChronoUnit.DAYS`, `BigDecimal`.
- Entity map với `Customer` và `BankAccount`.
- API mở sổ và tất toán đã có.
- Exception tất toán lần hai dùng đúng message.
- Test đã chạy thành công.

## 15. Tổng hợp kỹ năng sử dụng AI

Qua bài làm này, em dùng AI theo quy trình lặp:

1. Giao vai trò rõ cho AI: Business Analyst, Spring Boot Backend Engineer, Reviewer.
2. Cung cấp bối cảnh đầy đủ trước khi yêu cầu code.
3. Ràng buộc AI theo base code thật: package, entity, repository, `ApiResponse`, `BusinessException`.
4. Ràng buộc công nghệ đúng đề: `LocalDate`, `ChronoUnit.DAYS`, `BigDecimal`.
5. Không nhận kết quả AI đầu tiên một cách thụ động.
6. Kiểm tra điểm chưa tối ưu: response entity trực tiếp, thiếu field đối soát, check tất toán lại đặt muộn.
7. Prompt lại để sửa đúng trọng tâm.
8. Chạy compile/test để xác nhận kết quả.
9. Rà soát lại theo rubric.

Điểm quan trọng nhất là AI được dùng như trợ lý kỹ thuật, còn em vẫn kiểm tra yêu cầu nghiệp vụ, độ an toàn tiền tệ, cấu trúc code và tiêu chí chấm điểm.

## 16. Kết luận

Prompt History này thể hiện quá trình điều hướng AI theo chuỗi: phân tích, thiết kế, sinh code, kiểm tra, phát hiện điểm chưa tối ưu, sửa lại và xác minh bằng test. Bài làm đáp ứng đầy đủ các tiêu chí:

- SRS có tư duy phân tích và công thức tính lãi.
- Prompt có context base code rõ ràng.
- Code có entity/API/service đúng nghiệp vụ.
- Exception HTTP 400 được xử lý đúng.
- Test xác nhận nhánh trước hạn, đúng hạn và tất toán lần hai.
