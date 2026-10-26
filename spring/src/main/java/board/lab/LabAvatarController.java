/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 2-5 파일 업로드: 헤더만 믿고, 원본 이름 그대로, 공개 폴더에 저장. 안전한 쪽은 board.upload.AvatarController */
@RestController
@Profile("lab-vulnerable")
public class LabAvatarController {
    private final Path webRoot;

    public LabAvatarController(@Value("${app.lab.upload-dir}") String dir) throws IOException {
        this.webRoot = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(webRoot);
    }

    @PostMapping("/lab/avatars")
    Map<String, String> upload(@RequestParam("file") MultipartFile f)
        throws IOException {
      // ✗ 클라이언트가 보낸 헤더만 확인
      String ct = f.getContentType();
      if (ct == null || !ct.startsWith("image/"))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
      // ✗ 원본 이름 그대로 "웹 루트" 에 저장
      //   (실습 PC 보호를 위해 디렉터리 부분만 떼어 낸다. 이름·확장자는 그대로 남는다)
      String name = Path.of(f.getOriginalFilename()).getFileName().toString();
      f.transferTo(webRoot.resolve(name));
      return Map.of("url", "/lab/uploads/" + name);
    }

    // ✗ 올린 파일을 inline 으로, 확장자로 추정한 타입 그대로 내려준다 (nosniff·attachment 없음)
    @GetMapping("/lab/uploads/{name:.+}")
    ResponseEntity<Resource> serve(@PathVariable String name) {
        Path file = webRoot.resolve(Path.of(name).getFileName().toString());
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.TEXT_HTML);
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(file));
    }
}
