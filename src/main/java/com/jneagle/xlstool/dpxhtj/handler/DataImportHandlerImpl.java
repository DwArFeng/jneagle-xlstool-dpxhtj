package com.jneagle.xlstool.dpxhtj.handler;

import com.dwarfeng.subgrade.stack.exception.HandlerException;
import com.jneagle.xlstool.dpxhtj.bean.entity.ConsumingDetail;
import com.jneagle.xlstool.dpxhtj.bean.entity.ImportErrorInfo;
import com.jneagle.xlstool.dpxhtj.exception.WrongPasswordException;
import com.jneagle.xlstool.dpxhtj.service.ConsumingDetailMaintainService;
import com.jneagle.xlstool.dpxhtj.service.ImportErrorInfoMaintainService;
import com.jneagle.xlstool.dpxhtj.structure.ProgressStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;

@Component
public class DataImportHandlerImpl extends AbstractProgressHandler implements DataImportHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataImportHandlerImpl.class);

    private final ConsumingDetailMaintainService consumingDetailMaintainService;
    private final ImportErrorInfoMaintainService importErrorInfoMaintainService;

    @Value("${data_import.valid_sheet_name_regex}")
    private String validSheetNameRegex;
    @Value("${data_import.data_sheet.first_data_row}")
    private int firstDataRow;

    @Value("${data_import.data_sheet.column_index.tool_cutter_type}")
    private int toolCutterTypeColumnIndex;
    @Value("${data_import.data_sheet.column_index.device}")
    private int deviceColumnIndex;
    @Value("${data_import.data_sheet.column_index.consuming_quantity}")
    private int consumingQuantityColumnIndex;
    @Value("${data_import.data_sheet.column_index.worth}")
    private int worthColumnIndex;
    @Value("${data_import.data_sheet.column_index.consuming_person}")
    private int consumingPersonColumnIndex;
    @Value("${data_import.data_sheet.column_index.consuming_date}")
    private int consumingDateColumnIndex;
    @Value("${data_import.data_sheet.column_index.remark}")
    private int remarkColumnIndex;
    @Value("${data_import.data_sheet.column_index.returning_quantity}")
    private int returningQuantityColumnIndex;
    @Value("${data_import.data_sheet.column_index.returning_usage_info}")
    private int returningUsageInfoColumnIndex;

    public DataImportHandlerImpl(
            ConsumingDetailMaintainService consumingDetailMaintainService,
            ImportErrorInfoMaintainService importErrorInfoMaintainService
    ) {
        this.consumingDetailMaintainService = consumingDetailMaintainService;
        this.importErrorInfoMaintainService = importErrorInfoMaintainService;
    }

    @Override
    public void execImport(File file, String password) throws HandlerException {
        try {
            // ???????????????????????????
            fireProgressChanged(ProgressStatus.UNCERTAIN);

            // ?????????????????????
            Workbook workbook = parseWorkbook(file, password);

            // ??????????????????????????????????????????????????????????????????
            Iterator<Sheet> sheetIterator = workbook.sheetIterator();
            List<Sheet> sheets = new ArrayList<>();
            while (sheetIterator.hasNext()) {
                Sheet sheet = sheetIterator.next();
                if (sheet.getSheetName().matches(validSheetNameRegex)) {
                    sheets.add(sheet);
                }
            }

            // ????????????????????????????????????
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // ?????????????????????
            List<ConsumingDetail> consumingDetails = new ArrayList<>();
            List<ImportErrorInfo> importErrorInfos = new ArrayList<>();

            // ?????????????????????
            int progress = 0;
            fireProgressChanged(progress, sheets.size());

            // ??????????????????????????????????????????????????????
            for (Sheet sheet : sheets) {
                execImportSingleSheet(evaluator, sheet, consumingDetails, importErrorInfos);
                fireProgressChanged(++progress, sheets.size());
            }

            // ????????????????????????????????????????????????
            consumingDetailMaintainService.batchInsert(consumingDetails);
            importErrorInfoMaintainService.batchInsert(importErrorInfos);
        } catch (org.apache.poi.EncryptedDocumentException e) {
            throw new WrongPasswordException(e);
        } catch (HandlerException e) {
            throw e;
        } catch (Exception e) {
            throw new HandlerException(e);
        } finally {
            // ???????????????????????????
            fireProgressChanged(ProgressStatus.IDLE);
        }
    }

    private Workbook parseWorkbook(File file, String password) throws Exception {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            if (StringUtils.isNotEmpty(password)) {
                POIFSFileSystem pfs = new POIFSFileSystem(in);
                EncryptionInfo encInfo = new EncryptionInfo(pfs);
                Decryptor decryptor = Decryptor.getInstance(encInfo);
                decryptor.verifyPassword(password);
                return WorkbookFactory.create(decryptor.getDataStream(pfs));
            } else {
                return WorkbookFactory.create(in);
            }
        }
    }

    private void execImportSingleSheet(
            FormulaEvaluator evaluator, Sheet sheet, List<ConsumingDetail> consumingDetails,
            List<ImportErrorInfo> importErrorInfos
    ) {
        // ????????????????????????
        String sheetName = sheet.getSheetName();

        // ?????????????????????????????????????????????
        int currentRowIndex = firstDataRow;
        int totalRowIndex = sheet.getLastRowNum();

        // ??????????????????????????????????????????????????????
        for (; currentRowIndex <= totalRowIndex; currentRowIndex++) {
            loadRow(
                    evaluator, sheetName, currentRowIndex, sheet.getRow(currentRowIndex), consumingDetails,
                    importErrorInfos
            );
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private void loadRow(
            FormulaEvaluator evaluator, String sheetName, int rowIndex, Row row, List<ConsumingDetail> consumingDetails,
            List<ImportErrorInfo> importErrorInfos
    ) {
        try {
            // ???????????????
            CellValue cellValue;

            // ???????????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, toolCutterTypeColumnIndex));
            String toolCutterType = Optional.ofNullable(cellValue).map(CellValue::getStringValue).orElse(null);

            // ?????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, deviceColumnIndex));
            String device = Optional.ofNullable(cellValue).map(CellValue::getStringValue).orElse(null);

            // ???????????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, consumingQuantityColumnIndex));
            Integer consumingQuantity = Optional.ofNullable(cellValue).map(v -> (int) v.getNumberValue()).orElse(null);

            // ?????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, worthColumnIndex));
            BigDecimal worth = Optional.ofNullable(cellValue).map(v -> BigDecimal.valueOf(v.getNumberValue()))
                    .orElse(null);

            // ????????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, consumingPersonColumnIndex));
            String consumingPerson = Optional.ofNullable(cellValue).map(CellValue::getStringValue).orElse(null);

            // ???????????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, consumingDateColumnIndex));
            Date consumingDate = Optional.ofNullable(cellValue).map(v -> DateUtil.getJavaDate(v.getNumberValue()))
                    .orElse(null);

            // ?????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, remarkColumnIndex));
            String remark = Optional.ofNullable(cellValue).map(CellValue::getStringValue).orElse(null);

            // ???????????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, returningQuantityColumnIndex));
            Integer returningQuantity = Optional.ofNullable(cellValue).map(v -> (int) v.getNumberValue()).orElse(null);

            // ?????????????????????
            cellValue = evaluator.evaluate(CellUtil.getCell(row, returningUsageInfoColumnIndex));
            String returningUsageInfo = Optional.ofNullable(cellValue).map(CellValue::getStringValue).orElse(null);

            consumingDetails.add(new ConsumingDetail(
                    null, toolCutterType, device, consumingQuantity, worth, consumingPerson, consumingDate, remark,
                    sheetName, returningQuantity, returningUsageInfo
            ));
        } catch (Exception e) {
            String warnMessage = "????????????????????? " + rowIndex + " ???(????????????????????? " +
                    (rowIndex + 1) + " ???)???????????????????????????????????????: ";
            LOGGER.warn(warnMessage, e);
            ImportErrorInfo importErrorInfo = new ImportErrorInfo(
                    null, sheetName, rowIndex, "???????????????????????????????????????"
            );
            importErrorInfos.add(importErrorInfo);
        }
    }
}
