package com.lois.management.service;

import com.lois.management.domain.Reservation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "reservation.limiter.type=none")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReservationTestForSearch {

    @Autowired
    private ReservationService reservationService;


    private final List<Double> resultList = new ArrayList<>();

    private static final int WARM_UP_COUNT = 3;
    private static final int MEASURE_COUNT = 10;
    @AfterAll
    void printResult() {
        if (resultList.size() <= WARM_UP_COUNT) {
            System.out.println("측정 데이터가 부족합니다.");
            System.out.println("수집된 데이터 수: " + resultList.size());
            return;
        }

        List<Double> measuredResults =
                new ArrayList<>(
                        resultList.subList(
                                WARM_UP_COUNT,
                                resultList.size()
                        )
                );

        Collections.sort(measuredResults);

        double average =
                measuredResults.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

        double median;

        int size = measuredResults.size();

        if (size % 2 == 0) {
            median =
                    (
                            measuredResults.get(size / 2 - 1)
                                    + measuredResults.get(size / 2)
                    ) / 2.0;
        } else {
            median =
                    measuredResults.get(size / 2);
        }

        double min =
                measuredResults.get(0);

        double max =
                measuredResults.get(size - 1);

        System.out.println();
        System.out.println(
                "========================================="
        );
        System.out.println(
                "      전화번호 검색 성능 최종 리포트"
        );
        System.out.println(
                "========================================="
        );
        System.out.println(
                "검색 뒷번호       : 5329"
        );
        System.out.println(
                "워밍업 횟수       : " + WARM_UP_COUNT
        );
        System.out.println(
                "실제 측정 횟수   : " + measuredResults.size()
        );
        System.out.printf(
                "평균 처리시간    : %.3f ms%n",
                average
        );
        System.out.printf(
                "중앙값           : %.3f ms%n",
                median
        );
        System.out.printf(
                "최소 처리시간    : %.3f ms%n",
                min
        );
        System.out.printf(
                "최대 처리시간    : %.3f ms%n",
                max
        );
        System.out.println(
                "========================================="
        );
    }

//    @Test
    @RepeatedTest(13)
    void 번호검색() {
        long startTime = System.nanoTime();
        List<Reservation> reservations = reservationService.findByContactSuffix("5329");

        long endTime = System.nanoTime();


        double durationMillis = (endTime - startTime) / 1_000_000.0;

        resultList.add(durationMillis);

        assertThat(reservations).isNotEmpty();

        System.out.printf(
                "번호 검색 처리시간: %.3f ms, 조회 건수: %d%n",
                durationMillis,
                reservations.size()
        );
    }

}
