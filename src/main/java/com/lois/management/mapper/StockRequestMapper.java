package com.lois.management.mapper;

import com.lois.management.domain.Item;
import com.lois.management.domain.StockRequest;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StockRequestMapper {

    //CRUD
    //C : 생성하기
    void insert(StockRequest stockRequest);

    //R : 조회하기
    //전체 조회
    List<StockRequest> findAll();
    //아이디로 조회
    StockRequest findById(Long id);

    int existsRequestedByItemId(StockRequest req);


    //U : 수정하기
    int update(StockRequest stockRequest);

    //D : 삭제하기
    void delete(Long id);

}
