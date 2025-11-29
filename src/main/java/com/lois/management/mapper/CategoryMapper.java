package com.lois.management.mapper;

import com.lois.management.domain.Category;
import com.lois.management.domain.Employee;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper {
    //CRUD
    //C : 생성하기
    void insert(Category category);

    //R : 조회하기
    //전체 조회
    List<Category> findAll();
    //아이디로 조회
    Category findById(Long id);

    //U : 수정하기
    int update(Category category);

    //D : 삭제하기
    int delete(Category id);
}
