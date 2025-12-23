package com.lois.management.service;

import com.lois.management.mapper.CakeMapper;
import com.lois.management.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CakeService {
    private final CakeMapper cakeMapper;


    public List<String> getAllFlavors() {
        return cakeMapper.findAllFlavors();
    }
}
