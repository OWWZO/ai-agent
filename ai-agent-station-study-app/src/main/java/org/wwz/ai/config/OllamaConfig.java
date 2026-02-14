//package org.wwz.ai.config;
//
//import org.springframework.ai.ollama.api.OllamaApi;
//import org.springframework.ai.openai.api.OpenAiApi;
//import org.springframework.ai.transformer.splitter.TokenTextSplitter;
//import org.springframework.ai.vectorstore.SimpleVectorStore;
//import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.jdbc.core.JdbcTemplate;
//
//@Configuration
//public class OllamaConfig {
//
//    @Bean
//    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
//        return new OllamaApi(baseUrl);
//    }
//
//    @Bean
//    public OpenAiApi openAiApi(@Value("${spring.ai.openai.base-url}") String baseUrl, @Value("${spring.ai.openai.api-key}") String apikey) {
//        return new OpenAiApi(baseUrl, apikey);
//    }
//
//    @Bean
//    public OllamaChatClient ollamaChatClient(OllamaApi ollamaApi) {
//        return new OllamaChatClient(ollamaApi);
//    }
//
//    @Bean
//    public TokenTextSplitter tokenTextSplitter() {
//        return new TokenTextSplitter();
//    }
//
//    @Bean
//    public SimpleVectorStore vectorStore(@Value("${spring.ai.rag.embed}") String model, OllamaApi ollamaApi, OpenAiApi openAiApi) {
//        if ("nomic-embed-text".equalsIgnoreCase(model)) {
//            OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
//            embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
//            return new SimpleVectorStore(embeddingClient);
//        } else {
//            OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(openAiApi);
//            return new SimpleVectorStore(embeddingClient);
//        }
//    }
//
//    @Bean
//    public PgVectorStore pgVectorStore(@Value("${spring.ai.rag.embed}") String model, OllamaApi ollamaApi, OpenAiApi openAiApi, JdbcTemplate jdbcTemplate) {
//        if ("nomic-embed-text".equalsIgnoreCase(model)) {
//            OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
//            embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
//            return new PgVectorStore(jdbcTemplate, embeddingClient);
//        } else {
//            OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(openAiApi);
//            return new PgVectorStore(jdbcTemplate, embeddingClient);
//        }
//    }
//
//
//}