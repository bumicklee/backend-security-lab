package board;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.user.AppUser;
import board.user.AppUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 3-6 MFA(비밀번호 + 패스키)와 세션 탈취 대응(비밀번호 변경 시 모든 세션 종료) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaSessionTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PublicKeyCredentialUserEntityRepository userEntities;
    @Autowired UserCredentialRepository credentials;

    private String newUser(String password) {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new AppUser(name, name + "@example.com", encoder.encode(password), "USER"));
        return name;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** 패스키를 이미 등록한 사용자로 만든다 (실제 등록 의식은 브라우저·인증기가 필요하다) */
    private void registerPasskey(String username) {
        PublicKeyCredentialUserEntity entity = ImmutablePublicKeyCredentialUserEntity.builder()
            .name(username).id(Bytes.random()).displayName(username).build();
        userEntities.save(entity);
        credentials.save(ImmutableCredentialRecord.builder()
            .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
            .credentialId(Bytes.random())
            .userEntityUserId(entity.getId())
            .publicKey(new ImmutablePublicKeyCose(new byte[] { 1, 2, 3 }))
            .signatureCount(0)
            .label("test key")
            .build());
    }

    @Test
    @DisplayName("비밀번호만 통과한 사용자는 패스키 인증을 요구받는다")
    void passwordOnlyUserMustUsePasskey() throws Exception {
        String u = newUser("mfa-EXAMPLE-pw-1");
        registerPasskey(u);
        MockHttpSession session = login(u, "mfa-EXAMPLE-pw-1");      // FACTOR_PASSWORD 만 있다

        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("webauthn_required"));
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 다른 기기의 세션은 401")
    void changingPasswordRevokesOtherSessions() throws Exception {
        String u = newUser("old-EXAMPLE-pw-22");
        MockHttpSession laptop = login(u, "old-EXAMPLE-pw-22");
        MockHttpSession stolen = login(u, "old-EXAMPLE-pw-22");       // 다른 기기(또는 훔친 쿠키)
        mvc.perform(get("/api/me").session(stolen)).andExpect(status().isOk());

        mvc.perform(post("/api/me/password").session(laptop).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"old-EXAMPLE-pw-22\",\"newPassword\":\"new-EXAMPLE-pw-33\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").session(stolen)).andExpect(status().isUnauthorized());
        login(u, "new-EXAMPLE-pw-33");                                  // 새 비밀번호로 다시 로그인
    }
}
