package org.wwz.ai.infrastructure.imagegeneration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 米醋 / OpenAI 兼容图片生成客户端（从 micu-image-mcp 移植核心路由）。
 * <p>
 * 覆盖文生图、单图编辑、多图参考；支持 gpt-image-2 与 grok 通道，
 * 以及 2K/4K 自动切 pro、重试与 chat fallback。
 */
@Slf4j
public class MicuImageGenerationClient {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    private static final String PRO_MODEL = "gpt-image-2-pro";
    private static final String NONPRO_MODEL = "gpt-image-2";
    private static final int HIGH_RES_EDGE = 1600;
    private static final int MAX_N = 10;
    private static final int MIN_SIZE_EDGE = 256;
    private static final int MAX_SIZE_EDGE = 4096;
    private static final int SIZE_ALIGNMENT = 8;
    private static final long MAX_INPUT_FILE_BYTES = 4L * 1024 * 1024;
    private static final long MAX_TOTAL_INPUT_BYTES = 8L * 1024 * 1024;
    private static final long MAX_RESPONSE_BYTES = 25L * 1024 * 1024;
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(
            0, 408, 409, 425, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524, 525, 527
    );
    private static final Set<Integer> FALLBACK_STATUS = Set.of(
            0, 408, 500, 502, 503, 504, 520, 521, 522, 523, 524, 525, 527
    );
    private static final Pattern SIZE_PATTERN = Pattern.compile("^(\\d+)x(\\d+)$");
    private static final Pattern PROMPT_SIZE_PATTERN = Pattern.compile("(\\d{3,4})\\s*[x×]\\s*(\\d{3,4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_URL_PATTERN = Pattern.compile(
            "^data:(?<mime>[^;,]+);base64,(?<data>.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\((https?://[^)\\s]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_IMAGE_PATTERN = Pattern.compile("https?://[^\\s\"'<>)]+\\.(?:png|jpe?g|gif|webp)(?:\\?[^\\s\"'<>)]*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE64_IN_TEXT = Pattern.compile("data:image/[^;]+;base64,([A-Za-z0-9+/=\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Double> GROK_ASPECT_RATIOS = buildGrokAspectRatios();

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final String grokBaseUrl;
    private final String grokApiKey;
    private final String defaultGrokModel;
    private final String previewBaseUrl;

    public MicuImageGenerationClient(ClientConfig config) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(config.getBaseUrl(), "baseUrl"));
        this.apiKey = Objects.requireNonNull(config.getApiKey(), "apiKey");
        this.defaultModel = StringUtils.hasText(config.getDefaultModel()) ? config.getDefaultModel().trim() : NONPRO_MODEL;
        this.grokBaseUrl = StringUtils.hasText(config.getGrokBaseUrl())
                ? trimTrailingSlash(config.getGrokBaseUrl())
                : this.baseUrl;
        this.grokApiKey = StringUtils.hasText(config.getGrokApiKey()) ? config.getGrokApiKey().trim() : this.apiKey;
        this.defaultGrokModel = StringUtils.hasText(config.getDefaultGrokModel())
                ? config.getDefaultGrokModel().trim()
                : "grok-imagine-image-lite";
        this.previewBaseUrl = StringUtils.hasText(config.getPreviewBaseUrl())
                ? trimTrailingSlash(config.getPreviewBaseUrl())
                : null;
        long timeoutSeconds = config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0
                ? 900L
                : config.getTimeoutSeconds();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    public GenerationResult generate(GenerationRequest request) {
        if (request == null || !StringUtils.hasText(request.getPrompt())) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        String mode = resolveMode(request);
        int n = request.getN() == null || request.getN() < 1 ? 1 : Math.min(request.getN(), MAX_N);
        String size = resolveSize(request.getSize(), request.getPrompt(), mode);
        String model = resolveModel(request.getModel(), size);
        List<String> notes = new ArrayList<>();
        notes.add("mode=" + mode);
        notes.add("size=" + size);
        notes.add("model=" + model);

        if (isGrokModel(model)) {
            return generateWithGrok(request, mode, model, size, n, notes);
        }
        if ("images".equals(mode)) {
            return generateTextToImage(request, model, size, n, notes);
        }
        List<LoadedImage> sourceImages = loadReferenceImages(request.getFileNames(), "fileNames", request.getRequestId());
        List<LoadedImage> maskImages = loadOptionalMaskImages(request.getMaskFileNames(), sourceImages.size(), request.getRequestId());
        if (sourceImages.size() == 1) {
            return editSingleImage(request, model, size, n, sourceImages.get(0),
                    maskImages.isEmpty() ? null : maskImages.get(0), notes);
        }
        return multiReference(request, model, size, sourceImages, notes);
    }

    private GenerationResult generateTextToImage(GenerationRequest request,
                                                 String model,
                                                 String size,
                                                 int n,
                                                 List<String> notes) {
        String tier = sizeTier(size);
        boolean isPro = model.toLowerCase(Locale.ROOT).contains("pro");
        int effectiveN = n;
        if (("2k".equals(tier) || "4k".equals(tier)) && n > 1) {
            notes.add(tier.toUpperCase(Locale.ROOT) + " 强制 N=1，已忽略请求的 n=" + n);
            effectiveN = 1;
        }
        boolean usedFallback = false;
        List<GeneratedImage> images = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < effectiveN; i++) {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("prompt", request.getPrompt());
            body.put("n", 1);
            body.put("size", size);
            body.put("response_format", "b64_json");
            CallResult call = callWithRetry(
                    buildJsonRequest(joinUrl(baseUrl, "/v1/images/generations"), body, apiKey),
                    isPro || "2k".equals(tier) || "4k".equals(tier),
                    notes
            );
            if (!call.success() && FALLBACK_STATUS.contains(call.status) && ("2k".equals(tier) || "4k".equals(tier))) {
                usedFallback = true;
                notes.add("generations HTTP " + call.status + " → fallback chat stream（size 可能不生效）");
                JSONObject chatBody = new JSONObject();
                chatBody.put("model", model);
                chatBody.put("messages", List.of(Map.of(
                        "role", "user",
                        "content", request.getPrompt()
                )));
                chatBody.put("stream", Boolean.FALSE);
                call = callWithRetry(
                        buildJsonRequest(joinUrl(baseUrl, "/v1/chat/completions"), chatBody, apiKey),
                        isPro,
                        notes
                );
            }
            if (!call.success()) {
                errors.add("#" + (i + 1) + " HTTP " + call.status + ": " + call.errorDetail());
                continue;
            }
            List<GeneratedImage> extracted = extractImages(call.body);
            if (extracted.isEmpty()) {
                errors.add("#" + (i + 1) + " 上游未返回可识别图片");
                continue;
            }
            images.add(extracted.get(0));
        }
        if (images.isEmpty()) {
            throw new IllegalStateException(errors.isEmpty() ? "文生图失败" : String.join("; ", errors));
        }
        return GenerationResult.builder()
                .mode("images")
                .model(model)
                .size(size)
                .usedFallback(usedFallback)
                .images(images)
                .notes(notes)
                .build();
    }

    private GenerationResult editSingleImage(GenerationRequest request,
                                             String model,
                                             String size,
                                             int n,
                                             LoadedImage source,
                                             LoadedImage mask,
                                             List<String> notes) {
        if ("4k".equals(sizeTier(size))) {
            throw new IllegalArgumentException(
                    "size=" + size + " (4K) 在图生图已禁用：请改用 2K，或先 1K/2K 再文生图升 4K");
        }
        boolean isPro = model.toLowerCase(Locale.ROOT).contains("pro");
        boolean usedFallback = false;
        MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
        multipart.addFormDataPart("model", model);
        multipart.addFormDataPart("prompt", request.getPrompt());
        multipart.addFormDataPart("size", size);
        multipart.addFormDataPart("response_format", "b64_json");
        multipart.addFormDataPart("n", String.valueOf(Math.max(1, Math.min(n, MAX_N))));
        multipart.addFormDataPart(
                "image",
                source.getFileName(),
                RequestBody.create(MediaType.parse(source.getMimeType()), source.getBytes())
        );
        if (mask != null) {
            multipart.addFormDataPart(
                    "mask",
                    "mask.png",
                    RequestBody.create(MediaType.parse(mask.getMimeType()), mask.getBytes())
            );
        }
        CallResult call = callWithRetry(
                buildMultipartRequest(joinUrl(baseUrl, "/v1/images/edits"), multipart.build(), apiKey),
                isPro || "2k".equals(sizeTier(size)),
                notes
        );
        if (!call.success() && FALLBACK_STATUS.contains(call.status)) {
            usedFallback = true;
            notes.add("edits HTTP " + call.status + " → fallback chat completions");
            call = callWithRetry(buildChatEditRequest(model, request.getPrompt(), size, List.of(source), mask), isPro, notes);
        }
        if (!call.success()) {
            throw new IllegalStateException("图生图失败 HTTP " + call.status + ": " + call.errorDetail());
        }
        List<GeneratedImage> images = extractImages(call.body);
        if (images.isEmpty()) {
            throw new IllegalStateException("图生图上游未返回可识别图片");
        }
        return GenerationResult.builder()
                .mode("edits")
                .model(model)
                .size(size)
                .usedFallback(usedFallback)
                .images(images)
                .notes(notes)
                .build();
    }

    private GenerationResult multiReference(GenerationRequest request,
                                            String model,
                                            String size,
                                            List<LoadedImage> sourceImages,
                                            List<String> notes) {
        if (sourceImages.size() < 2) {
            throw new IllegalArgumentException("多图参考至少需要 2 张参考图");
        }
        if (sourceImages.size() > 10) {
            throw new IllegalArgumentException("参考图最多 10 张");
        }
        if ("4k".equals(sizeTier(size))) {
            throw new IllegalArgumentException(
                    "size=" + size + " (4K) 在多图参考已禁用：请改用 1K/2K 后文生图升 4K");
        }
        boolean isPro = model.toLowerCase(Locale.ROOT).contains("pro");
        boolean usedFallback = false;
        MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
        multipart.addFormDataPart("model", model);
        multipart.addFormDataPart("prompt", request.getPrompt());
        multipart.addFormDataPart("size", size);
        multipart.addFormDataPart("response_format", "b64_json");
        for (LoadedImage image : sourceImages) {
            multipart.addFormDataPart(
                    "image[]",
                    image.getFileName(),
                    RequestBody.create(MediaType.parse(image.getMimeType()), image.getBytes())
            );
        }
        CallResult call = callWithRetry(
                buildMultipartRequest(joinUrl(baseUrl, "/v1/images/edits"), multipart.build(), apiKey),
                isPro || "2k".equals(sizeTier(size)),
                notes
        );
        if (!call.success() && FALLBACK_STATUS.contains(call.status)) {
            usedFallback = true;
            notes.add("multi-ref edits HTTP " + call.status + " → fallback chat completions");
            call = callWithRetry(buildChatEditRequest(model, request.getPrompt(), size, sourceImages, null), isPro, notes);
        }
        if (!call.success()) {
            throw new IllegalStateException("多图参考失败 HTTP " + call.status + ": " + call.errorDetail());
        }
        List<GeneratedImage> images = extractImages(call.body);
        if (images.isEmpty()) {
            throw new IllegalStateException("多图参考上游未返回可识别图片");
        }
        return GenerationResult.builder()
                .mode("edits")
                .model(model)
                .size(size)
                .usedFallback(usedFallback)
                .images(images.subList(0, 1))
                .notes(notes)
                .build();
    }

    private GenerationResult generateWithGrok(GenerationRequest request,
                                              String mode,
                                              String model,
                                              String size,
                                              int n,
                                              List<String> notes) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("prompt", request.getPrompt());
        body.put("n", Math.max(1, Math.min(n, MAX_N)));
        body.put("resolution", grokResolution(size));
        body.put("aspect_ratio", grokAspectRatio(size));
        body.put("response_format", "b64_json");
        if ("edits".equals(mode)) {
            List<LoadedImage> sourceImages = loadReferenceImages(request.getFileNames(), "fileNames", request.getRequestId());
            if (sourceImages.size() == 1) {
                body.put("reference_image", toDataUrl(sourceImages.get(0)));
                if (!CollectionUtils.isEmpty(request.getMaskFileNames())) {
                    notes.add("Grok 路径当前不支持 mask，已忽略 maskFileNames");
                }
            } else {
                List<String> imageUrls = new ArrayList<>();
                for (LoadedImage image : sourceImages) {
                    imageUrls.add(toDataUrl(image));
                }
                body.put("image_urls", imageUrls);
                body.put("prompt",
                        "Reference images are provided. Synthesize their visual elements into ONE single new image.\n\nInstruction:\n"
                                + request.getPrompt());
            }
        }
        CallResult call = callWithRetry(
                buildJsonRequest(joinUrl(grokBaseUrl, "/v1/images/generations"), body, grokApiKey),
                true,
                notes
        );
        if (!call.success()) {
            throw new IllegalStateException("Grok 生图失败 HTTP " + call.status + ": " + call.errorDetail());
        }
        List<GeneratedImage> images = extractImages(call.body);
        if (images.isEmpty()) {
            throw new IllegalStateException("Grok 上游未返回可识别图片");
        }
        return GenerationResult.builder()
                .mode(mode)
                .model(model)
                .size(size)
                .usedFallback(false)
                .images(images)
                .notes(notes)
                .build();
    }

    private Request buildChatEditRequest(String model,
                                         String prompt,
                                         String size,
                                         List<LoadedImage> images,
                                         LoadedImage mask) {
        List<Map<String, Object>> content = new ArrayList<>();
        String sizeDirective = StringUtils.hasText(size)
                ? "Output the full edited image at exactly " + size + " pixels."
                : "Output the full edited image, same dimensions as the input.";
        String header = images.size() == 1
                ? "Edit the attached image as described. " + sizeDirective + "\n\nInstruction:\n" + prompt
                : "Attached are " + images.size() + " reference images. Use them together to produce one final image. "
                + sizeDirective + "\n\nInstruction:\n" + prompt;
        content.add(Map.of("type", "text", "text", header));
        for (LoadedImage image : images) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", toDataUrl(image))
            ));
        }
        if (mask != null) {
            content.add(0, Map.of(
                    "type", "text",
                    "text", "The second attached image is an alpha mask: transparent pixels mark the ONLY region to modify."
            ));
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", toDataUrl(mask))
            ));
        }
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("stream", Boolean.FALSE);
        return buildJsonRequest(joinUrl(baseUrl, "/v1/chat/completions"), body, apiKey);
    }

    private CallResult callWithRetry(Request request, boolean aggressive, List<String> notes) {
        int attempts = aggressive ? 3 : 2;
        long[] delays = aggressive ? new long[]{2_000L, 8_000L, 20_000L} : new long[]{1_000L, 3_000L};
        CallResult last = CallResult.failure(0, "未发起请求");
        for (int i = 0; i < attempts; i++) {
            last = execute(request);
            if (last.success()) {
                return last;
            }
            if (!RETRYABLE_STATUS.contains(last.status) || i == attempts - 1) {
                return last;
            }
            long delay = delays[Math.min(i, delays.length - 1)];
            notes.add("HTTP " + last.status + " 重试 " + (i + 1) + "/" + attempts + "，等待 " + delay + "ms");
            sleep(delay);
        }
        return last;
    }

    private CallResult execute(Request request) {
        try (Response response = httpClient.newCall(request).execute()) {
            byte[] raw = response.body() == null ? new byte[0] : response.body().bytes();
            if (raw.length > MAX_RESPONSE_BYTES) {
                return CallResult.failure(413, "响应体超过上限 " + (MAX_RESPONSE_BYTES / 1024 / 1024) + "MB");
            }
            String text = new String(raw, StandardCharsets.UTF_8);
            if (!response.isSuccessful()) {
                return CallResult.failure(response.code(), text);
            }
            return CallResult.success(response.code(), text);
        } catch (IOException e) {
            log.warn("图片上游调用网络异常 url={}", request.url(), e);
            return CallResult.failure(0, e.getMessage());
        }
    }

    private List<GeneratedImage> extractImages(String responseText) {
        Object parsed;
        try {
            parsed = JSON.parse(responseText);
        } catch (Exception e) {
            return extractImagesFromText(responseText);
        }
        List<GeneratedImage> images = new ArrayList<>();
        if (parsed instanceof JSONObject obj) {
            JSONArray data = obj.getJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    if (StringUtils.hasText(item.getString("b64_json"))) {
                        images.add(GeneratedImage.fromBase64(item.getString("b64_json")));
                    } else if (StringUtils.hasText(item.getString("url"))) {
                        String url = item.getString("url");
                        if (url.startsWith("data:image/")) {
                            images.add(GeneratedImage.fromDataUrl(url));
                        } else {
                            images.add(GeneratedImage.fromUrl(url));
                        }
                    }
                }
            }
            JSONArray output = obj.getJSONArray("output");
            if (output != null) {
                for (int i = 0; i < output.size(); i++) {
                    JSONObject item = output.getJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    if ("image_generation_call".equals(item.getString("type"))
                            && StringUtils.hasText(item.getString("result"))) {
                        images.add(GeneratedImage.fromDataUrlOrBase64(item.getString("result")));
                    }
                }
            }
            JSONArray choices = obj.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                if (message != null) {
                    Object content = message.get("content");
                    if (content instanceof String text) {
                        images.addAll(extractImagesFromText(text));
                    } else if (content instanceof JSONArray parts) {
                        for (int i = 0; i < parts.size(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (part == null) {
                                continue;
                            }
                            if (part.get("image_url") instanceof JSONObject imageUrl
                                    && StringUtils.hasText(imageUrl.getString("url"))) {
                                images.add(GeneratedImage.fromDataUrlOrUrl(imageUrl.getString("url")));
                            } else if (StringUtils.hasText(part.getString("text"))) {
                                images.addAll(extractImagesFromText(part.getString("text")));
                            }
                        }
                    }
                }
            }
        }
        if (images.isEmpty()) {
            images.addAll(extractImagesFromText(responseText));
        }
        return dedupe(images);
    }

    private List<GeneratedImage> extractImagesFromText(String text) {
        List<GeneratedImage> images = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return images;
        }
        Matcher dataUrl = BASE64_IN_TEXT.matcher(text);
        while (dataUrl.find()) {
            images.add(GeneratedImage.fromDataUrl(dataUrl.group(0)));
        }
        Matcher markdown = MARKDOWN_IMAGE_PATTERN.matcher(text);
        while (markdown.find()) {
            images.add(GeneratedImage.fromUrl(markdown.group(1)));
        }
        Matcher http = HTTP_IMAGE_PATTERN.matcher(text);
        while (http.find()) {
            images.add(GeneratedImage.fromUrl(http.group(0)));
        }
        return images;
    }

    private List<GeneratedImage> dedupe(List<GeneratedImage> images) {
        Map<String, GeneratedImage> unique = new LinkedHashMap<>();
        for (GeneratedImage image : images) {
            if (image == null) {
                continue;
            }
            String key = image.getDataUrl() != null
                    ? image.getDataUrl().substring(0, Math.min(120, image.getDataUrl().length()))
                    : image.getUrl();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            unique.putIfAbsent(key, image);
        }
        return new ArrayList<>(unique.values());
    }

    public byte[] materialize(GeneratedImage image) {
        if (image == null) {
            throw new IllegalStateException("图片结果为空");
        }
        if (StringUtils.hasText(image.getDataUrl())) {
            return decodeDataUrl(image.getDataUrl()).getBytes();
        }
        if (!StringUtils.hasText(image.getUrl())) {
            throw new IllegalStateException("图片结果缺少 data_url 和 url");
        }
        Request request = new Request.Builder().url(image.getUrl()).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("下载生成图片失败 HTTP " + response.code());
            }
            return response.body().bytes();
        } catch (IOException e) {
            throw new IllegalStateException("下载生成图片失败", e);
        }
    }

    public String guessMime(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "image/png";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') {
            return "image/webp";
        }
        return "image/png";
    }

    private List<LoadedImage> loadReferenceImages(List<String> references, String fieldName, String requestId) {
        if (CollectionUtils.isEmpty(references)) {
            throw new IllegalArgumentException("图生图模式至少需要一张参考图片");
        }
        List<LoadedImage> images = new ArrayList<>();
        long total = 0L;
        for (int i = 0; i < references.size(); i++) {
            LoadedImage image = loadImageReference(references.get(i), fieldName + "[" + i + "]", requestId);
            total += image.getBytes().length;
            if (total > MAX_TOTAL_INPUT_BYTES) {
                throw new IllegalArgumentException("参考图累计超过 " + (MAX_TOTAL_INPUT_BYTES / 1024 / 1024) + "MB 上限");
            }
            images.add(image);
        }
        return images;
    }

    private List<LoadedImage> loadOptionalMaskImages(List<String> references, int sourceCount, String requestId) {
        if (CollectionUtils.isEmpty(references)) {
            return List.of();
        }
        if (references.size() > sourceCount) {
            throw new IllegalArgumentException("maskFileNames 数量不能超过 fileNames");
        }
        List<LoadedImage> masks = new ArrayList<>();
        for (int i = 0; i < references.size(); i++) {
            String ref = references.get(i);
            if (!StringUtils.hasText(ref)) {
                continue;
            }
            masks.add(loadImageReference(ref, "maskFileNames[" + i + "]", requestId));
        }
        return masks;
    }

    private LoadedImage loadImageReference(String reference, String label, String requestId) {
        String normalized = normalizeReference(reference, requestId);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(label + " 为空");
        }
        if (normalized.startsWith("data:")) {
            DecodedData decoded = decodeDataUrl(normalized);
            validateInputSize(decoded.getBytes(), label);
            return new LoadedImage(label + ".png", decoded.getMimeType(), decoded.getBytes());
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            Request request = new Request.Builder().url(normalized).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException(label + " 下载失败 HTTP " + response.code() + " url=" + normalized);
                }
                byte[] bytes = response.body().bytes();
                validateInputSize(bytes, label);
                String mime = response.header("Content-Type");
                if (!StringUtils.hasText(mime) || !mime.startsWith("image/")) {
                    mime = guessMime(bytes);
                } else {
                    mime = mime.split(";", 2)[0].trim();
                }
                String fileName = extractFileName(normalized, mime);
                return new LoadedImage(fileName, mime, bytes);
            } catch (IOException e) {
                throw new IllegalStateException(label + " 下载失败", e);
            }
        }
        throw new IllegalArgumentException(label + " 仅支持 http(s)/data url，收到: " + reference);
    }

    private String normalizeReference(String reference, String requestId) {
        String value = reference == null ? "" : reference.trim();
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) {
            return encodePreviewPathIfNeeded(value);
        }
        // 相对 preview/download 路径：补全为绝对 URL
        if ((value.startsWith("/preview/") || value.startsWith("/download/"))
                && StringUtils.hasText(previewBaseUrl)) {
            return encodePreviewPathIfNeeded(previewBaseUrl + value);
        }
        if (StringUtils.hasText(previewBaseUrl) && StringUtils.hasText(requestId)) {
            String fileName = value;
            if (fileName.contains("/") || fileName.contains("\\")) {
                int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                fileName = slash >= 0 ? fileName.substring(slash + 1) : fileName;
            }
            return encodePreviewPathIfNeeded(previewBaseUrl + "/preview/" + requestId + "/" + fileName);
        }
        return value;
    }

    /**
     * 文件服务 preview/download 路径中的中文文件名需要 URL 编码，否则会 404。
     */
    private String encodePreviewPathIfNeeded(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        int markerPreview = url.indexOf("/preview/");
        int markerDownload = url.indexOf("/download/");
        int marker = markerPreview >= 0 ? markerPreview : markerDownload;
        if (marker < 0) {
            return url;
        }
        String prefixPath = markerPreview >= 0 ? "/preview/" : "/download/";
        int start = marker + prefixPath.length();
        String head = url.substring(0, start);
        String tail = url.substring(start);
        String query = "";
        int q = tail.indexOf('?');
        if (q >= 0) {
            query = tail.substring(q);
            tail = tail.substring(0, q);
        }
        String[] parts = tail.split("/", -1);
        StringBuilder encoded = new StringBuilder(head);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            if (!parts[i].isEmpty()) {
                encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        encoded.append(query);
        return encoded.toString();
    }

    private void validateInputSize(byte[] bytes, String label) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException(label + " 内容为空");
        }
        if (bytes.length > MAX_INPUT_FILE_BYTES) {
            throw new IllegalArgumentException(label + " 超过单图 " + (MAX_INPUT_FILE_BYTES / 1024 / 1024) + "MB 上限");
        }
    }

    private String resolveMode(GenerationRequest request) {
        if (StringUtils.hasText(request.getMode())) {
            String mode = request.getMode().trim().toLowerCase(Locale.ROOT);
            if ("images".equals(mode) || "edits".equals(mode)) {
                return mode;
            }
        }
        return CollectionUtils.isEmpty(request.getFileNames()) ? "images" : "edits";
    }

    private String resolveSize(String rawSize, String prompt, String mode) {
        if (StringUtils.hasText(rawSize)) {
            return validateSize(rawSize.trim().toLowerCase(Locale.ROOT), false);
        }
        String inferred = inferSizeFromPrompt(prompt);
        if (StringUtils.hasText(inferred)) {
            return inferred;
        }
        return "1024x1024";
    }

    private String resolveModel(String requestedModel, String size) {
        String model = StringUtils.hasText(requestedModel) ? requestedModel.trim() : defaultModel;
        if (isGrokModel(model)) {
            return StringUtils.hasText(requestedModel) ? model : defaultGrokModel;
        }
        String tier = sizeTier(size);
        if (("2k".equals(tier) || "4k".equals(tier)) && !model.toLowerCase(Locale.ROOT).contains("pro")) {
            return PRO_MODEL;
        }
        return model;
    }

    private boolean isGrokModel(String model) {
        if (!StringUtils.hasText(model)) {
            return false;
        }
        String lower = model.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("grok-") || lower.contains("imagine-image");
    }

    private String validateSize(String size, boolean grok) {
        Matcher matcher = SIZE_PATTERN.matcher(size);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("size 格式错误，必须是 WxH，收到 " + size);
        }
        int w = Integer.parseInt(matcher.group(1));
        int h = Integer.parseInt(matcher.group(2));
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("size W/H 必须为正数");
        }
        if (!grok) {
            if (w < MIN_SIZE_EDGE || h < MIN_SIZE_EDGE || w > MAX_SIZE_EDGE || h > MAX_SIZE_EDGE) {
                throw new IllegalArgumentException("size 边长必须在 " + MIN_SIZE_EDGE + "-" + MAX_SIZE_EDGE);
            }
            if (w % SIZE_ALIGNMENT != 0 || h % SIZE_ALIGNMENT != 0) {
                throw new IllegalArgumentException("size W/H 必须是 " + SIZE_ALIGNMENT + " 的倍数");
            }
        }
        return w + "x" + h;
    }

    private String inferSizeFromPrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return null;
        }
        String p = prompt.toLowerCase(Locale.ROOT);
        Matcher matcher = PROMPT_SIZE_PATTERN.matcher(p);
        if (matcher.find()) {
            int w = roundToAlignment(Integer.parseInt(matcher.group(1)));
            int h = roundToAlignment(Integer.parseInt(matcher.group(2)));
            if (w >= MIN_SIZE_EDGE && h >= MIN_SIZE_EDGE && w <= MAX_SIZE_EDGE && h <= MAX_SIZE_EDGE) {
                return w + "x" + h;
            }
        }
        boolean vertical = containsAny(p, "9:16", "竖屏", "竖版", "vertical", "portrait", "手机壁纸");
        boolean horizontal = containsAny(p, "16:9", "横屏", "横版", "landscape", "wallpaper", "壁纸", "banner", "封面");
        boolean square = containsAny(p, "正方形", "square", "avatar", "头像", "logo", "icon");
        if (p.matches(".*\\b4k\\b.*") || p.contains("uhd") || p.contains("超高清")) {
            return vertical ? "2160x3840" : "3840x2160";
        }
        if (p.matches(".*\\b2k\\b.*") || p.contains("1080p") || p.contains("fullhd") || p.contains("full hd")) {
            return vertical ? "1152x2048" : "2048x1152";
        }
        if (square) {
            return "1024x1024";
        }
        if (vertical) {
            return "1024x1536";
        }
        if (horizontal) {
            return "1536x1024";
        }
        return null;
    }

    private String sizeTier(String size) {
        int edge = maxEdge(size);
        if (edge == 0) {
            return "unknown";
        }
        if (edge < 1024) {
            return "small";
        }
        if (edge < HIGH_RES_EDGE) {
            return "1k";
        }
        if (edge < 3000) {
            return "2k";
        }
        return "4k";
    }

    private int maxEdge(String size) {
        Matcher matcher = SIZE_PATTERN.matcher(size == null ? "" : size);
        if (!matcher.matches()) {
            return 0;
        }
        return Math.max(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private String grokResolution(String size) {
        return maxEdge(size) >= HIGH_RES_EDGE ? "2k" : "1k";
    }

    private String grokAspectRatio(String size) {
        Matcher matcher = SIZE_PATTERN.matcher(size == null ? "" : size);
        if (!matcher.matches()) {
            return "1:1";
        }
        double ratio = Double.parseDouble(matcher.group(1)) / Double.parseDouble(matcher.group(2));
        String best = "1:1";
        double bestDelta = Double.MAX_VALUE;
        for (Map.Entry<String, Double> entry : GROK_ASPECT_RATIOS.entrySet()) {
            double delta = Math.abs(ratio - entry.getValue());
            if (delta < bestDelta) {
                bestDelta = delta;
                best = entry.getKey();
            }
        }
        return best;
    }

    private int roundToAlignment(int value) {
        return Math.max(16, (int) Math.round(value / 8.0) * 8);
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Request buildJsonRequest(String url, JSONObject body, String key) {
        return new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + key)
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(JSON_MEDIA, body.toJSONString().getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private Request buildMultipartRequest(String url, MultipartBody body, String key) {
        return new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + key)
                .addHeader("Accept", "application/json")
                .post(body)
                .build();
    }

    private String toDataUrl(LoadedImage image) {
        return "data:" + image.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(image.getBytes());
    }

    private DecodedData decodeDataUrl(String dataUrl) {
        Matcher matcher = DATA_URL_PATTERN.matcher(dataUrl.trim());
        if (!matcher.matches()) {
            byte[] raw = Base64.getDecoder().decode(padBase64(dataUrl.replaceAll("\\s+", "")));
            return new DecodedData(guessMime(raw), raw);
        }
        String mime = matcher.group("mime");
        byte[] raw = Base64.getDecoder().decode(padBase64(matcher.group("data").replaceAll("\\s+", "")));
        return new DecodedData(StringUtils.hasText(mime) ? mime : guessMime(raw), raw);
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "====".substring(remainder);
    }

    private String extractFileName(String url, String mime) {
        try {
            String path = okhttp3.HttpUrl.parse(url) == null ? url : Objects.requireNonNull(okhttp3.HttpUrl.parse(url)).encodedPath();
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            if (StringUtils.hasText(name) && name.contains(".")) {
                return name;
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (mime != null && mime.contains("jpeg")) {
            return "image.jpg";
        }
        if (mime != null && mime.contains("webp")) {
            return "image.webp";
        }
        return "image.png";
    }

    private String joinUrl(String base, String path) {
        return trimTrailingSlash(base) + path;
    }

    private String trimTrailingSlash(String value) {
        String target = value == null ? "" : value.trim();
        while (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        return target;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Double> buildGrokAspectRatios() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("1:1", 1.0);
        map.put("16:9", 16.0 / 9.0);
        map.put("9:16", 9.0 / 16.0);
        map.put("4:3", 4.0 / 3.0);
        map.put("3:4", 3.0 / 4.0);
        map.put("3:2", 3.0 / 2.0);
        map.put("2:3", 2.0 / 3.0);
        map.put("2:1", 2.0);
        map.put("1:2", 0.5);
        return map;
    }

    @Data
    @Builder
    public static class ClientConfig {
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private String grokBaseUrl;
        private String grokApiKey;
        private String defaultGrokModel;
        private String previewBaseUrl;
        private Long timeoutSeconds;
    }

    @Data
    @Builder
    public static class GenerationRequest {
        private String requestId;
        private String prompt;
        private String mode;
        private List<String> fileNames;
        private List<String> maskFileNames;
        private String model;
        private String size;
        private Integer n;
    }

    @Data
    @Builder
    public static class GenerationResult {
        private String mode;
        private String model;
        private String size;
        private boolean usedFallback;
        private List<GeneratedImage> images;
        private List<String> notes;
    }

    @Data
    public static class GeneratedImage {
        private final String dataUrl;
        private final String url;

        public static GeneratedImage fromBase64(String base64) {
            return new GeneratedImage("data:image/png;base64," + base64.replaceAll("\\s+", ""), null);
        }

        public static GeneratedImage fromDataUrl(String dataUrl) {
            return new GeneratedImage(dataUrl, null);
        }

        public static GeneratedImage fromUrl(String url) {
            return new GeneratedImage(null, url);
        }

        public static GeneratedImage fromDataUrlOrBase64(String raw) {
            if (raw != null && raw.startsWith("data:")) {
                return fromDataUrl(raw);
            }
            return fromBase64(raw);
        }

        public static GeneratedImage fromDataUrlOrUrl(String raw) {
            if (raw != null && raw.startsWith("data:")) {
                return fromDataUrl(raw);
            }
            return fromUrl(raw);
        }
    }

    @Data
    private static class LoadedImage {
        private final String fileName;
        private final String mimeType;
        private final byte[] bytes;
    }

    @Data
    private static class DecodedData {
        private final String mimeType;
        private final byte[] bytes;
    }

    private static final class CallResult {
        private final int status;
        private final String body;
        private final boolean success;

        private CallResult(int status, String body, boolean success) {
            this.status = status;
            this.body = body;
            this.success = success;
        }

        static CallResult success(int status, String body) {
            return new CallResult(status, body, true);
        }

        static CallResult failure(int status, String body) {
            return new CallResult(status, body, false);
        }

        boolean success() {
            return success;
        }

        String errorDetail() {
            if (!StringUtils.hasText(body)) {
                return "empty body";
            }
            try {
                JSONObject json = JSON.parseObject(body);
                if (json == null) {
                    return body.substring(0, Math.min(400, body.length()));
                }
                if (json.get("error") instanceof JSONObject error && StringUtils.hasText(error.getString("message"))) {
                    return error.getString("message");
                }
                if (StringUtils.hasText(json.getString("message"))) {
                    return json.getString("message");
                }
                if (StringUtils.hasText(json.getString("detail"))) {
                    return json.getString("detail");
                }
            } catch (Exception ignored) {
                // fall through
            }
            return body.substring(0, Math.min(400, body.length()));
        }
    }
}
