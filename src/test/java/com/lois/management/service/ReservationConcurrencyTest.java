package com.lois.management.service;

import com.lois.management.domain.Reservation;
import com.lois.management.domain.ReservationPolicy;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.AbstractReservationLimiter;
import com.lois.management.service.reservation.limiter.ReservationLimiter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "reservation.limiter.type=none")
@ActiveProfiles("test")
public class ReservationConcurrencyTest {
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationMapper reservationMapper;
    @Autowired
    private ReservationPolicyMapper reservationPolicyMapper;

    // 테스트에 사용한 가짜 전화번호 식별자
    private final String TEST_CONTACT_PREFIX = "010-9876-";

    @AfterEach
    @DisplayName("테스트 완료 후 가짜 예약 데이터를 자동으로 청소한다")
    void tearDown() {
        // 테스트가 성공하든 실패하든, 010-9999- 로 시작하는 가짜 예약 데이터를 DB에서 싹 지웁니다.
        reservationMapper.deleteTestReservations(TEST_CONTACT_PREFIX);
    }

    // 클래스 내부에 전역 변수로 누적 카운터 선언 (멀티스레드 환경이므로 AtomicInteger 사용)
    private static final AtomicInteger totalTestsRun = new AtomicInteger(0);
    private static final AtomicInteger bugReproducedCount = new AtomicInteger(0); // assertThat(afterCount > 10)이 참인 경우
    private static final AtomicInteger bugFailedCount = new AtomicInteger(0);     // 10개 딱 맞춰서 버그 재현 안 된 경우
    private static final AtomicInteger totalDbCount = new AtomicInteger(0);

    @RepeatedTest(100)
    @DisplayName("동시성(정합성) 테스트: 시간당 10개 제한인 상황에서 동시에 50명이 예약을 요청하면 초과 예약 버그가 발생한다")
    void concurrency_limit_fail_test_by_gemini() throws InterruptedException {

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        LocalDate targetDate = LocalDate.of(2026, 11, 10);
        LocalTime targetTime = LocalTime.of(15, 0);

        // [중요] 매 반복 테스트 시작 전, DB 데이터를 초기화해주는 로직이 필요합니다!
        // (예: reservationMapper.deleteAllByDateAndTime(targetDate, targetTime);)

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Reservation reservation = new Reservation();
                    reservation.setResDate(targetDate);
                    reservation.setResTime(targetTime);
                    reservation.setCakeId(5L);
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("%04d", index));
                    reservation.setPaid(true);

                    reservationService.create(reservation);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // 결과 확인
        int afterCount = reservationMapper.countByDateAndTime(targetDate, targetTime);

        // [데이터 누적]
        totalTestsRun.incrementAndGet();
        totalDbCount.addAndGet(afterCount);

        if (afterCount > 10) {
            bugReproducedCount.incrementAndGet(); // 버그 재현 성공 (10개 초과 저장됨)
        } else {
            bugFailedCount.incrementAndGet();     // 버그 재현 실패 (운 좋게 10개만 저장됨)
        }

        // 개별 System.out.println은 제거하거나 주석 처리하여 콘솔을 깨끗하게 유지합니다.
        assertThat(afterCount).isGreaterThan(10);
    }

    // 전체 100번의 테스트가 모두 끝난 후 딱 한 번만 실행되는 집계 메서드
    @AfterAll
    static void printFinalSummary_by_gemini() {
        System.out.println("\n=========================================");
        System.out.println("====== 📊 동시성 테스트 최종 집계 리포트 ======");
        System.out.println("=========================================");
        System.out.println("총 실행한 테스트 횟수: " + totalTestsRun.get() + "회");
        System.out.println("❌ 초과 예약 버그 발생 (재현 성공): " + bugReproducedCount.get() + "회");
        System.out.println("🟢 정상 차단됨 (버그 재현 실패): " + bugFailedCount.get() + "회");

        double reproductionRate = ((double) bugReproducedCount.get() / totalTestsRun.get()) * 100;
        System.out.printf("🎯 버그 재현율 (동시성 이슈 발생 확률): %.2f%%\n", reproductionRate);
        System.out.printf("📈 테스트당 평균 DB 저장 건수: %.1f건\n", (double) totalDbCount.get() / totalTestsRun.get());
        System.out.println("=========================================\n");
    }

    @Test
    @DisplayName("동시성(정합성) 테스트: 시간당 10개 제한인 상황에서 동시에 50명이 예약을 요청하면 초과 예약 버그가 발생한다")
    @RepeatedTest(100)
    void concurrency_limit_fail_test_by_haeun() throws InterruptedException {


        int threadCount = 100; // 동시에 요청을 보낼 사람(스레드) 수
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 32개의 스레드 풀 생성
        CountDownLatch latch = new CountDownLatch(threadCount); // 50명이 모두 준비될 때까지 대기하는 래치

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // [기본 세팅] 테스트할 날짜와 시간 고정 (예: 2026-06-10 15:00)
//        LocalDate targetDate = LocalDate.of(2026, 6, 10);
        LocalDate targetDate = LocalDate.of(2026, 11, 10);
        LocalTime targetTime = LocalTime.of(15, 0);


        // [동시 요청 시작]
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {


                    // 가상의 예약 데이터 생성 (각기 다른 전화번호로 중복 방지)
                    Reservation reservation = new Reservation();
                    reservation.setResDate(targetDate);
                    reservation.setResTime(targetTime);
                    reservation.setCakeId(5L); // 존재하는 케이크 ID 입력
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("%04d", index));
                    reservation.setPaid(true);

//                    ReservationPolicy reservationPolicy = reservationPolicyMapper.selectLatestPolicy();
//
//                    // 테스트 시작 전 해당 시간대의 기존 예약이 0개인지 확인
//                    int countDate = reservationMapper.countByDate(targetDate);
//                    int countDateNTime = reservationMapper.countByDateAndTime(targetDate, targetTime);
//                    assertThat(countDateNTime).isEqualTo(0);

//                    if(reservationPolicy.getDailyMaxLimit()>countDate && reservationPolicy.getHourlyMaxLimit()>countDateNTime) {
                        // 서비스 호출
                        reservationService.create(reservation);
                        successCount.incrementAndGet(); // 예외 없이 성공 시 카운트 증가
//                    }

                } catch (Exception e) {
                    failCount.incrementAndGet(); // 예약 초과 등의 예외 발생 시 카운트 증가
                } finally {
                    latch.countDown(); // 하나의 스레드가 끝나면 래치 카운트 감소
                }
            });
        }

        latch.await(); // 50개의 스레드가 모두 완료될 때까지 메인 스레드는 대기
        executorService.shutdown();

        // [결과 검증 및 확인]
        int afterCount = reservationMapper.countByDateAndTime(targetDate, targetTime);

        System.out.println("=========================================");
        System.out.println("요청한 총 스레드 수: " + threadCount);
        System.out.println("로직상 성공 응답 수: " + successCount.get());
        System.out.println("로직상 실패(튕겨냄) 수: " + failCount.get());
        System.out.println("실제 DB에 쌓인 최종 예약 건수: " + afterCount);
        System.out.println("=========================================");

        // 원래대로라면 10개에서 딱 멈춰야 하지만, 동시성 이슈로 인해 10개보다 더 많이 저장됩니다.
        // 이 현상을 눈으로 확인하기 위해 일부러 초과 저장이 되었는지 검증합니다.
        assertThat(afterCount).isGreaterThan(10);
//        assertThat(afterCount).isEqualTo(10);

    }
//
//    @Test
//    @DisplayName(
//            "동시성 방어 없이 정책을 조회하고 검증하면 시간당 예약 제한을 초과한다"
//    )
//    void concurrency_limit_fail_test_by_gpt()
//            throws InterruptedException {
//
//        int threadCount = 100;
//        int hourlyLimit = 10;
//
//        LocalDate targetDate =
//                LocalDate.of(2026, 12, 10);
//
//        LocalTime targetTime =
//                LocalTime.of(15, 0);
//
//        /*
//         * 테스트 시작 전에 해당 슬롯이 비어 있는지
//         * 메인 스레드에서 한 번만 확인한다.
//         */
//        int beforeCount =
//                reservationMapper.countByDateAndTime(
//                        targetDate,
//                        targetTime
//                );
//
//        assertThat(beforeCount).isZero();
//
//        ExecutorService executorService =
//                Executors.newFixedThreadPool(32);
//
//        /*
//         * 동시 출발용
//         */
//        CountDownLatch startLatch =
//                new CountDownLatch(1);
//
//        /*
//         * 전체 완료 대기용
//         */
//        CountDownLatch doneLatch =
//                new CountDownLatch(threadCount);
//
//        AtomicInteger successCount =
//                new AtomicInteger();
//
//        AtomicInteger failCount =
//                new AtomicInteger();
//
//        for (int i = 0; i < threadCount; i++) {
//            final int index = i;
//
//            executorService.submit(() -> {
//                try {
//                    /*
//                     * 모든 작업이 등록된 후 동시에 출발
//                     */
//                    startLatch.await();
//
//                    Reservation reservation =
//                            new Reservation();
//
//                    reservation.setResDate(targetDate);
//                    reservation.setResTime(targetTime);
//                    reservation.setCakeId(5L);
//                    reservation.setContact(
//                            TEST_CONTACT_PREFIX
//                                    + String.format(
//                                    "%04d",
//                                    index
//                            )
//                    );
//                    reservation.setPaid(true);
//
//                    reservationService.create(reservation);
//
//                    successCount.incrementAndGet();
//
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//
//                    System.out.println(
//                            "실패 원인: "
//                                    + e.getClass().getSimpleName()
//                                    + " / "
//                                    + e.getMessage()
//                    );
//
//                } finally {
//                    doneLatch.countDown();
//                }
//            });
//        }
//
//        /*
//         * 등록된 작업을 한꺼번에 출발시킨다.
//         */
//        startLatch.countDown();
//
//        /*
//         * 모든 요청 완료 대기
//         */
//        doneLatch.await();
//
//        executorService.shutdown();
//
//        int afterCount =
//                reservationMapper.countByDateAndTime(
//                        targetDate,
//                        targetTime
//                );
//
//        int exceededCount =
//                Math.max(
//                        0,
//                        afterCount - hourlyLimit
//                );
//
//        System.out.println();
//        System.out.println(
//                "========================================="
//        );
//        System.out.println(
//                "동시성 방어: 없음"
//        );
//        System.out.println(
//                "시간당 예약 제한: "
//                        + hourlyLimit
//        );
//        System.out.println(
//                "요청한 총 스레드 수: "
//                        + threadCount
//        );
//        System.out.println(
//                "로직상 성공 응답 수: "
//                        + successCount.get()
//        );
//        System.out.println(
//                "로직상 실패 응답 수: "
//                        + failCount.get()
//        );
//        System.out.println(
//                "실제 DB 최종 예약 건수: "
//                        + afterCount
//        );
//        System.out.println(
//                "초과 예약 건수: "
//                        + exceededCount
//        );
//        System.out.println(
//                "========================================="
//        );
//
//        /*
//         * 동시성 방어가 없으므로
//         * 10건을 초과하는 경쟁 상태가 발생하는지 확인한다.
//         */
//        assertThat(afterCount)
//                .isGreaterThan(hourlyLimit);
//    }
}
