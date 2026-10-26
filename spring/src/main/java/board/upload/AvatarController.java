package board.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 프로필 이미지 업로드 (2-5).
 * - 형식은 클라이언트가 보낸 Content-Type 이 아니라 매직 바이트로 판별
 * - 저장 이름은 서버가 새로 만든다 (원본 이름 폐기)
 * - 저장 위치는 웹 루트 밖, 내려줄 때는 attachment + nosniff
 */
@RestController
@RequestMapping("/api/avatars")
public class AvatarController {

    // 실제 형식 → 저장 확장자 (허용 목록)
    static final Map<String, String> ALLOWED = Map.of(
        "image/png", "png",
        "image/jpeg", "jpg");
    static final Pattern ID = Pattern.compile("^[0-9a-f-]{36}\\.(png|jpg)$");

    private final Tika tika = new Tika();
    private final Path STORE;

    public AvatarController(@Value("${app.upload.dir}") String dir) throws IOException {
        this.STORE = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(STORE);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> upload(@RequestParam("file") MultipartFile f)
        throws IOException {
      if (f.isEmpty()) throw badRequest();
      // 헤더 대신 내용(매직 바이트)으로 판별
      String type = tika.detect(f.getBytes());
      String ext = ALLOWED.get(type); // png, jpg
      if (ext == null) throw badRequest();
      // 이름은 새로, 저장은 웹 루트 밖
      var name = UUID.randomUUID() + "." + ext;
      f.transferTo(STORE.resolve(name));
      return Map.of("id", name);
    }

    @GetMapping("/{id}")
    ResponseEntity<Resource> download(@PathVariable String id) {
        if (!ID.matcher(id).matches()) throw badRequest();
        Path file = STORE.resolve(id);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok()
            .contentType(id.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
            // 브라우저가 실행·렌더링하지 않고 내려받게 한다
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(id).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(new FileSystemResource(file));
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported file");
    }
}
