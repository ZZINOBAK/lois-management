package com.lois.management.service;

import com.lois.management.domain.Reservation;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.service.reservation.limiter.AbstractReservationLimiter;
import com.lois.management.service.reservation.limiter.ReservationLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "reservation.limiter.type=atomic")
@ActiveProfiles("test")
public class AtomicReservationConcurrencyTest {
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationMapper reservationMapper;

    // 테스트에 사용한 가짜 전화번호 식별자
    private final String TEST_CONTACT_PREFIX = "010-9876-";

    @AfterEach
    @DisplayName("테스트 완료 후 가짜 예약 데이터를 자동으로 청소한다")
    void tearDown() {
        // 테스트가 성공하든 실패하든, 010-9999- 로 시작하는 가짜 예약 데이터를 DB에서 싹 지웁니다.
        reservationMapper.deleteTestReservations(TEST_CONTACT_PREFIX);
    }

    @Test
    @DisplayName("동시성(정합성) 테스트: 시간당 10개 제한인 상황에서 동시에 50명이 예약을 요청하면 초과 예약 버그가 발생한다")
    void concurrency_limit_fail_test() throws InterruptedException {
        // [기본 세팅] 테스트할 날짜와 시간 고정 (예: 2026-06-10 15:00)
//        LocalDate targetDate = LocalDate.of(2026, 6, 10);
        LocalDate targetDate = LocalDate.of(2026, 9, 10);
        LocalTime targetTime = LocalTime.of(15, 0);

        // 테스트 시작 전 해당 시간대의 기존 예약이 0개인지 확인
        int beforeCount = reservationMapper.countByDateAndTime(targetDate, targetTime);
        assertThat(beforeCount).isEqualTo(0);

        int threadCount = 50; // 동시에 요청을 보낼 사람(스레드) 수
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 32개의 스레드 풀 생성
        CountDownLatch latch = new CountDownLatch(threadCount); // 50명이 모두 준비될 때까지 대기하는 래치

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

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

                    // 서비스 호출
                    reservationService.create(reservation);
                    successCount.incrementAndGet(); // 예외 없이 성공 시 카운트 증가
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
//        assertThat(afterCount).isGreaterThan(10);
        assertThat(afterCount).isEqualTo(10);

    }

    @Test
    void 속도_비교_테스트_시간날짜한개() throws InterruptedException {
        LocalDate targetDate = LocalDate.of(2026, 9, 10);
        LocalTime targetTime = LocalTime.of(15, 0);

        int threadCount = 2000; // 💡 변별력을 위해 요청 수를 2,000개 정도로 크게 잡습니다.
        ExecutorService executorService = Executors.newFixedThreadPool(200); // 200개 스레드로 동시 압박
        CountDownLatch latch = new CountDownLatch(threadCount);

        // ⏱️ 정밀 스톱워치 시작 (나노초 단위)
        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 가짜 예약 데이터 바인딩 후 호출
                    Reservation reservation = new Reservation();
                    reservation.setResDate(targetDate);
                    reservation.setResTime(targetTime);
                    reservation.setCakeId(5L); // 존재하는 케이크 ID 입력
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("%04d", index));
                    reservation.setPaid(true);
                    reservationService.create(reservation);
                } catch (Exception e) {
                    // 실패(마감) 처리된 것도 스레드 연산 속도에 포함되므로 둡니다.
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        // ⏱️ 스톱워치 종료
        long endTime = System.nanoTime();

        long durationMillis = (endTime - startTime) / 1_000_000;
        System.out.println("=========================================");
        System.out.println("총 처리 소요 시간: " + durationMillis + " ms");
        System.out.println("=========================================");
    }

    @Test
    void 속도_비교_테스트_시간날짜여러개() throws InterruptedException {
        // 💡 랜덤으로 선택할 후보군 배열 정의
        LocalDate[] targetDates = {
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 13)
        };
        LocalTime[] targetTimes = {
                LocalTime.of(15, 0),
                LocalTime.of(16, 0)
        };

        int threadCount = 2000; // 💡 변별력을 위해 요청 수를 2,000개 정도로 크게 잡습니다.
        ExecutorService executorService = Executors.newFixedThreadPool(200); // 200개 스레드로 동시 압박
        CountDownLatch latch = new CountDownLatch(threadCount);

        // ⏱️ 정밀 스톱워치 시작 (나노초 단위)
        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 💡 멀티스레드 환경에서 안전한 랜덤 객체 사용
                    java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

                    // 날짜와 시간을 랜덤으로 하나씩 픽(Pick)합니다.
                    LocalDate pickedDate = targetDates[random.nextInt(targetDates.length)];
                    LocalTime pickedTime = targetTimes[random.nextInt(targetTimes.length)];

                    // 가짜 예약 데이터 바인딩 후 호출
                    Reservation reservation = new Reservation();
                    reservation.setResDate(pickedDate);
                    reservation.setResTime(pickedTime);
                    reservation.setCakeId(5L); // 존재하는 케이크 ID 입력
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("%04d", index));
                    reservation.setPaid(true);

                    reservationService.create(reservation);
                } catch (Exception e) {
                    // 실패(마감) 처리된 것도 스레드 연산 속도에 포함되므로 둡니다.
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        // ⏱️ 스톱워치 종료
        long endTime = System.nanoTime();

        long durationMillis = (endTime - startTime) / 1_000_000;
        System.out.println("=========================================");
        System.out.println("총 처리 소요 시간: " + durationMillis + " ms");
        System.out.println("=========================================");
    }

    @Autowired
//    @Qualifier("dbLockConcurrencyGuard")
    private ReservationLimiter reservationLimiter; // 💡 현재 테스트 중인 가드 주입

    @Test
    void 속도_비교_테스트_시간날짜한개_플래그온오프() throws InterruptedException {
        // 부모 클래스 타입으로 캐스팅하여 스위치 리모컨을 확보합니다.
        AbstractReservationLimiter guard = (AbstractReservationLimiter) reservationLimiter;

        // 💡 [수정] 배열을 완전히 빼고 깔끔하게 단일 객체로만 선언합니다.
        LocalDate targetDate = LocalDate.of(2026, 9, 10);
        LocalTime targetTime = LocalTime.of(15, 0);

        int threadCount = 2000;

        // =================================================================
        // ❌ 1회차: 플래그 OFF 상태로 2,000개 요청 (단일 구멍 락 경합 유도)
        // =================================================================
        guard.setEnableFastFail(false); // 팻말 끄기
        System.out.println("▶️ 1회차 시작: 플래그 [OFF] 상태로 자리를 채우는 중...");

        ExecutorService executorService1 = Executors.newFixedThreadPool(200);
        CountDownLatch latch1 = new CountDownLatch(threadCount);

        long startTimeOff = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final int index = i; // 0 ~ 1999
            executorService1.submit(() -> {
                try {
                    Reservation reservation = new Reservation();
                    // 💡 배열 조회나 랭스 없이 변수를 곧바로 바인딩합니다.
                    reservation.setResDate(targetDate);
                    reservation.setResTime(targetTime);
                    reservation.setCakeId(5L);
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("OFF-%04d", index));
                    reservation.setPaid(true);

                    reservationService.create(reservation);
                } catch (Exception e) {
                } finally {
                    latch1.countDown();
                }
            });
        }
        latch1.await();
        long endTimeOff = System.nanoTime();
        executorService1.shutdown();


        // =================================================================
        //  2회차: 플래그 ON 상태로 2,000개 요청 (이미 마감된 상태에서 초고속 패스 검증)
        // =================================================================
        guard.setEnableFastFail(true); // 팻말 켜기!
        System.out.println("▶️ 2회차 시작: 플래그 [ON] 상태로 초고속 입구컷을 검증하는 중...");

        ExecutorService executorService2 = Executors.newFixedThreadPool(200);
        CountDownLatch latch2 = new CountDownLatch(threadCount);

        long startTimeOn = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final int index = i; // 0 ~ 1999
            executorService2.submit(() -> {
                try {
                    Reservation reservation = new Reservation();
                    // 💡 2회차도 직관적으로 단일 변수 직접 바인딩!
                    reservation.setResDate(targetDate);
                    reservation.setResTime(targetTime);
                    reservation.setCakeId(5L);
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("ON-%04d", index));
                    reservation.setPaid(true);

                    reservationService.create(reservation);
                } catch (Exception e) {
                } finally {
                    latch2.countDown();
                }
            });
        }
        latch2.await();
        long endTimeOn = System.nanoTime();
        executorService2.shutdown();


        // =================================================================
        // 📊 최종 나노초 스톱워치 결과 정산
        // =================================================================
        long durationOffMillis = (endTimeOff - startTimeOff) / 1_000_000;
        long durationOnMillis = (endTimeOn - startTimeOn) / 1_000_000;

        System.out.println("\n=========================================");
        System.out.println("      📊 플래그 ON/OFF 최종 속도 비교");
        System.out.println("=========================================");
        System.out.println("❌ 플래그 [OFF] 소요 시간: " + durationOffMillis + " ms");
        System.out.println(" 플래그 [ON]  소요 시간: " + durationOnMillis + " ms");
        System.out.println("=========================================");

        if (durationOffMillis > durationOnMillis) {
            System.out.println("=> Fast-Fail 팻말 덕분에 " + (durationOffMillis - durationOnMillis) + "ms 단축되었습니다! 🚀");
        }
    }
    @Test
    void 속도_비교_테스트_시간날짜여러개_플래그온오프() throws InterruptedException {
        // 부모 클래스 타입으로 캐스팅하여 스위치 리모컨을 확보합니다.
        AbstractReservationLimiter guard = (AbstractReservationLimiter) reservationLimiter;

        LocalDate[] targetDates = {
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 13)
        };
        LocalTime[] targetTimes = {
                LocalTime.of(15, 0),
                LocalTime.of(16, 0)
        };

        int threadCount = 2000;

        // =================================================================
        // ❌ 1회차: 플래그 OFF 상태로 2,000개 요청 (자리를 채워 마감 상태 만들기)
        // =================================================================
        guard.setEnableFastFail(false); // 팻말 끄기
        System.out.println("▶️ 1회차 시작: 플래그 [OFF] 상태로 자리를 채우는 중...");

        ExecutorService executorService1 = Executors.newFixedThreadPool(200);
        CountDownLatch latch1 = new CountDownLatch(threadCount);

        long startTimeOff = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final int index = i; // 0 ~ 1999
            executorService1.submit(() -> {
                try {
                    java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                    LocalDate pickedDate = targetDates[random.nextInt(targetDates.length)];
                    LocalTime pickedTime = targetTimes[random.nextInt(targetTimes.length)];

                    Reservation reservation = new Reservation();
                    reservation.setResDate(pickedDate);
                    reservation.setResTime(pickedTime);
                    reservation.setCakeId(5L);
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("OFF-%04d", index)); // 중복 방지 접두사
                    reservation.setPaid(true);

                    reservationService.create(reservation);
                } catch (Exception e) {
                } finally {
                    latch1.countDown();
                }
            });
        }
        latch1.await();
        long endTimeOff = System.nanoTime();
        executorService1.shutdown();


        // =================================================================
        //  2회차: 플래그 ON 상태로 2,000개 요청 (이미 마감된 상태에서 성능 비교)
        // =================================================================
        guard.setEnableFastFail(true); // 팻말 켜기!
        System.out.println("▶️ 2회차 시작: 플래그 [ON] 상태로 초고속 입구컷을 검증하는 중...");

        ExecutorService executorService2 = Executors.newFixedThreadPool(200);
        CountDownLatch latch2 = new CountDownLatch(threadCount);

        long startTimeOn = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final int index = i; // 0 ~ 1999
            executorService2.submit(() -> {
                try {
                    java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                    LocalDate pickedDate = targetDates[random.nextInt(targetDates.length)];
                    LocalTime pickedTime = targetTimes[random.nextInt(targetTimes.length)];

                    Reservation reservation = new Reservation();
                    reservation.setResDate(pickedDate);
                    reservation.setResTime(pickedTime);
                    reservation.setCakeId(5L);
                    reservation.setContact(TEST_CONTACT_PREFIX + String.format("ON-%04d", index)); // 중복 방지 접두사
                    reservation.setPaid(true);

                    reservationService.create(reservation);
                } catch (Exception e) {
                } finally {
                    latch2.countDown();
                }
            });
        }
        latch2.await();
        long endTimeOn = System.nanoTime();
        executorService2.shutdown();


        // =================================================================
        // 📊 최종 나노초 스톱워치 결과 정산
        // =================================================================
        long durationOffMillis = (endTimeOff - startTimeOff) / 1_000_000;
        long durationOnMillis = (endTimeOn - startTimeOn) / 1_000_000;

        System.out.println("\n=========================================");
        System.out.println("      📊 플래그 ON/OFF 최종 속도 비교");
        System.out.println("=========================================");
        System.out.println("❌ 플래그 [OFF] 소요 시간: " + durationOffMillis + " ms");
        System.out.println(" 플래그 [ON]  소요 시간: " + durationOnMillis + " ms");
        System.out.println("=========================================");

        if (durationOffMillis > durationOnMillis) {
            System.out.println("=> Fast-Fail 팻말 덕분에 " + (durationOffMillis - durationOnMillis) + "ms 단축되었습니다! 🚀");
        }
    }
}
