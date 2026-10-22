package com.neueda.leap.repository;

import com.neueda.leap.entities.FifoAllocation;
import org.springframework.data.repository.Repository;

public interface FifoAllocationRepository extends Repository<FifoAllocation, Long> {
    FifoAllocation save(FifoAllocation allocation);
}
