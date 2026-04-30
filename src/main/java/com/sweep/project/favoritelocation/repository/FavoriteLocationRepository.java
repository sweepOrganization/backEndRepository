package com.sweep.project.favoritelocation.repository;

import com.sweep.project.favoritelocation.domain.FavoriteLocation;
import com.sweep.project.favoritelocation.domain.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteLocationRepository extends JpaRepository<FavoriteLocation, Long> {

    @Query(value = """
            SELECT * FROM favorite_location
            WHERE member_id = :memberId
            ORDER BY
                CASE location_type
                    WHEN 'HOME' THEN 0
                    WHEN 'WORK' THEN 1
                    ELSE 2
                END,
                created_at DESC
            """, nativeQuery = true)
    List<FavoriteLocation> findAllByMemberIdOrdered(@Param("memberId") Long memberId);

    long countByMember_Id(Long memberId);

    boolean existsByMember_IdAndLocationType(Long memberId, LocationType locationType);

    boolean existsByMember_IdAndName(Long memberId, String name);
}
