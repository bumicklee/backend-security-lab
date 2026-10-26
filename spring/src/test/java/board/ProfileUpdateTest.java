package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.user.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 4-1 Mass assignment 와 과도한 데이터 노출: 요청 DTO(ProfileUpdate) · 응답 DTO(ProfileView) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileUpdateTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    ResultActions patchMe(String json) throws Exception {
        return mvc.perform(patch("/api/users/me").with(user("alice")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("role이 섞인 프로필 수정은 400")
    void roleIsRejected() throws Exception {
        patchMe("{\"nickname\":\"앨리스\",\"role\":\"ADMIN\"}").andExpect(status().isBadRequest());
        assertThat(users.findByUsername("alice").orElseThrow().getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("프로필 응답에 비밀번호 해시가 없다")
    void responseHasOnlyProfileFields() throws Exception {
        patchMe("{\"nickname\":\"앨리스\",\"bio\":\"안녕하세요\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.nickname").value("앨리스"))
            .andExpect(jsonPath("$.bio").value("안녕하세요"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.resetToken").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.length()").value(3));
    }
}
