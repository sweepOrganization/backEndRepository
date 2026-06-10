package com.sweep.project.member.controller;

import com.sweep.project.member.service.MemberService;
import com.sweep.project.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "로그아웃", description = "Access 토큰을 무효화하고 로그아웃합니다. 실제 처리는 Spring Security 필터에서 수행됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "토큰이 없습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization",
            description = "요청시 토큰값을 넣어주셔야됩니다.",
            required = true,
            example = "Bearer [tokenvalue]",
            in = ParameterIn.HEADER)
    @PostMapping("/logout")
    public ApiResponseUtil<Object> logout() {
        // Spring Security LogoutSuccessHandler(CustomLogOutHandler)가 실제 처리
        return ApiResponseUtil.SuccessApiResponse("로그아웃 되었습니다", null);
    }

    @Operation(summary = "회원 탈퇴",description = "회원을 탈퇴합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공",
                    headers = {
                            @Header(name = "Authorization", description = "Bearer [Access JWT 토큰]",
                                    schema = @Schema(type = "string")),
                    },
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "권한이 부족합니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization",
            description = "요청시 토큰값을 넣어주셔야됩니다.",
            required = true,
            example = "Bearer [tokenvalue]",
            in = ParameterIn.HEADER)
    @DeleteMapping("/delete")
    public ApiResponseUtil<Object> deleteMember(){
        memberService.deleteMember();

        return ApiResponseUtil.SuccessApiResponse("ok",null);
    }
}
