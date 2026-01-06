package ai.intentchain.storer.tidb;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * TiDB 元数据过滤器映射器
 * 
 * 将 langchain4j 的 Filter 转换为 TiDB 的 SQL WHERE 子句
 */
public class TiDBMetadataFilterMapper {

    /**
     * SQL 类型映射
     * TiDB 支持标准的 MySQL 数据类型
     */
    static final Map<Class<?>, String> SQL_TYPE_MAP = Stream.of(
                    new AbstractMap.SimpleEntry<>(Integer.class, "SIGNED"),
                    new AbstractMap.SimpleEntry<>(Long.class, "SIGNED"),
                    new AbstractMap.SimpleEntry<>(Float.class, "DECIMAL(10,5)"),
                    new AbstractMap.SimpleEntry<>(Double.class, "DECIMAL(20,10)"),
                    new AbstractMap.SimpleEntry<>(String.class, "CHAR"),
                    new AbstractMap.SimpleEntry<>(UUID.class, "CHAR"),
                    new AbstractMap.SimpleEntry<>(Boolean.class, "SIGNED"),
                    new AbstractMap.SimpleEntry<>(Object.class, "CHAR"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    /**
     * 将 Filter 映射为 SQL WHERE 子句
     */
    public String map(Filter filter) {
        if (filter instanceof IsEqualTo eq) {
            return mapEqual(eq);
        } else if (filter instanceof IsNotEqualTo neq) {
            return mapNotEqual(neq);
        } else if (filter instanceof IsGreaterThan gt) {
            return mapGreaterThan(gt);
        } else if (filter instanceof IsGreaterThanOrEqualTo gte) {
            return mapGreaterThanOrEqual(gte);
        } else if (filter instanceof IsLessThan lt) {
            return mapLessThan(lt);
        } else if (filter instanceof IsLessThanOrEqualTo lte) {
            return mapLessThanOrEqual(lte);
        } else if (filter instanceof IsIn in) {
            return mapIn(in);
        } else if (filter instanceof IsNotIn nin) {
            return mapNotIn(nin);
        } else if (filter instanceof And and) {
            return mapAnd(and);
        } else if (filter instanceof Not not) {
            return mapNot(not);
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else {
            throw new UnsupportedOperationException(
                    "不支持的 Filter 类型: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
        return format("%s is not null and %s = %s", key, key, formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key = formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
        return format("%s is null or %s != %s", key, key, formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return format(
                "%s > %s",
                formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return format(
                "%s >= %s",
                formatKey(
                        isGreaterThanOrEqualTo.key(),
                        isGreaterThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return format(
                "%s < %s",
                formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return format(
                "%s <= %s",
                formatKey(
                        isLessThanOrEqualTo.key(),
                        isLessThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private String mapIn(IsIn isIn) {
        return format("%s in %s", formatKeyAsString(isIn.key()), formatValuesAsString(isIn.comparisonValues()));
    }

    private String mapNotIn(IsNotIn isNotIn) {
        String key = formatKeyAsString(isNotIn.key());
        return format("%s is null or %s not in %s", key, key, formatValuesAsString(isNotIn.comparisonValues()));
    }

    private String mapAnd(And and) {
        return format("%s and %s", map(and.left()), map(and.right()));
    }

    private String mapNot(Not not) {
        return format("not(%s)", map(not.expression()));
    }

    private String mapOr(Or or) {
        return format("(%s or %s)", map(or.left()), map(or.right()));
    }

    /**
     * 格式化键（带类型转换）
     * TiDB 使用 ->> 操作符提取 JSON 字段并转换为文本，然后使用 CAST 进行类型转换
     * 
     * @param key JSON 键名
     * @param valueType 值的 Java 类型
     * @return 格式化后的 SQL 表达式
     */
    String formatKey(String key, Class<?> valueType) {
        String sqlType = SQL_TYPE_MAP.get(valueType);
        if (sqlType.equals("CHAR")) {
            // 对于字符串类型，直接使用 ->> 操作符
            return format("metadata->>'$.%s'", escapeJsonPath(key));
        } else {
            // 对于数值类型，使用 CAST 进行类型转换
            return format("CAST(metadata->>'$.%s' AS %s)", escapeJsonPath(key), sqlType);
        }
    }

    /**
     * 格式化键（作为字符串）
     * 用于 IN 和 NOT IN 操作
     */
    String formatKeyAsString(String key) {
        return format("metadata->>'$.%s'", escapeJsonPath(key));
    }

    /**
     * 格式化单个值
     */
    String formatValue(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return "'" + escapeSql(value.toString()) + "'";
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        } else {
            return value.toString();
        }
    }

    /**
     * 格式化多个值（用于 IN 和 NOT IN）
     */
    String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream()
                .map(v -> format("'%s'", escapeSql(v.toString())))
                .collect(Collectors.joining(",")) + ")";
    }

    /**
     * 转义 SQL 字符串，防止 SQL 注入
     */
    private String escapeSql(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("'", "''");
    }

    /**
     * 转义 JSON 路径，防止注入
     */
    private String escapeJsonPath(String path) {
        if (path == null) {
            return "";
        }
        // 只允许字母、数字、下划线和点
        if (!path.matches("^[a-zA-Z0-9_.]+$")) {
            throw new IllegalArgumentException("无效的 JSON 键名: " + path);
        }
        return path;
    }
}

