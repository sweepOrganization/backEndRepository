package com.sweep.project.favoritelocation.controller;

import com.sweep.project.favoritelocation.dto.FavoriteLocationCreateRequest;
import com.sweep.project.favoritelocation.dto.FavoriteLocationResponse;
import com.sweep.project.favoritelocation.service.FavoriteLocationService;
import com.sweep.project.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "즐겨찾기 장소", description = "국내 대중교통 장소 즐겨찾기 API")
@RestController
@RequestMapping("/favorite-places")
@RequiredArgsConstructor
public class FavoriteLocationController {

    private final FavoriteLocationService favoriteLocationService;

    @Operation(
            summary = "즐겨찾기 장소 등록",
            description = """
                    국내 대중교통 경로 탐색에 사용할 즐겨찾기 장소를 등록합니다.
                    - name, placeName, address, x, y
                    - 회원 ID는 인증 토큰에서 자동으로 식별됩니다.
                    - 동일 회원의 name 중복 불가능
                    - 회원당 최대 5개까지 등록할 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "즐겨찾기 등록 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponseUtil.class))),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 비즈니스 규칙 위반",
                    content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @PostMapping
    public ApiResponseUtil<FavoriteLocationResponse> create(
            @Valid @RequestBody FavoriteLocationCreateRequest request
    ) {
        return ApiResponseUtil.SuccessApiResponse("즐겨찾기 등록 성공", favoriteLocationService.create(request));
    }

    @Operation(
            summary = "내 즐겨찾기 장소 목록 조회",
            description = """
                    로그인한 사용자의 즐겨찾기 장소 목록을 최근 등록순으로 조회합니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponseUtil.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @GetMapping
    public ApiResponseUtil<List<FavoriteLocationResponse>> getMyFavorites() {
        return ApiResponseUtil.SuccessApiResponse("즐겨찾기 목록 조회 성공", favoriteLocationService.getMyFavorites());
    }

    @Operation(
            summary = "즐겨찾기 장소 삭제",
            description = """
                    즐겨찾기 장소를 삭제합니다.
                    - 본인 소유의 즐겨찾기만 삭제할 수 있습니다.
                    - 존재하지 않는 ID 요청 시 404를 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "즐겨찾기 삭제 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponseUtil.class))),
            @ApiResponse(responseCode = "400", description = "본인 소유가 아닌 즐겨찾기 삭제 시도",
                    content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 즐겨찾기 ID",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @DeleteMapping("/{id}")
    public ApiResponseUtil<Void> delete(
            @Parameter(description = "삭제할 즐겨찾기 ID", required = true, example = "1")
            @PathVariable Long id
    ) {
        favoriteLocationService.delete(id);
        return ApiResponseUtil.SuccessApiResponse("즐겨찾기 삭제 성공", null);
    }
}
