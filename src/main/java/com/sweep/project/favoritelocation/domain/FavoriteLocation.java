package com.sweep.project.favoritelocation.domain;

import com.sweep.project.member.domain.Member;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_location",
        uniqueConstraints = @UniqueConstraint(name = "uq_member_name", columnNames = {"member_id", "name"}))
@Getter
@NoArgsConstructor
public class FavoriteLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 10)
    private LocationType locationType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public FavoriteLocation(Member member, String name, String address,
                            double x, double y, LocationType locationType) {
        this.member = member;
        this.name = name;
        this.address = address;
        this.x = x;
        this.y = y;
        this.locationType = locationType;
        this.createdAt = LocalDateTime.now();
    }

    public Long getMemberId() {
        return member.getId();
    }
}
