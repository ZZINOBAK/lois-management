package com.lois.management.mapper;

import com.lois.management.domain.Employee;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

public interface EmployeeMapper {
    //CRUD
    //C : 생성하기
    void insert(Employee employee);

    //R : 조회하기
    //전체 조회
    List<Employee> findAll();
    //아이디로 조회
    Employee findById(Long id);

    //U : 수정하기
    int update(Employee employee);

    //D : 삭제하기
    int delete(Long id);
}
