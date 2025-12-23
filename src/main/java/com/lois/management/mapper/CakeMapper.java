package com.lois.management.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CakeMapper {

    List<String> findAllFlavors();

}
