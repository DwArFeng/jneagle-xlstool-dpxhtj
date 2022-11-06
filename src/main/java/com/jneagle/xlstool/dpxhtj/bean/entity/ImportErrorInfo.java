package com.jneagle.xlstool.dpxhtj.bean.entity;

import com.dwarfeng.subgrade.stack.bean.entity.Entity;
import com.dwarfeng.subgrade.stack.bean.key.UuidKey;

/**
 * 导入错误信息。
 *
 * @author DwArFeng
 * @since 1.0.0
 */
public class ImportErrorInfo implements Entity<UuidKey> {

    private static final long serialVersionUID = 7112418416083577797L;

    private UuidKey key;
    private Integer sheetIndex;
    private Integer rowIndex;
    private String errorMessage;

    public ImportErrorInfo() {
    }

    public ImportErrorInfo(UuidKey key, Integer sheetIndex, Integer rowIndex, String errorMessage) {
        this.key = key;
        this.sheetIndex = sheetIndex;
        this.rowIndex = rowIndex;
        this.errorMessage = errorMessage;
    }

    @Override
    public UuidKey getKey() {
        return key;
    }

    @Override
    public void setKey(UuidKey key) {
        this.key = key;
    }

    public Integer getSheetIndex() {
        return sheetIndex;
    }

    public void setSheetIndex(Integer sheetIndex) {
        this.sheetIndex = sheetIndex;
    }

    public Integer getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(Integer rowIndex) {
        this.rowIndex = rowIndex;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "ImportErrorInfo{" +
                "key=" + key +
                ", sheetIndex=" + sheetIndex +
                ", rowIndex=" + rowIndex +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
