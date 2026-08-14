package career.assistant.document.controller;

import career.assistant.document.dto.CustomizeResumeRequest;
import career.assistant.document.dto.ResumeDetailsResponse;
import career.assistant.document.dto.ResumeSummaryResponse;
import career.assistant.document.model.ResumeDownload;
import career.assistant.document.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/resumes", "/api/resume-versions"})
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    @PostMapping(path = {"", "/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeDetailsResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "master", defaultValue = "false") boolean master
    ) {
        return ResponseEntity.status(201).body(service.upload(file, master));
    }

    @GetMapping
    public List<ResumeSummaryResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ResumeDetailsResponse details(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(
            @PathVariable UUID id,
            @RequestParam(name = "inline", defaultValue = "false") boolean inline
    ) {
        ResumeDownload download = service.download(id);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(download.content()));
    }

    @PostMapping("/{id}/activate")
    public ResumeDetailsResponse activate(@PathVariable UUID id) {
        return service.activateMaster(id);
    }

    @PutMapping("/{id}/master")
    public ResumeDetailsResponse markMaster(@PathVariable UUID id) {
        return service.activateMaster(id);
    }

    @PostMapping("/{id}/parse")
    public ResumeDetailsResponse reparse(@PathVariable UUID id) {
        return service.reparse(id);
    }

    @PostMapping({"/customized", "/customize"})
    public ResponseEntity<ResumeDetailsResponse> customize(@Valid @RequestBody CustomizeResumeRequest request) {
        return ResponseEntity.status(201).body(service.createCustomized(request.jobId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
