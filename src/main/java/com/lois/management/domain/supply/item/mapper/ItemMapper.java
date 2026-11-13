package com.lois.management.domain.supply.item.mapper;

import com.lois.management.domain.supply.item.Item;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ItemMapper {
    //CRUD
    //C : 생성하기
    void insert(Item item);

    //R : 조회하기
    //전체 조회
    List<Item> findAll();

    List<Item> findAllOrderByPopularity();

    List<Item> findAllOrderByNameAsc();

    List<Item> findByCategory(String category);

    //아이디로 조회
    Item findById(Long id);

    //U : 수정하기
    int update(Item item);

    //D : 삭제하기
    int delete(Long id);



}
