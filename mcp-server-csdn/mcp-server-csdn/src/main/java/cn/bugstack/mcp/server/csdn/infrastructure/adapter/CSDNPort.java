package cn.bugstack.mcp.server.csdn.infrastructure.adapter;

import cn.bugstack.mcp.server.csdn.domain.adapter.ICSDNPort;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.ICSDNService;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNApiProperties;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

/**
 * 端口类 聚合有发送能力的csdnService和账户配置csdnApiProperties
 * 发帖封装成CSDNPort的writeArticle函数
 */
@Slf4j
@Component
public class CSDNPort implements ICSDNPort {

    @Resource
    private ICSDNService csdnService;

    @Resource
    private CSDNApiProperties csdnApiProperties;

    @Override
    public ArticleFunctionResponse writeArticle(ArticleFunctionRequest request) throws IOException {

        ArticleRequestDTO articleRequestDTO = new ArticleRequestDTO();
        articleRequestDTO.setTitle(request.getTitle());
        articleRequestDTO.setMarkdowncontent(request.getMarkdowncontent());
        articleRequestDTO.setContent(request.getContent());
        articleRequestDTO.setTags(request.getTags());
        articleRequestDTO.setDescription(request.getDescription());
        articleRequestDTO.setCategories(csdnApiProperties.getCategories());

        Call<ArticleResponseDTO> call = csdnService.saveArticle(articleRequestDTO, csdnApiProperties.getCookie());

        Response<ArticleResponseDTO> response = call.execute();

        log.info("请求CSDN发帖 \nreq:{} \nres:{}", JSON.toJSONString(articleRequestDTO), JSON.toJSONString(response));

        /**
         * 处理文章响应数据，将接口返回的ArticleResponseDTO转换为对外输出的ArticleFunctionResponse
         * 仅在HTTP响应成功（状态码200系列）时执行此逻辑
         */
// 检查HTTP响应是否成功（状态码在200-299之间）
        if (response.isSuccessful()) {
            // 1. 从响应体中获取接口返回的文章响应DTO对象（核心数据载体）
            ArticleResponseDTO articleResponseDTO = response.body();

            // 2. 空值校验：如果响应体解析后的DTO为空，直接返回null避免空指针
            if (null == articleResponseDTO) {
                return null;
            }

            // 3. 从DTO中提取文章核心数据对象（封装了文章的URL、ID、标题等具体信息）
            ArticleResponseDTO.ArticleData articleData = articleResponseDTO.getData();

            // 4. 构建对外输出的文章功能响应对象（统一返回格式）
            ArticleFunctionResponse articleFunctionResponse = new ArticleFunctionResponse();

            // 4.1 赋值响应状态码（保持与原接口返回的状态码一致）
            articleFunctionResponse.setCode(articleResponseDTO.getCode());
            // 4.2 赋值响应提示信息（保持与原接口返回的提示信息一致）
            articleFunctionResponse.setMsg(articleResponseDTO.getMsg());

            // 4.3 构建并赋值文章数据对象（使用建造者模式，仅复制需要的核心字段）
            articleFunctionResponse.setArticleData(
                    ArticleFunctionResponse.ArticleData.builder()
                            // 文章访问URL
                            .url(articleData.getUrl())
                            // 文章唯一标识ID
                            .id(articleData.getId())
                            // 文章二维码地址
                            .qrcode(articleData.getQrcode())
                            // 文章标题
                            .title(articleData.getTitle())
                            // 文章描述/摘要
                            .description(articleData.getDescription())
                            // 建造者模式构建对象
                            .build()
            );

            // 5. 返回封装后的响应对象
            return articleFunctionResponse;
        }

        return null;
    }

}
