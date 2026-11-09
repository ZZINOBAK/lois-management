package com.lois.management.domain.cake.service;

import com.lois.management.domain.cake.Cake;
import com.lois.management.domain.cake.mapper.CakeMapper;
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

    public List<Cake> findAllIdFlavor() {
        return cakeMapper.findAllIdFlavor();

    }

    public List<Cake> findFlavorsForDashboard() {
        return cakeMapper.findFlavorsForDashboard();
    }
}
