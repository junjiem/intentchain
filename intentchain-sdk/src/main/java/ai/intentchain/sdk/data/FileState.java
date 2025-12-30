package ai.intentchain.sdk.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileState {
    /**
     * 文件相对路径
     */
    private String relativePath;

    /**
     * 文件最后修改时间（毫秒时间戳）
     */
    private long lastModified;

    /**
     * 文件的MD5哈希值
     */
    private String md5Hash;

    /**
     * 向量存储ID列表
     */
    @JsonProperty("vectorIds")
    private Map<String, List<String>> vectorIds = Map.of();
}