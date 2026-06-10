package com.sweep.project.favoritelocation.repository;

import com.sweep.project.favoritelocation.domain.FavoriteLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoriteLocationRepository extends JpaRepository<FavoriteLocation, Long> {

    List<FavoriteLocation> findAllByMember_IdOrderByCreatedAtDesc(Long memberId);

    long countByMember_Id(Long memberId);

    boolean existsByMember_IdAndName(Long memberId, String name);
}
