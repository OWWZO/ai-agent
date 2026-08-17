package org.wwz.ai.trigger.http.admin;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageService;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.List;
import java.util.Map;

/**
 * 技能管理：zip 预览/上传、粘贴新建、URL 导入（对齐 agentic-rag skill 管理）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/skills")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class SkillAdminController {

    private final SkillPackageService skillPackageService;

    @GetMapping("/list")
    public Response<List<Map<String, Object>>> list() {
        return ok(skillPackageService.listInstalled());
    }

    @PostMapping(value = "/parse-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Map<String, Object>> parsePackage(@RequestPart("file") MultipartFile file) {
        try {
            return ok(skillPackageService.previewZipAsMap(file.getBytes()));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace
    ) {
        try {
            return ok(skillPackageService.installZip(
                    file.getBytes(),
                    file.getOriginalFilename() == null ? "skill.zip" : file.getOriginalFilename(),
                    replace));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping("/create")
    public Response<Map<String, Object>> create(@RequestBody CreateSkillBody body) {
        try {
            return ok(skillPackageService.installFromMarkdown(
                    body == null ? null : body.getName(),
                    body == null ? null : body.getDescription(),
                    body == null ? null : body.getContent(),
                    body != null && Boolean.TRUE.equals(body.getReplace())));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping("/import-url")
    public Response<Map<String, Object>> importUrl(@RequestBody ImportUrlBody body) {
        try {
            return ok(skillPackageService.installFromUrl(
                    body == null ? null : body.getUrl(),
                    body != null && Boolean.TRUE.equals(body.getReplace())));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @DeleteMapping("/{name}")
    public Response<Boolean> delete(@PathVariable("name") String name) {
        try {
            return ok(skillPackageService.deleteSkill(name));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping("/reload")
    public Response<Boolean> reload() {
        skillPackageService.reload();
        return ok(true);
    }

    private <T> Response<T> ok(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> fail(Exception e) {
        log.warn("skill admin failed: {}", e.getMessage());
        String msg = e instanceof SkillLoadException
                ? e.getMessage()
                : (e.getMessage() == null ? ResponseCode.UN_ERROR.getInfo() : e.getMessage());
        return Response.<T>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(msg)
                .data(null)
                .build();
    }

    @Data
    public static class CreateSkillBody {
        private String name;
        private String description;
        private String content;
        private Boolean replace;
    }

    @Data
    public static class ImportUrlBody {
        private String url;
        private Boolean replace;
    }
}
