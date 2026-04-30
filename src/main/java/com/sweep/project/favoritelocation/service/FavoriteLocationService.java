package com.sweep.project.favoritelocation.service;

import com.sweep.project.favoritelocation.domain.FavoriteLocation;
import com.sweep.project.favoritelocation.domain.LocationType;
import com.sweep.project.favoritelocation.dto.FavoriteLocationCreateRequest;
import com.sweep.project.favoritelocation.dto.FavoriteLocationResponse;
import com.sweep.project.favoritelocation.repository.FavoriteLocationRepository;
import com.sweep.project.member.domain.Member;
import com.sweep.project.member.service.SecurityMemberReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FavoriteLocationService {

    private static final int MAX_FAVORITE_COUNT = 5;

    private final FavoriteLocationRepository favoriteLocationRepository;
    private final SecurityMemberReadService securityMemberReadService;

    public FavoriteLocationResponse create(FavoriteLocationCreateRequest req) {
        Member member = securityMemberReadService.securityMemberRead();

        if (Boolean.TRUE.equals(member.getDeleted())) {
            throw new IllegalStateException("탈퇴한 회원은 즐겨찾기를 추가할 수 없습니다.");
        }

        long count = favoriteLocationRepository.countByMember_Id(member.getId());
        if (count >= MAX_FAVORITE_COUNT) {
            throw new IllegalStateException("즐겨찾기는 최대 " + MAX_FAVORITE_COUNT + "개까지 등록할 수 있습니다.");
        }

        if (req.locationType() != LocationType.CUSTOM
                && favoriteLocationRepository.existsByMember_IdAndLocationType(member.getId(), req.locationType())) {
            throw new IllegalStateException(req.locationType().name() + " 유형은 회원당 1개만 등록할 수 있습니다.");
        }

        if (favoriteLocationRepository.existsByMember_IdAndName(member.getId(), req.name())) {
            throw new IllegalArgumentException("이미 사용 중인 즐겨찾기 이름입니다: " + req.name());
        }

        FavoriteLocation favoriteLocation = FavoriteLocation.builder()
                .member(member)
                .name(req.name())
                .address(req.address())
                .x(req.x())
                .y(req.y())
                .locationType(req.locationType())
                .build();

        return new FavoriteLocationResponse(favoriteLocationRepository.save(favoriteLocation));
    }

    @Transactional(readOnly = true)
    public List<FavoriteLocationResponse> getMyFavorites() {
        Member member = securityMemberReadService.securityMemberRead();
        return favoriteLocationRepository.findAllByMemberIdOrdered(member.getId())
                .stream()
                .map(FavoriteLocationResponse::new)
                .collect(Collectors.toList());
    }

    public void delete(Long id) {
        FavoriteLocation favoriteLocation = favoriteLocationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 즐겨찾기입니다. id=" + id));

        Member member = securityMemberReadService.securityMemberRead();
        if (!favoriteLocation.getMemberId().equals(member.getId())) {
            throw new IllegalStateException("본인의 즐겨찾기만 삭제할 수 있습니다.");
        }

        favoriteLocationRepository.delete(favoriteLocation);
    }
}
