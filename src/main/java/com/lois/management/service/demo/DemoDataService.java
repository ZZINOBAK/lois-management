package com.lois.management.service.demo;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Category;
import com.lois.management.domain.Item;
import com.lois.management.domain.Reservation;
import com.lois.management.domain.StockRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DemoDataService {
    private static final String SESSION_KEY = "loisDemoData";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public DemoData data(HttpSession session) {
        DemoData data = (DemoData) session.getAttribute(SESSION_KEY);
        if (data == null) {
            data = seed();
            session.setAttribute(SESSION_KEY, data);
        }
        return data;
    }

    public List<Reservation> findReservations(HttpSession session, String range, String sort, LocalDate date) {
        LocalDate today = LocalDate.now(KST);
        return data(session).reservations.stream()
                .filter(reservation -> matchesRange(reservation, range, date, today))
                .filter(reservation -> !"waiting".equals(normalize(sort))
                        || "WAITING".equals(reservation.getPickupStatus()))
                .sorted(reservationComparator(sort))
                .map(this::copy)
                .toList();
    }

    public List<Reservation> searchReservations(HttpSession session, String contactSuffix) {
        String suffix = contactSuffix == null ? "" : contactSuffix.trim();
        return data(session).reservations.stream()
                .filter(reservation -> digits(reservation.getContact()).endsWith(suffix))
                .sorted(reservationComparator(""))
                .map(this::copy)
                .toList();
    }

    public Optional<Reservation> findReservation(HttpSession session, Long id) {
        return data(session).reservations.stream()
                .filter(reservation -> Objects.equals(reservation.getId(), id))
                .findFirst()
                .map(this::copy);
    }

    public Reservation createReservation(HttpSession session, Reservation reservation) {
        DemoData data = data(session);
        Reservation saved = copy(reservation);
        saved.setId(data.nextReservationId++);
        saved.setCakeFlavor(cakeFlavor(data, saved.getCakeId(), saved.getCakeFlavor()));
        saved.setMakeStatus(defaultString(saved.getMakeStatus(), "PENDING"));
        saved.setPickupStatus(defaultString(saved.getPickupStatus(), "WAITING"));
        saved.setPaid(Boolean.TRUE.equals(saved.getPaid()));
        saved.setCreatedAt(LocalDateTime.now(KST));
        saved.setUpdatedAt(saved.getCreatedAt());
        data.reservations.add(saved);
        return copy(saved);
    }

    public Optional<Reservation> updateReservation(HttpSession session, Long id, Reservation source) {
        DemoData data = data(session);
        for (Reservation reservation : data.reservations) {
            if (Objects.equals(reservation.getId(), id)) {
                reservation.setResDate(source.getResDate());
                reservation.setResTime(source.getResTime());
                reservation.setCakeId(source.getCakeId());
                reservation.setCakeFlavor(cakeFlavor(data, source.getCakeId(), source.getCakeFlavor()));
                reservation.setCakeSize(source.getCakeSize());
                reservation.setCandles(source.getCandles());
                reservation.setContact(source.getContact());
                reservation.setPaid(Boolean.TRUE.equals(source.getPaid()));
                reservation.setNote(source.getNote());
                reservation.setUpdatedAt(LocalDateTime.now(KST));
                return Optional.of(copy(reservation));
            }
        }
        return Optional.empty();
    }

    public void deleteReservation(HttpSession session, Long id) {
        data(session).reservations.removeIf(reservation -> Objects.equals(reservation.getId(), id));
    }

    public Optional<Reservation> togglePickup(HttpSession session, Long id) {
        return mutateReservation(session, id, reservation -> {
            boolean picked = "PICKED".equals(reservation.getPickupStatus());
            reservation.setPickupStatus(picked ? "WAITING" : "PICKED");
            reservation.setPickedUpAt(picked ? null : LocalDateTime.now(KST));
            reservation.setUpdatedAt(LocalDateTime.now(KST));
        });
    }

    public Optional<Reservation> toggleMake(HttpSession session, Long id) {
        return mutateReservation(session, id, reservation -> {
            reservation.setMakeStatus("READY".equals(reservation.getMakeStatus()) ? "PENDING" : "READY");
            reservation.setUpdatedAt(LocalDateTime.now(KST));
        });
    }

    public List<Cake> cakes(HttpSession session) {
        return data(session).cakes.stream().map(this::copy).toList();
    }

    public List<Category> categories(HttpSession session) {
        return data(session).categories.stream().map(this::copy).toList();
    }

    public List<Item> itemsByPopularity(HttpSession session) {
        return data(session).items.stream()
                .sorted(Comparator.comparingInt(Item::getCurrentQty))
                .map(this::copy)
                .toList();
    }

    public List<Item> itemsByName(HttpSession session) {
        return data(session).items.stream()
                .sorted(Comparator.comparing(Item::getItemName, Comparator.nullsLast(String::compareTo)))
                .map(this::copy)
                .toList();
    }

    public List<Item> itemsByCategory(HttpSession session, String categoryName) {
        DemoData data = data(session);
        Map<Long, String> categoryNames = data.categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getCategoryName));
        return data.items.stream()
                .filter(item -> Objects.equals(categoryNames.get(item.getCategoryId()), categoryName))
                .sorted(Comparator.comparing(Item::getItemName, Comparator.nullsLast(String::compareTo)))
                .map(this::copy)
                .toList();
    }

    public List<StockRequest> stockRequests(HttpSession session) {
        return data(session).stockRequests.stream()
                .sorted(Comparator.comparing(StockRequest::getCreatedAt))
                .map(this::copy)
                .toList();
    }

    public Optional<StockRequest> stockRequest(HttpSession session, Long id) {
        return data(session).stockRequests.stream()
                .filter(request -> Objects.equals(request.getId(), id))
                .findFirst()
                .map(this::copy);
    }

    public boolean createStockRequest(HttpSession session, Long itemId) {
        DemoData data = data(session);
        if (data.stockRequests.stream().anyMatch(request -> Objects.equals(request.getItemId(), itemId))) {
            return false;
        }

        Item item = data.items.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), itemId))
                .findFirst()
                .orElse(null);
        if (item == null) {
            return false;
        }

        StockRequest request = new StockRequest();
        request.setId(data.nextStockRequestId++);
        request.setItemId(itemId);
        request.setItemName(item.getItemName());
        request.setCategoryName(categoryName(data, item.getCategoryId()));
        request.setReqQty(Math.max(item.getMinQty() - item.getCurrentQty(), 1));
        request.setStatus("REQUESTED");
        request.setCreatedAt(LocalDateTime.now(KST));
        request.setUpdatedAt(request.getCreatedAt());
        request.setDaysSinceRequest(0L);
        data.stockRequests.add(request);
        return true;
    }

    public void deleteStockRequest(HttpSession session, Long id) {
        data(session).stockRequests.removeIf(request -> Objects.equals(request.getId(), id));
    }

    public Map<Integer, Map<Long, Integer>> toMakeMap(HttpSession session) {
        LocalDate today = LocalDate.now(KST);
        Map<Integer, Map<Long, Long>> counts = data(session).reservations.stream()
                .filter(reservation -> Objects.equals(reservation.getResDate(), today))
                .filter(reservation -> !"READY".equals(reservation.getMakeStatus()))
                .collect(Collectors.groupingBy(Reservation::getCakeSize,
                        Collectors.groupingBy(Reservation::getCakeId, LinkedHashMap::new, Collectors.counting())));
        return flattenCounts(counts);
    }

    public Map<Integer, Map<Long, Integer>> stockMap(HttpSession session) {
        Map<Integer, Map<Long, Integer>> stock = new LinkedHashMap<>();
        for (int size : List.of(1, 2)) {
            stock.put(size, new LinkedHashMap<>());
        }
        stock.get(1).put(1L, 1);
        stock.get(1).put(2L, 2);
        stock.get(2).put(3L, 1);
        return stock;
    }

    public List<Item> topItems(HttpSession session) {
        return itemsByPopularity(session).stream().limit(8).toList();
    }

    public List<Item> itemsExcept(HttpSession session, List<Item> topItems) {
        Set<Long> topIds = topItems.stream().map(Item::getId).collect(Collectors.toSet());
        return itemsByName(session).stream()
                .filter(item -> !topIds.contains(item.getId()))
                .toList();
    }

    private Optional<Reservation> mutateReservation(HttpSession session, Long id, ReservationMutation mutation) {
        for (Reservation reservation : data(session).reservations) {
            if (Objects.equals(reservation.getId(), id)) {
                mutation.apply(reservation);
                return Optional.of(copy(reservation));
            }
        }
        return Optional.empty();
    }

    private boolean matchesRange(Reservation reservation, String range, LocalDate date, LocalDate today) {
        return switch (normalize(range)) {
            case "today" -> Objects.equals(reservation.getResDate(), today);
            case "from-today" -> reservation.getResDate() != null && !reservation.getResDate().isBefore(today);
            case "date" -> date != null && Objects.equals(reservation.getResDate(), date);
            default -> true;
        };
    }

    private Comparator<Reservation> reservationComparator(String sort) {
        if ("created-at".equals(normalize(sort))) {
            return Comparator.comparing(Reservation::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing(Reservation::getResDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(Reservation::getResTime, Comparator.nullsLast(LocalTime::compareTo))
                .thenComparing(Reservation::getId, Comparator.nullsLast(Long::compareTo));
    }

    private Map<Integer, Map<Long, Integer>> flattenCounts(Map<Integer, Map<Long, Long>> counts) {
        Map<Integer, Map<Long, Integer>> result = new LinkedHashMap<>();
        counts.forEach((size, cakeCounts) -> {
            Map<Long, Integer> inner = new LinkedHashMap<>();
            cakeCounts.forEach((cakeId, count) -> inner.put(cakeId, count.intValue()));
            result.put(size, inner);
        });
        return result;
    }

    private String cakeFlavor(DemoData data, Long cakeId, String fallback) {
        if (cakeId == null) {
            return fallback;
        }
        return data.cakes.stream()
                .filter(cake -> Objects.equals(cake.getId(), cakeId))
                .findFirst()
                .map(Cake::getFlavor)
                .orElse(fallback);
    }

    private String categoryName(DemoData data, Long categoryId) {
        return data.categories.stream()
                .filter(category -> Objects.equals(category.getId(), categoryId))
                .findFirst()
                .map(Category::getCategoryName)
                .orElse("기타");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private Reservation copy(Reservation source) {
        Reservation copy = new Reservation();
        copy.setId(source.getId());
        copy.setResDate(source.getResDate());
        copy.setResTime(source.getResTime());
        copy.setCakeId(source.getCakeId());
        copy.setCakeSize(source.getCakeSize());
        copy.setCandles(source.getCandles());
        copy.setContact(source.getContact());
        copy.setPaid(source.getPaid());
        copy.setMakeStatus(source.getMakeStatus());
        copy.setNote(source.getNote());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setPickedUpAt(source.getPickedUpAt());
        copy.setPickupStatus(source.getPickupStatus());
        copy.setCakeFlavor(source.getCakeFlavor());
        copy.setRowNumber(source.getRowNumber());
        copy.setCnt(source.getCnt());
        copy.setProducedUi(source.isProducedUi());
        copy.setContactSuffix(source.getContactSuffix());
        return copy;
    }

    private Cake copy(Cake source) {
        Cake copy = new Cake();
        copy.setId(source.getId());
        copy.setFlavor(source.getFlavor());
        copy.setName(source.getName());
        copy.setCode(source.getCode());
        copy.setNote(source.getNote());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private Category copy(Category source) {
        Category copy = new Category();
        copy.setId(source.getId());
        copy.setCategoryName(source.getCategoryName());
        copy.setNote(source.getNote());
        copy.setCreateAt(source.getCreateAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private Item copy(Item source) {
        Item copy = new Item();
        copy.setId(source.getId());
        copy.setItemName(source.getItemName());
        copy.setCurrentQty(source.getCurrentQty());
        copy.setMinQty(source.getMinQty());
        copy.setCategoryId(source.getCategoryId());
        copy.setNote(source.getNote());
        copy.setCreateAt(source.getCreateAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private StockRequest copy(StockRequest source) {
        StockRequest copy = new StockRequest();
        copy.setId(source.getId());
        copy.setItemId(source.getItemId());
        copy.setReqQty(source.getReqQty());
        copy.setStatus(source.getStatus());
        copy.setNote(source.getNote());
        copy.setLastSmSAt(source.getLastSmSAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setItemName(source.getItemName());
        copy.setCategoryName(source.getCategoryName());
        copy.setDaysSinceRequest(source.getDaysSinceRequest());
        return copy;
    }

    private DemoData seed() {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime now = LocalDateTime.now(KST);

        DemoData data = new DemoData();
        data.cakes.add(cake(1L, "딸기 생크림", "strawberry"));
        data.cakes.add(cake(2L, "초코 가나슈", "choco"));
        data.cakes.add(cake(3L, "말차 크림", "matcha"));
        data.cakes.add(cake(4L, "얼그레이", "earlgrey"));

        data.categories.add(category(1L, "케이크"));
        data.categories.add(category(2L, "음료"));
        data.categories.add(category(3L, "스티커"));
        data.categories.add(category(4L, "베이킹"));

        data.items.add(item(1L, "1호 박스", 2, 10, 1L));
        data.items.add(item(2L, "2호 박스", 1, 8, 1L));
        data.items.add(item(3L, "아이스팩", 4, 20, 1L));
        data.items.add(item(4L, "딸기 시럽", 1, 5, 2L));
        data.items.add(item(5L, "아메리카노 원두", 3, 6, 2L));
        data.items.add(item(6L, "생일 스티커", 0, 15, 3L));
        data.items.add(item(7L, "초코 펜", 4, 12, 4L));
        data.items.add(item(8L, "버터", 2, 8, 4L));
        data.items.add(item(9L, "휘핑크림", 2, 9, 4L));
        data.items.add(item(10L, "케이크 칼", 5, 20, 1L));

        data.reservations.add(reservation(1L, today, LocalTime.of(11, 0), 1L, 1,
                "010-0000-1122", true, "READY", "WAITING", "초 3개", now.minusHours(4)));
        data.reservations.add(reservation(2L, today, LocalTime.of(14, 30), 2L, 2,
                "010-0000-3344", false, "PENDING", "WAITING", "문구: 축하해", now.minusHours(2)));
        data.reservations.add(reservation(3L, today, LocalTime.of(17, 0), 3L, 1,
                "당일-0000-7788", true, "PENDING", "WAITING", "픽업 전 연락", now.minusMinutes(30)));
        data.reservations.add(reservation(4L, today.plusDays(1), LocalTime.of(13, 0), 4L, 2,
                "010-0000-9911", true, "PENDING", "WAITING", "레터링 짧게", now.minusDays(1)));
        data.reservations.add(reservation(5L, today.minusDays(1), LocalTime.of(16, 0), 1L, 1,
                "010-0000-5566", true, "READY", "PICKED", "데모 완료건", now.minusDays(2)));

        data.stockRequests.add(stockRequest(1L, data.items.get(0), "케이크", now.minusDays(2)));
        data.stockRequests.add(stockRequest(2L, data.items.get(5), "스티커", now.minusDays(1)));
        data.stockRequests.add(stockRequest(3L, data.items.get(7), "베이킹", now.minusHours(5)));
        data.nextReservationId = 6L;
        data.nextStockRequestId = 4L;
        return data;
    }

    private Cake cake(Long id, String flavor, String code) {
        Cake cake = new Cake();
        cake.setId(id);
        cake.setFlavor(flavor);
        cake.setName(flavor);
        cake.setCode(code);
        return cake;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setCategoryName(name);
        return category;
    }

    private Item item(Long id, String name, int currentQty, int minQty, Long categoryId) {
        Item item = new Item();
        item.setId(id);
        item.setItemName(name);
        item.setCurrentQty(currentQty);
        item.setMinQty(minQty);
        item.setCategoryId(categoryId);
        return item;
    }

    private Reservation reservation(Long id, LocalDate date, LocalTime time, Long cakeId, int size,
                                    String contact, boolean paid, String makeStatus, String pickupStatus,
                                    String note, LocalDateTime createdAt) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setResDate(date);
        reservation.setResTime(time);
        reservation.setCakeId(cakeId);
        reservation.setCakeFlavor(switch (cakeId.intValue()) {
            case 1 -> "딸기 생크림";
            case 2 -> "초코 가나슈";
            case 3 -> "말차 크림";
            default -> "얼그레이";
        });
        reservation.setCakeSize(size);
        reservation.setCandles(3);
        reservation.setContact(contact);
        reservation.setPaid(paid);
        reservation.setMakeStatus(makeStatus);
        reservation.setPickupStatus(pickupStatus);
        reservation.setNote(note);
        reservation.setCreatedAt(createdAt);
        reservation.setUpdatedAt(createdAt);
        reservation.setPickedUpAt("PICKED".equals(pickupStatus) ? createdAt.plusHours(1) : null);
        return reservation;
    }

    private StockRequest stockRequest(Long id, Item item, String categoryName, LocalDateTime createdAt) {
        StockRequest request = new StockRequest();
        request.setId(id);
        request.setItemId(item.getId());
        request.setItemName(item.getItemName());
        request.setCategoryName(categoryName);
        request.setReqQty(Math.max(item.getMinQty() - item.getCurrentQty(), 1));
        request.setStatus("REQUESTED");
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(createdAt);
        request.setDaysSinceRequest(ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now(KST)));
        return request;
    }

    private interface ReservationMutation {
        void apply(Reservation reservation);
    }

    public static class DemoData {
        private long nextReservationId;
        private long nextStockRequestId;
        private final List<Reservation> reservations = new ArrayList<>();
        private final List<StockRequest> stockRequests = new ArrayList<>();
        private final List<Cake> cakes = new ArrayList<>();
        private final List<Category> categories = new ArrayList<>();
        private final List<Item> items = new ArrayList<>();
    }
}
