package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** 地磅实重在件货明细上的确定性分摊。 */
public final class LpnWeightAllocation {
    private LpnWeightAllocation() {}

    /** 已核验的唯一明细 UUID、LPN 和正理重（千克）。 */
    public record Line(UUID id, String lpn, BigDecimal theoreticalKg) {}

    /** 单行按地磅实重分摊的四位小数净重（千克）。 */
    public record Allocation(UUID id, BigDecimal netKg) {}

    /**
     * 按明细 UUID 的无符号字节顺序分摊，与数据库 BINARY(16) 排序一致，末行承接尾差。
     * 同一输入多次执行得出相同结果，任何行分得零重量均拒绝过账。
     *
     * @param netKg 正地磅总净重，千克，最多四位小数
     * @param lines 已核验、LPN 和 ID 均唯一的非空明细集合
     * @return 按 UUID 排序的不可变分摊结果，汇总严格等于地磅总净重
     * @throws IllegalArgumentException 有效定量或明细不满足分摊要求
     */
    public static List<Allocation> allocate(BigDecimal netKg, List<Line> lines) {
        if (netKg == null || netKg.signum() <= 0 || netKg.stripTrailingZeros().scale() > 4 || lines.isEmpty()) {
            throw new IllegalArgumentException("地磅净重或 LPN 明细无效");
        }
        var ids = new HashSet<UUID>();
        var lpns = new HashSet<String>();
        BigDecimal totalTheoretical = BigDecimal.ZERO;
        for (var line : lines) {
            if (line.id() == null || !ids.add(line.id()) || line.lpn() == null
                    || line.lpn().isBlank() || !lpns.add(line.lpn())
                    || line.theoreticalKg() == null || line.theoreticalKg().signum() <= 0
                    || line.theoreticalKg().stripTrailingZeros().scale() > 4) {
                throw new IllegalArgumentException("明细标识、LPN 或理重无效");
            }
            totalTheoretical = totalTheoretical.add(line.theoreticalKg());
        }

        var sorted = lines.stream().sorted(Comparator.comparing(line->line.id().toString())).toList();
        var result = new ArrayList<Allocation>(sorted.size());
        BigDecimal remaining = netKg.setScale(4);
        for (int i = 0; i < sorted.size(); i++) {
            var line = sorted.get(i);
            BigDecimal allocated = i == sorted.size() - 1
                    ? remaining
                    : netKg.multiply(line.theoreticalKg()).divide(totalTheoretical, 4, RoundingMode.DOWN);
            if (allocated.signum() <= 0) {
                throw new IllegalArgumentException("分摊后 LPN 重量不足最小计量精度");
            }
            result.add(new Allocation(line.id(), allocated));
            remaining = remaining.subtract(allocated);
        }
        return List.copyOf(result);
    }
}
