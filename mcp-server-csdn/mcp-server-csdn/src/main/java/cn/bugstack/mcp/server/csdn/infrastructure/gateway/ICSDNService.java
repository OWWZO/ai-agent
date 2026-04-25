package cn.bugstack.mcp.server.csdn.infrastructure.gateway;


import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ICSDNService {

    @Headers({
            "accept: */*",
            "accept-language: zh-CN,zh;q=0.9",
            "content-type: application/json",
            "dnt: 1",
            "origin: https://editor.csdn.net",
            "priority: u=1, i",
            "referer: https://editor.csdn.net/",
            "sec-ch-ua: \"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\", \"Google Chrome\";v=\"134\"",
            "sec-ch-ua-mobile: ?0",
            "sec-ch-ua-platform: \"macOS\"",
            "sec-fetch-dest: empty",
            "sec-fetch-mode: cors",
            "sec-fetch-site: same-site",
            "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "x-ca-key: 203803574",
            "x-ca-nonce: a70ca99e-8bfa-46d1-8d12-363c72707ebe",
            "x-ca-signature: NGLzlIyvH7BuQgGJrgfGOzao0SVpzdTs4aTcw3hio6Y=",
            "x-ca-signature-headers: x-ca-key,x-ca-nonce",
    })
    @POST("/blog-console-api/v3/mdeditor/saveArticle")
    Call<ArticleResponseDTO> saveArticle(@Body ArticleRequestDTO request, @Header("Cookie") String cookieValue);

}
