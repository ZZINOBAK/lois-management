package com.lois.management.dto.reservation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationPageReq {

    // 요청
    private int page = 1;
    private int size = 20;

    // 응답(계산)
    private int totalCount;
    private int totalPage;

    private int startPage;
    private int endPage;

    private boolean hasPrev;
    private boolean hasNext;

    public int getOffset() {
        return (page - 1) * size;
    }

    public void calculate() {

        totalPage = (int) Math.ceil((double) totalCount / size);

        int blockSize = 10;

        startPage = ((page - 1) / blockSize) * blockSize + 1;
        endPage = Math.min(startPage + blockSize - 1, totalPage);

        hasPrev = startPage > 1;
        hasNext = endPage < totalPage;
    }
}
