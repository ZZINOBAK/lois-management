package com.lois.management.domain.cake.service;

import com.lois.management.domain.cake.CakeMovement;
import com.lois.management.domain.cake.mapper.CakeMovementMapper;
import com.lois.management.domain.cake.mapper.CakeStockMapper;
import com.lois.management.global.sse.DataChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CakeStockService {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String DAILY_RESET = "DAILY_RESET";

    private final CakeStockMapper cakeStockMapper;
    private final CakeMovementMapper cakeMovementMapper;
    // 일일 초기화는 같은 클래스의 스케줄/시작 메서드에서 호출되므로 @Transactional 대신 직접 트랜잭션을 건다.
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean stockResetEnabled;

    public CakeStockService(CakeStockMapper cakeStockMapper,
                            CakeMovementMapper cakeMovementMapper,
                            PlatformTransactionManager transactionManager,
                            ApplicationEventPublisher eventPublisher,
                            @Value("${lois.stock-reset.enabled:true}") boolean stockResetEnabled) {
        this.cakeStockMapper = cakeStockMapper;
        this.cakeMovementMapper = cakeMovementMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.eventPublisher = eventPublisher;
        this.stockResetEnabled = stockResetEnabled;
    }

    // 매일 00:00(한국 시간) 재고를 모두 0으로 초기화한다.
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void resetStocksAtMidnight() {
        resetSafely("SCHEDULED");
    }

    // 00:00에 서버가 꺼져 있었던 경우를 위해 앱 시작 시에도 오늘 초기화 여부를 확인한다.
    @EventListener(ApplicationReadyEvent.class)
    public void resetStocksOnStartup() {
        resetSafely("STARTUP");
    }

    private void resetSafely(String trigger) {
        if (!stockResetEnabled) return;
        try {
            resetDailyStocks(LocalDate.now(KST), trigger);
        } catch (RuntimeException e) {
            // 초기화 실패가 앱 시작이나 스케줄러를 멈추지 않게 로그만 남긴다.
            log.error("업무=재고초기화 event=실패 trigger={}", trigger, e);
        }
    }

    /**
     * 해당 날짜에 아직 초기화하지 않았다면 전체 재고(1호·2호)를 0으로 만들고 행마다 DAILY_RESET 수불을 남긴다.
     * 같은 날 두 번 실행되면 건너뛴다. (request_id 유니크 제약이 동시 실행도 막는다)
     *
     * @return 초기화를 실행했으면 true, 이미 초기화되어 건너뛰었으면 false
     */
    public boolean resetDailyStocks(LocalDate bizDate, String trigger) {
        Boolean reset = transactionTemplate.execute(status -> {
            // 재고를 먼저 잠가서 동시에 실행된 초기화가 순서대로 처리되게 한다.
            List<CakeMovement> stocks = cakeStockMapper.findAllStocksForUpdate();
            if (cakeMovementMapper.existsDailyReset(bizDate)) {
                log.info("업무=재고초기화 event=건너뜀 trigger={} biz_date={} reason=이미초기화", trigger, bizDate);
                return false;
            }
            String day = bizDate.format(DateTimeFormatter.BASIC_ISO_DATE);
            for (CakeMovement stock : stocks) {
                int quantity = stock.getStock() == null ? 0 : stock.getStock();
                CakeMovement movement = new CakeMovement();
                movement.setBizDate(bizDate);
                movement.setCakeId(stock.getCakeId());
                movement.setCakeSize(stock.getCakeSize());
                movement.setDelta(-quantity);
                movement.setMoveType(DAILY_RESET);
                movement.setStockChanged(quantity != 0);
                movement.setRequestId("RESET-" + day + "-" + stock.getCakeId() + "-" + stock.getCakeSize());
                movement.setMemo("일일 재고 초기화 (초기화 전 수량 " + quantity + ")");
                if (cakeMovementMapper.insertDailyResetMovement(movement) != 1) {
                    throw new IllegalStateException("Expected exactly one daily reset movement");
                }
            }
            int affectedRows = cakeStockMapper.resetAllStocks();
            log.info("업무=재고초기화 event=DB처리반환 trigger={} biz_date={} stockRows={} affectedRows={} commit=미확인",
                    trigger, bizDate, stocks.size(), affectedRows);
            return true;
        });
        if (Boolean.TRUE.equals(reset)) {
            // 커밋 후 열린 화면(대시보드·제작관리)이 새 재고를 다시 불러오게 알린다.
            log.info("업무=재고초기화 event=완료 trigger={} biz_date={} commit=완료", trigger, bizDate);
            eventPublisher.publishEvent(new DataChangedEvent());
        }
        return Boolean.TRUE.equals(reset);
    }

    @Transactional // 제작 완료
    public void produce(Long cakeId, Integer cakeSize) {
        // 스톡 증가
        if (cakeStockMapper.increaseStock(cakeId, cakeSize) != 1) {
            // 스톡 증가 중 예외 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }

        String requestId = UUID.randomUUID().toString();
        // 제작 완료 이력 저장
        if (cakeMovementMapper.insertStockMovement(cakeId, cakeSize, "PRODUCED", true, requestId) != 1) {
            // 제작 완료 이력 저장 중 예외 발생
            throw new IllegalStateException("Expected exactly one production movement");
        }
    }

    @Transactional // 제작 취소
    public boolean cancelProduction(Long cakeId, Integer cakeSize) {
        // 스톡 수량 확인
        Integer quantity = cakeStockMapper.findQuantityForUpdate(cakeId, cakeSize);
        if (quantity == null || quantity == 0) {
            // 재고 없음: 스톡 감소 불가
            return false;
        }

        // 스톡 감소
        if (cakeStockMapper.decreaseStock(cakeId, cakeSize) != 1) {
            // 스톡 감소 중 예외 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }

        String requestId = UUID.randomUUID().toString();
        // 제작 취소 이력 저장
        if (cakeMovementMapper.insertStockMovement(cakeId, cakeSize, "UNDO_PRODUCED", true, requestId) != 1) {
            // 제작 취소 이력 저장 중 예외 발생
            throw new IllegalStateException("Expected exactly one production movement");
        }
        return true;
    }

    @Transactional // 현장 판매
    public void sellOnSite(Long cakeId, Integer cakeSize) {
        // 스톡 수량 확인
        Integer quantity = cakeStockMapper.findQuantityForUpdate(cakeId, cakeSize);

        boolean stockChanged = quantity != null && quantity > 0;
        // 스톡 재고 있으면 스톡 감소
        if (stockChanged && cakeStockMapper.decreaseStock(cakeId, cakeSize) != 1) {
            // 스톡 감소 중 예외 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }

        String requestId = UUID.randomUUID().toString();
        // 현장 판매 이력 저장
        if (cakeMovementMapper.insertStockMovement(cakeId, cakeSize, "ON_SITE_SOLD", stockChanged, requestId) != 1) {
            // 현장 판매 이력 저장 중 예외 발생
            throw new IllegalStateException("Expected exactly one on-site sale movement");
        }
    }
}
