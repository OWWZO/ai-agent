package org.wwz.ai.test.spring.ai;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConstants;
import org.wwz.ai.domain.agent.reactor.data.dto.VectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.VectorSaveReq;
import org.wwz.ai.domain.agent.reactor.service.VectorService;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

//测试全部通过
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class OpenAiTest {

    @Value("classpath:data/dog.png")
    private Resource imageResource;

    @Value("classpath:data/file.txt")
    private Resource textResource;

    @Value("classpath:data/article-prompt-words.txt")
    private Resource articlePromptWordsResource;

    @Value("classpath:data/grafana-mcp-tools-guide.md")
    private Resource grafanaMcpToolsGuideResource;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Autowired
    private VectorService vectorService;

    private final TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();

    @Test
    public void test_call() {
        ChatResponse response = openAiChatModel.call(new Prompt(
                "1+1",
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));
        log.info("测试结果(call):{}", JSON.toJSONString(response));
    }

    @Test
    public void test_call_images() {
        UserMessage userMessage = UserMessage.builder()
                .text("请描述这张图片的主要内容，并说明图中物品的可能用途。")
                .media(org.springframework.ai.content.Media.builder()
                        .mimeType(MimeType.valueOf(MimeTypeUtils.IMAGE_PNG_VALUE))
                        .data(imageResource)
                        .build())
                .build();

        ChatResponse response = openAiChatModel.call(new Prompt(
                userMessage,
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));

        log.info("测试结果(images):{}", JSON.toJSONString(response));
    }

    @Test
    public void test_stream() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Flux<ChatResponse> stream = openAiChatModel.stream(new Prompt(
                "1+1",
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));

        stream.subscribe(
                chatResponse -> {
                    AssistantMessage output = chatResponse.getResult().getOutput();
                    log.info("测试结果(stream): {}", JSON.toJSONString(output));
                },
                Throwable::printStackTrace,
                () -> {
                    countDownLatch.countDown();
                    log.info("测试结果(stream): done!");
                }
        );

        countDownLatch.await();
    }

    @Test
    public void upload() {
        // textResource、articlePromptWordsResource
        TikaDocumentReader reader = new TikaDocumentReader(grafanaMcpToolsGuideResource);

        List<Document> documents = reader.get();
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "grafana-mcp-tools-guide"));

        VectorSaveReq req = new VectorSaveReq();
        req.setCollectionName(DataAgentConstants.SCHEMA_COLLECTION_NAME);
        req.setDataList(documentSplitterList.stream().map(this::toVectorData).collect(Collectors.toList()));
        vectorService.saveVector(req);

        log.info("上传完成");
    }

    @Test
    public void chat() {
        String message = "王大瓜今年几岁";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        VectorRecallReq request = new VectorRecallReq();
        request.setCollectionName(DataAgentConstants.SCHEMA_COLLECTION_NAME);
        request.setQuery(message);
        request.setLimit(5);
        request.setKeywordFilterMap(Map.of("knowledge", "知识库名称-v4"));

        List<Document> documents = vectorService.vectorRecall(request).stream()
                .map(this::toDocument)
                .collect(Collectors.toList());

        String documentsCollectors = null == documents ? "" : documents.stream().map(Document::getText).collect(Collectors.joining());

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        ChatResponse chatResponse = openAiChatModel.call(new Prompt(
                messages,
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));

        log.info("测试结果:{}", JSON.toJSONString(chatResponse));
    }

    private VectorSaveReq.VectorData toVectorData(Document document) {
        VectorSaveReq.VectorData vectorData = new VectorSaveReq.VectorData();
        vectorData.setEmbeddingText(document.getText());
        vectorData.setPayloads(document.getMetadata());
        return vectorData;
    }

    private Document toDocument(Map<String, Object> payload) {
        Object content = payload.get("content");
        return new Document(content == null ? "" : content.toString(), payload);
    }

}
