package board.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 파일 내려받기 (2-4 경로 조작).
 * 정규화한 뒤 기준 폴더(BASE) 안인지 Path.startsWith 로 검사한다.
 */
@RestController
public class FileController {

    private final Path BASE;

    public FileController(@Value("${app.files.dir}") String dir) {
        // 강의 화면의 /srv/files 에 해당. 기본값은 ./data/files
        this.BASE = Path.of(dir).toAbsolutePath().normalize();
    }

    @GetMapping("/api/files")
    ResponseEntity<Resource> file(@RequestParam String name) {
        Path target = BASE.resolve(name).normalize();
        // String 이 아니라 Path 의 startsWith ("/srv/files2" 같은 형제 폴더도 걸러진다)
        if (!target.startsWith(BASE))
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST);
        if (!Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(target.getFileName().toString()).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(new FileSystemResource(target));
    }
}
