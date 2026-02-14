package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.mapper.CakeMapper;
import com.lois.management.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CakeService {
    private final CakeMapper cakeMapper;


    public List<String> getAllFlavors() {
        return cakeMapper.findAllFlavors();
    }

    public List<Cake> findAllIdFlavor() {
        return cakeMapper.findAllIdFlavor();

    }

    public List<Cake> findFlavorsForDashboard() {
        return cakeMapper.findFlavorsForDashboard();
    }
}
