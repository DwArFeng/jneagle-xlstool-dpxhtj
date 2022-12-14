package com.jneagle.xlstool.dpxhtj.handler;

import com.dwarfeng.subgrade.stack.exception.HandlerException;
import com.jneagle.xlstool.dpxhtj.bean.dto.StatisticResult;
import com.jneagle.xlstool.dpxhtj.bean.dto.StatisticResult.DevicePerspective;
import com.jneagle.xlstool.dpxhtj.bean.dto.StatisticResult.PersonPerspective;
import com.jneagle.xlstool.dpxhtj.bean.dto.StatisticResult.ToolCutterPerspective;
import com.jneagle.xlstool.dpxhtj.bean.entity.ConsumingDetail;
import com.jneagle.xlstool.dpxhtj.service.ConsumingDetailMaintainService;
import com.jneagle.xlstool.dpxhtj.structure.ProgressStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class StatisticHandlerImpl extends AbstractProgressHandler implements StatisticHandler {

    private final ConsumingDetailMaintainService consumingDetailMaintainService;

    @Value("${statistics.regex.returning_usage.g01}")
    private String returningUsageG01Regex;

    @Value("${statistics.regex.returning_usage.g02}")
    private String returningUsageG02Regex;

    @Value("${statistics.regex.returning_usage.g03}")
    private String returningUsageG03Regex;

    public StatisticHandlerImpl(ConsumingDetailMaintainService consumingDetailMaintainService) {
        this.consumingDetailMaintainService = consumingDetailMaintainService;
    }

    @Override
    public StatisticResult execStatistic() throws HandlerException {
        try {
            // 广播进度变更事件。
            fireProgressChanged(ProgressStatus.UNCERTAIN);

            // 获取所有的消耗明细数据。
            List<ConsumingDetail> consumingDetails = consumingDetailMaintainService.lookupAsList();

            // 定义统计结果中间变量。
            Map<PersonPerspectiveKey, PersonPerspective> personPerspectiveMap = new LinkedHashMap<>();
            Map<DevicePerspectiveKey, DevicePerspective> devicePerspectiveMap = new LinkedHashMap<>();
            Map<ToolCutterPerspectiveKey, ToolCutterPerspective> toolCutterPerspectiveMap = new LinkedHashMap<>();

            // 设置总体进度。
            int progress = 0;
            fireProgressChanged(progress, consumingDetails.size());

            // 遍历所有消耗明细，处理单条数据。
            for (ConsumingDetail consumingDetail : consumingDetails) {
                execStatisticSingleData(consumingDetail, personPerspectiveMap, devicePerspectiveMap, toolCutterPerspectiveMap);
                fireProgressChanged(++progress, consumingDetails.size());
            }

            // 返回结果。
            return new StatisticResult(
                    new ArrayList<>(personPerspectiveMap.values()),
                    new ArrayList<>(devicePerspectiveMap.values()),
                    new ArrayList<>(toolCutterPerspectiveMap.values())
            );
        } catch (Exception e) {
            throw new HandlerException(e);
        } finally {
            // 广播进度变更事件。
            fireProgressChanged(ProgressStatus.IDLE);
        }
    }

    private void execStatisticSingleData(
            ConsumingDetail consumingDetail,
            Map<PersonPerspectiveKey, PersonPerspective> personPerspectiveMap,
            Map<DevicePerspectiveKey, DevicePerspective> devicePerspectiveMap,
            Map<ToolCutterPerspectiveKey, ToolCutterPerspective> toolCutterPerspectiveMap
    ) {
        // 获取关键参数，构造不同 Perspective 的主键。
        Date consumingDate = consumingDetail.getConsumingDate();
        Calendar consumingCalendar = Optional.ofNullable(consumingDate).map((date) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return calendar;
        }).orElse(null);
        Integer year = Optional.ofNullable(consumingCalendar).map(calendar -> calendar.get(Calendar.YEAR))
                .orElse(null);
        Integer month = Optional.ofNullable(consumingCalendar).map(calendar -> calendar.get(Calendar.MONTH))
                .orElse(null);
        String name = consumingDetail.getConsumingPerson();
        String toolCutterType = consumingDetail.getToolCutterType();
        String device = consumingDetail.getDevice();
        Integer consumingQuantity = Optional.ofNullable(consumingDetail.getConsumingQuantity()).orElse(0);
        BigDecimal worth = Optional.ofNullable(consumingDetail.getWorth()).orElse(BigDecimal.ZERO);
        String toolCutterCode = consumingDetail.getToolCutterCode();
        Integer returningQuantity = Optional.ofNullable(consumingDetail.getReturningQuantity()).orElse(0);
        PersonPerspectiveKey personPerspectiveKey = new PersonPerspectiveKey(
                year, month, name, toolCutterType, device, toolCutterCode
        );
        DevicePerspectiveKey devicePerspectiveKey = new DevicePerspectiveKey(
                year, month, toolCutterType, device, toolCutterCode
        );
        ToolCutterPerspectiveKey toolCutterPerspectiveKey = new ToolCutterPerspectiveKey(
                year, month, toolCutterType, toolCutterCode
        );

        // 处理退回使用等级。
        Integer returningUsageG01Quantity = 0;
        Integer returningUsageG02Quantity = 0;
        Integer returningUsageG03Quantity = 0;

        String returningUsageInfo = consumingDetail.getReturningUsageInfo();
        if (Objects.nonNull(returningUsageInfo)) {
            if (returningUsageInfo.matches(returningUsageG01Regex)) {
                returningUsageG01Quantity = returningQuantity;
            } else if (returningUsageInfo.matches(returningUsageG02Regex)) {
                returningUsageG02Quantity = returningQuantity;
            } else if (returningUsageInfo.matches(returningUsageG03Regex)) {
                returningUsageG03Quantity = returningQuantity;
            }
        }

        // 处理 PersonPerspective。
        PersonPerspective oldPersonPerspective = personPerspectiveMap.getOrDefault(
                personPerspectiveKey, initPersonPerspective(personPerspectiveKey)
        );
        PersonPerspective neoPersonPerspective = new PersonPerspective(
                oldPersonPerspective.getMonth(), oldPersonPerspective.getName(),
                oldPersonPerspective.getToolCutterType(),
                oldPersonPerspective.getConsumingQuantity() + consumingQuantity,
                oldPersonPerspective.getWorth().add(worth),
                oldPersonPerspective.getDevice(), oldPersonPerspective.getYear(),
                oldPersonPerspective.getToolCutterCode(),
                oldPersonPerspective.getReturningQuantity() + returningQuantity,
                oldPersonPerspective.getReturningUsageG01Quantity() + returningUsageG01Quantity,
                oldPersonPerspective.getReturningUsageG02Quantity() + returningUsageG02Quantity,
                oldPersonPerspective.getReturningUsageG03Quantity() + returningUsageG03Quantity
        );
        personPerspectiveMap.put(personPerspectiveKey, neoPersonPerspective);

        // 处理 DevicePerspective。
        DevicePerspective oldDevicePerspective = devicePerspectiveMap.getOrDefault(
                devicePerspectiveKey, initDevicePerspective(devicePerspectiveKey)
        );
        DevicePerspective neoDevicePerspective = new DevicePerspective(
                oldDevicePerspective.getMonth(), oldDevicePerspective.getDevice(),
                oldDevicePerspective.getToolCutterType(),
                oldDevicePerspective.getConsumingQuantity() + consumingQuantity,
                oldDevicePerspective.getWorth().add(worth),
                oldDevicePerspective.getYear(),
                oldDevicePerspective.getToolCutterCode()
        );
        devicePerspectiveMap.put(devicePerspectiveKey, neoDevicePerspective);

        // 处理 ToolCutterPerspective。
        ToolCutterPerspective oldToolCutterPerspective = toolCutterPerspectiveMap.getOrDefault(
                toolCutterPerspectiveKey, initToolCutterPerspective(toolCutterPerspectiveKey)
        );
        ToolCutterPerspective neoToolCutterPerspective = new ToolCutterPerspective(
                oldToolCutterPerspective.getMonth(),
                oldToolCutterPerspective.getToolCutterType(),
                oldToolCutterPerspective.getConsumingQuantity() + consumingQuantity,
                oldToolCutterPerspective.getWorth().add(worth),
                oldToolCutterPerspective.getYear(),
                oldToolCutterPerspective.getToolCutterCode()
        );
        toolCutterPerspectiveMap.put(toolCutterPerspectiveKey, neoToolCutterPerspective);
    }

    private PersonPerspective initPersonPerspective(PersonPerspectiveKey key) {
        return new PersonPerspective(
                key.getMonth(), key.getName(), key.getToolCutterType(), 0, BigDecimal.ZERO, key.getDevice(),
                key.getYear(), key.getToolCutterCode(), 0, 0, 0, 0
        );
    }

    private DevicePerspective initDevicePerspective(DevicePerspectiveKey key) {
        return new DevicePerspective(
                key.getMonth(), key.getDevice(), key.getToolCutterType(), 0, BigDecimal.ZERO, key.getYear(),
                key.getToolCutterCode()
        );
    }

    private ToolCutterPerspective initToolCutterPerspective(ToolCutterPerspectiveKey key) {
        return new ToolCutterPerspective(
                key.getMonth(), key.getToolCutterType(), 0, BigDecimal.ZERO, key.getYear(), key.getToolCutterCode()
        );
    }

    private static class PersonPerspectiveKey {

        private final Integer year;
        private final Integer month;
        private final String name;
        private final String toolCutterType;
        private final String device;
        private final String toolCutterCode;

        public PersonPerspectiveKey(
                Integer year, Integer month, String name, String toolCutterType, String device, String toolCutterCode
        ) {
            this.year = year;
            this.month = month;
            this.name = name;
            this.toolCutterType = toolCutterType;
            this.device = device;
            this.toolCutterCode = toolCutterCode;
        }

        public Integer getYear() {
            return year;
        }

        public Integer getMonth() {
            return month;
        }

        public String getName() {
            return name;
        }

        public String getToolCutterType() {
            return toolCutterType;
        }

        public String getDevice() {
            return device;
        }

        public String getToolCutterCode() {
            return toolCutterCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PersonPerspectiveKey that = (PersonPerspectiveKey) o;

            if (!Objects.equals(year, that.year)) return false;
            if (!Objects.equals(month, that.month)) return false;
            if (!Objects.equals(name, that.name)) return false;
            if (!Objects.equals(toolCutterType, that.toolCutterType))
                return false;
            if (!Objects.equals(device, that.device)) return false;
            return Objects.equals(toolCutterCode, that.toolCutterCode);
        }

        @Override
        public int hashCode() {
            int result = year != null ? year.hashCode() : 0;
            result = 31 * result + (month != null ? month.hashCode() : 0);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (toolCutterType != null ? toolCutterType.hashCode() : 0);
            result = 31 * result + (device != null ? device.hashCode() : 0);
            result = 31 * result + (toolCutterCode != null ? toolCutterCode.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "PersonPerspectiveKey{" +
                    "year=" + year +
                    ", month=" + month +
                    ", name='" + name + '\'' +
                    ", toolCutterType='" + toolCutterType + '\'' +
                    ", device='" + device + '\'' +
                    ", toolCutterCode='" + toolCutterCode + '\'' +
                    '}';
        }
    }

    private static class DevicePerspectiveKey {

        private final Integer year;
        private final Integer month;
        private final String toolCutterType;
        private final String device;
        private final String toolCutterCode;

        public DevicePerspectiveKey(
                Integer year, Integer month, String toolCutterType, String device, String toolCutterCode
        ) {
            this.year = year;
            this.month = month;
            this.toolCutterType = toolCutterType;
            this.device = device;
            this.toolCutterCode = toolCutterCode;
        }

        public Integer getYear() {
            return year;
        }

        public Integer getMonth() {
            return month;
        }

        public String getToolCutterType() {
            return toolCutterType;
        }

        public String getDevice() {
            return device;
        }

        public String getToolCutterCode() {
            return toolCutterCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DevicePerspectiveKey that = (DevicePerspectiveKey) o;

            if (!Objects.equals(year, that.year)) return false;
            if (!Objects.equals(month, that.month)) return false;
            if (!Objects.equals(toolCutterType, that.toolCutterType))
                return false;
            if (!Objects.equals(device, that.device)) return false;
            return Objects.equals(toolCutterCode, that.toolCutterCode);
        }

        @Override
        public int hashCode() {
            int result = year != null ? year.hashCode() : 0;
            result = 31 * result + (month != null ? month.hashCode() : 0);
            result = 31 * result + (toolCutterType != null ? toolCutterType.hashCode() : 0);
            result = 31 * result + (device != null ? device.hashCode() : 0);
            result = 31 * result + (toolCutterCode != null ? toolCutterCode.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "DevicePerspectiveKey{" +
                    "year=" + year +
                    ", month=" + month +
                    ", toolCutterType='" + toolCutterType + '\'' +
                    ", device='" + device + '\'' +
                    ", toolCutterCode='" + toolCutterCode + '\'' +
                    '}';
        }
    }

    private static class ToolCutterPerspectiveKey {

        private final Integer year;
        private final Integer month;
        private final String toolCutterType;
        private final String toolCutterCode;

        public ToolCutterPerspectiveKey(Integer year, Integer month, String toolCutterType, String toolCutterCode) {
            this.year = year;
            this.month = month;
            this.toolCutterType = toolCutterType;
            this.toolCutterCode = toolCutterCode;
        }

        public Integer getYear() {
            return year;
        }

        public Integer getMonth() {
            return month;
        }

        public String getToolCutterType() {
            return toolCutterType;
        }

        public String getToolCutterCode() {
            return toolCutterCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ToolCutterPerspectiveKey that = (ToolCutterPerspectiveKey) o;

            if (!Objects.equals(year, that.year)) return false;
            if (!Objects.equals(month, that.month)) return false;
            if (!Objects.equals(toolCutterType, that.toolCutterType))
                return false;
            return Objects.equals(toolCutterCode, that.toolCutterCode);
        }

        @Override
        public int hashCode() {
            int result = year != null ? year.hashCode() : 0;
            result = 31 * result + (month != null ? month.hashCode() : 0);
            result = 31 * result + (toolCutterType != null ? toolCutterType.hashCode() : 0);
            result = 31 * result + (toolCutterCode != null ? toolCutterCode.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "ToolCutterPerspectiveKey{" +
                    "year=" + year +
                    ", month=" + month +
                    ", toolCutterType='" + toolCutterType + '\'' +
                    ", toolCutterCode='" + toolCutterCode + '\'' +
                    '}';
        }
    }
}
