package ai.intentchain.storer.tidb;

import lombok.Getter;

/**
 * TiDB 向量距离度量方式
 * 
 * @see <a href="https://docs.pingcap.com/tidb/stable/vector-search-functions">TiDB Vector Search Functions</a>
 */
@Getter
public enum DistanceMetric {
    
    /**
     * 余弦距离 (Cosine Distance)
     * <p>
     * 范围: [0, 2]<br>
     * 适用场景: 大多数文本和图像相似度场景<br>
     * 向量索引: ✅ 支持
     */
    COSINE("cosine", "VEC_COSINE_DISTANCE", true),
    
    /**
     * 欧氏距离 (L2 Distance / Euclidean Distance)
     * <p>
     * 范围: [0, +∞)<br>
     * 适用场景: 需要精确距离度量的场景<br>
     * 向量索引: ✅ 支持
     */
    L2("l2", "VEC_L2_DISTANCE", true),
    
    /**
     * 曼哈顿距离 (L1 Distance / Manhattan Distance)
     * <p>
     * 范围: [0, +∞)<br>
     * 适用场景: 计算速度要求高的场景<br>
     * 向量索引: ❌ 不支持
     */
    L1("l1", "VEC_L1_DISTANCE", false),
    
    /**
     * 负内积 (Negative Inner Product)
     * <p>
     * 范围: (-∞, +∞)<br>
     * 适用场景: 已归一化的向量<br>
     * 向量索引: ❌ 不支持
     */
    INNER_PRODUCT("inner_product", "VEC_NEGATIVE_INNER_PRODUCT", false);

    /**
     * -- GETTER --
     *  获取距离度量的字符串值
     */
    private final String value;
    /**
     * -- GETTER --
     *  获取对应的 TiDB SQL 函数名
     */
    private final String sqlFunction;
    /**
     * -- GETTER --
     *  是否支持向量索引
     */
    private final boolean supportsVectorIndex;
    
    DistanceMetric(String value, String sqlFunction, boolean supportsVectorIndex) {
        this.value = value;
        this.sqlFunction = sqlFunction;
        this.supportsVectorIndex = supportsVectorIndex;
    }
    
    /**
     * 是否支持向量索引
     * 
     * @return true 如果支持向量索引，false 否则
     * @see <a href="https://docs.pingcap.com/zh/tidb/stable/vector-search-functions-and-operators">TiDB Vector Search Functions</a>
     */
    public boolean supportsVectorIndex() {
        return supportsVectorIndex;
    }

    /**
     * 从字符串值解析枚举
     * 
     * @param value 距离度量字符串值
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果值不合法
     */
    public static DistanceMetric fromValue(String value) {
        if (value == null) {
            return COSINE; // 默认值
        }
        
        for (DistanceMetric metric : values()) {
            if (metric.value.equalsIgnoreCase(value)) {
                return metric;
            }
        }
        
        throw new IllegalArgumentException(
            String.format("不支持的距离度量方式: %s. 支持的值: cosine, l2, l1, inner_product", value)
        );
    }
    
    /**
     * 将距离转换为相似度分数 (0-1 范围)
     * 
     * @param distance 原始距离值
     * @return 相似度分数，范围 [0, 1]，1 表示最相似
     */
    public double convertDistanceToScore(double distance) {
        return switch (this) {
            case COSINE -> 
                // 余弦距离范围 [0, 2]，转换为相似度 [1, 0]
                1.0 - (distance / 2.0);
            case INNER_PRODUCT -> 
                // 内积距离越小（负值越大）越相似
                1.0 / (1.0 + Math.abs(distance));
            case L1, L2 -> 
                // L1/L2 距离越小越相似
                1.0 / (1.0 + distance);
        };
    }
    
    @Override
    public String toString() {
        return value;
    }
}

