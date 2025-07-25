package com.supercode.service;

import com.supercode.entity.*;
import com.supercode.repository.*;
import com.supercode.request.GeneralRequest;
import com.supercode.response.BaseResponse;
import com.supercode.util.MessageConstant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.event.spi.SaveOrUpdateEvent;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@ApplicationScoped
public class GeneralService {

    @Inject
    HeaderPaymentRepository headerPaymentRepository;

    @Inject
    PaymentMethodRepository paymentMethodRepository;

    @Inject
    PosRepository posRepository;

    @Inject
    DetailPaymentAggregatorRepository detailPaymentAggregatorRepository;

    @Inject
    BankMutationRepository bankMutationRepository;

    @Inject
    LogReconRepository logReconRepository;

    @Inject
    MasterMerchantRepository masterMerchantRepository;

    @Inject
    BankMutationService bankMutationService;

    @Inject
    GeneralService generalService;




    public String saveHeaderPayment(MultipartFormDataInput file, String paymentWay, String pmId, String branchId, String transDate, String user) {
        String parentId = generateRandomCode();
        Map<String, List<InputPart>> formDataMap = file.getFormDataMap();
        List<InputPart> fileParts = formDataMap.get("file");
        HeaderPayment headerPayment = new HeaderPayment();

        if (paymentWay.equalsIgnoreCase(MessageConstant.POS)) {
            paymentWay = paymentMethodRepository.getPaymentIdByPaymentMethod(paymentWay);
            headerPayment.setPmId(paymentWay);
        } else {
            headerPayment.setPmId(pmId);
        }
        InputPart filePart = fileParts.get(0);
        headerPayment.setFileName(getFileName(filePart));
        headerPayment.setParentId(parentId);
        headerPayment.setBranchId(branchId);
        headerPayment.setTransDate(transDate);
        headerPayment.setCreatedBy(user);
        headerPaymentRepository.persist(headerPayment);
        return parentId;

    }

    private String getFileName(InputPart inputPart) {
        try {
            Map<String, List<String>> headers = inputPart.getHeaders();
            String contentDisposition = headers.get("Content-Disposition").get(0);

            for (String content : contentDisposition.split(";")) {
                if (content.trim().startsWith("filename")) {
                    return content.split("=")[1].trim().replaceAll("\"", "");
                }
            }
        } catch (Exception e) {
            return "unknown_file";
        }
        return "unknown_file";
    }

    public void saveDetailPayment(MultipartFormDataInput file, String paymentType, String parentId, String pmId, String branchId, String transDate, String user) {
        try {
            String paymentMethod = paymentMethodRepository.getPaymentMethodByPmId(pmId);
            if (paymentType.equalsIgnoreCase(MessageConstant.POS)) {
//                saveDetailPos(file, parentId);
                saveDetailDPos(file, parentId, branchId, transDate, user);
            }else if(paymentType.equalsIgnoreCase(MessageConstant.BANK)){
                if(paymentMethod.equalsIgnoreCase("BCA")){
                    bankMutationService.saveDetailBankBca(file, pmId, branchId, parentId, transDate, user);
                }else bankMutationService.saveDetailBank(file, pmId, branchId, parentId, transDate, user);

            }else if(paymentType.equalsIgnoreCase("ESB - QRIS")){
                saveDetailEsb(file, pmId, branchId, parentId, transDate, user);
            }
            else {

                if (paymentMethod.equalsIgnoreCase(MessageConstant.SHOPEEFOOD)) {
                    saveDetailShopeeFood(file, pmId, parentId, branchId, transDate, user);
                }
                else if(paymentMethod.equalsIgnoreCase(MessageConstant.GRABFOOD)){
                    saveDetailGrabFood(file, pmId, branchId, parentId, transDate, user);
                }else if(paymentMethod.equalsIgnoreCase(MessageConstant.GOFOOD)){
                    saveDetailGoFood(file, pmId, branchId, parentId, transDate, user);
                }
            }
        } catch (Exception e) {

        }
    }

    private void saveDetailEsb(MultipartFormDataInput file, String pmId, String branchId, String parentId, String transDate, String user) {
        try {
            Map<String, List<InputPart>> formDataMap = file.getFormDataMap();
            List<InputPart> inputParts = formDataMap.get("file");

            if (inputParts != null && !inputParts.isEmpty()) {
                InputPart inputPart = inputParts.get(0);
                try (InputStream inputStream = inputPart.getBody(InputStream.class, null)) {
                    processExcelESB(inputStream, pmId, parentId, transDate, user, branchId);
                }
            } else {
                System.out.println("No file part found in the request.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processExcelESB(InputStream inputStream, String pmId, String parentId, String transDate, String user, String branchId) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            Row disburseRow = sheet.getRow(1);
            String disburseDate = getCellValue(disburseRow.getCell(1), workbook);
            System.out.println("Disburse Date: " + disburseDate);
            for (Row row : sheet) {
                if (row.getRowNum() < 13) continue;
                if(getCellValue(row.getCell(0), workbook).isEmpty()){
                    break;
                }
                processRowEsb(row, pmId, parentId,disburseDate, user, branchId, workbook, transDate);
            }
            System.out.println("successfully save  data");
        }
    }


    private String getCellValue(Cell cell, Workbook workbook) {
        if (cell == null) return "";

        try {
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                try {
                    CellValue evaluated = evaluator.evaluate(cell);
                    switch (evaluated.getCellType()) {
                        case STRING:
                            return evaluated.getStringValue();
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                            }
                            return String.valueOf((long) evaluated.getNumberValue());
                        case BOOLEAN:
                            return String.valueOf(evaluated.getBooleanValue());
                        default:
                            return "";
                    }
                } catch (Exception e) {
                    // Jika formula referensi eksternal, gunakan nilai cache (jika ada)
                    switch (cell.getCachedFormulaResultType()) {
                        case STRING:
                            return cell.getStringCellValue();
                        case NUMERIC:
                            return String.valueOf((long) cell.getNumericCellValue());
                        case BOOLEAN:
                            return String.valueOf(cell.getBooleanCellValue());
                        default:
                            return "";
                    }
                }
            }

            // Bukan formula
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                    } else {
                        return String.valueOf((long) cell.getNumericCellValue());
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case BLANK:
                default:
                    return "";
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }





    private void processRowEsb(Row row, String pmId, String parentId, String disburseDate, String user, String branchId, Workbook workbook, String transDate) {
        try {
            if (row.getRowNum() == 0) return;

            Cell timeTransCell = row.getCell(1);
            if (!disburseDate.equalsIgnoreCase(transDate)) return;

            String formattedTime = getTime(timeTransCell);

            Cell grossAmountCell = row.getCell(9);  // Grand Total
            BigDecimal grossAmount = parseBigDecimalFromCell(grossAmountCell, workbook);

            Cell nettAmountCell = row.getCell(16);  // Disburse
            BigDecimal nettAmount = parseBigDecimalFromCell(nettAmountCell, workbook);

            String transId = getCellValue(row.getCell(5), workbook);

            DetailPaymentAggregator dpa = new DetailPaymentAggregator();
            dpa.setBranchId(branchId);
            dpa.setPmId(pmId);
            dpa.setTransDate(disburseDate);
            dpa.setTransId(transId);
            dpa.setTransTime(formattedTime);
            dpa.setGrossAmount(grossAmount);
            dpa.setNetAmount(nettAmount);
            dpa.setPaymentId(transId + pmId);
            dpa.setSettlementDate(disburseDate);
            dpa.setSettlementTime(formattedTime);
            dpa.setParentId(parentId);
            dpa.setCreatedBy(user);


            detailPaymentAggregatorRepository.persist(dpa);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BigDecimal parseBigDecimalFromCell(Cell cell, Workbook workbook) {
        if (cell == null) return BigDecimal.ZERO;

        try {
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                CellValue value = evaluator.evaluate(cell);
                if (value.getCellType() == CellType.NUMERIC) {
                    return BigDecimal.valueOf(value.getNumberValue());
                } else if (value.getCellType() == CellType.STRING) {
                    return new BigDecimal(value.getStringValue());
                }
            } else if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            } else if (cell.getCellType() == CellType.STRING) {
                return new BigDecimal(cell.getStringCellValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return BigDecimal.ZERO;
    }


    private void saveDetailDPos(MultipartFormDataInput file, String parentId, String branchIdHeader, String transDate, String user) {
        try {
            InputPart inputPart = getInputPart(file);
            try (InputStream inputStream = inputPart.getBody(InputStream.class, null);
                 Workbook workbook = new XSSFWorkbook(inputStream)) {

                Sheet sheet = workbook.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() < 13) continue; // Lewati header dan metadata rows

                    Cell salesNumberCell = row.getCell(0);
                    if (salesNumberCell == null || salesNumberCell.getCellType() == CellType.BLANK) continue;

                    String transId = row.getCell(0).getStringCellValue(); // Sales Number sebagai Trans ID
                    String branchName = row.getCell(8).getStringCellValue(); // Branch
                    String branchId = masterMerchantRepository.getBranchIdByBranchName(branchName);
                    if(!branchIdHeader.equalsIgnoreCase(branchId)){
                        continue;
                    }


                    String formattedDate = getDate(row.getCell(4)); // Sales Date
                    String formattedTime = getTime(row.getCell(5));
                    if(!formattedDate.equalsIgnoreCase(transDate)){

                        continue;
                    }
                    // Parse gross/net sales
                    BigDecimal grossSales = parseBigDecimal(row.getCell(40));

                    String payMethod = "";
                    Cell payMethodCell = row.getCell(12);
                    if (payMethodCell != null && payMethodCell.getCellType() == CellType.STRING) {
                        payMethod = payMethodCell.getStringCellValue();
                    } else if (payMethodCell != null && payMethodCell.getCellType() == CellType.NUMERIC) {
                        payMethod = String.valueOf((int) payMethodCell.getNumericCellValue());
                    }
                    String payMethodName= paymentMethodRepository.getPaymentIdByPaymentMethod(payMethod);
                    if(null == payMethodName){
                        payMethodName= payMethod;
                    }
                    DetailPaymentPos detailPaymentPos = new DetailPaymentPos();
                    detailPaymentPos.setPmId("0");
                    detailPaymentPos.setBranchId(branchId);
                    detailPaymentPos.setTransDate(formattedDate);
                    detailPaymentPos.setTransId(transId);
                    detailPaymentPos.setTransTime(formattedTime);
                    detailPaymentPos.setGrossAmount(grossSales);
                    detailPaymentPos.setParentId(parentId);
                    detailPaymentPos.setPayMethodAggregator(payMethodName);
                    detailPaymentPos.setCreatedBy(user);// kosong, tidak ada di Excel ini

                    posRepository.persist(detailPaymentPos);
                }

                // update header payment
                /*String getTransDate = posRepository.getTransDateByParentId(parentId);
                headerPaymentRepository.updateDate(parentId, getTransDate);*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BigDecimal parseBigDecimal(Cell cell) {
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            } else if (cell.getCellType() == CellType.STRING) {
                String raw = cell.getStringCellValue().replace(".", "").replace(",", ".");
                return new BigDecimal(raw);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    String getFormattedDate(Cell dateCell) {
        try {
            if (dateCell.getCellType() == CellType.NUMERIC) {
                // Jika Excel menyimpan tanggal sebagai nilai numerik
                Date date = dateCell.getDateCellValue();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); // Format yang diinginkan
                return sdf.format(date);
            } else if (dateCell.getCellType() == CellType.STRING) {
                // Jika tanggal dalam format string seperti "30/04/2023 00.00.00"
                String rawDate = dateCell.getStringCellValue();
                SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy HH.mm.ss");
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date date = inputFormat.parse(rawDate);
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveDetailBank(MultipartFormDataInput file, String pmId, String branchId, String parentId) {
        try {
            InputPart inputPart = getInputPart(file);
            try (InputStream inputStream = inputPart.getBody(InputStream.class, null);
                 Workbook workbook = new XSSFWorkbook(inputStream)) {
                String getTransDate = "";
                Sheet sheet = workbook.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    Cell timeTransCell = row.getCell(2);
                    String formattedTimeDate = getFormattedDate(timeTransCell);

                    String accountNo = String.valueOf(row.getCell(0));

                    Cell creditCell = row.getCell(8);

                    String pmName = paymentMethodRepository.getPaymentMethodByPmId(pmId);

                    double creditAmount = 0;
                    if (creditCell != null) {
                        if (creditCell.getCellType() == CellType.NUMERIC) {
                            creditAmount = creditCell.getNumericCellValue();
                        } else if (creditCell.getCellType() == CellType.STRING) {
                            String cleanCredit = creditCell.getStringCellValue().replace(",", "").trim();
                            creditAmount = Double.parseDouble(cleanCredit);
                        }
                    }
                    String debitCredit = creditAmount > 0 ? "Credit" : "Debit";



                    Cell grossAmountCell = row.getCell(8);
                    BigDecimal grossAmount = null;
                    if (grossAmountCell.getCellType() == CellType.NUMERIC) {
                        grossAmount = BigDecimal.valueOf(grossAmountCell.getNumericCellValue());
                    } else if (grossAmountCell.getCellType() == CellType.STRING) {
                        // Menghapus koma sebelum parsing
                        String cleanAmount = grossAmountCell.getStringCellValue().replace(",", "");
                        grossAmount = new BigDecimal(cleanAmount);
                    }

                    String notes = String.valueOf(row.getCell(5));

                    BankMutation bm = new BankMutation();
                    bm.setBank(pmName);
                    bm.setAccountNo(accountNo);
                    bm.setNotes(notes);
                    bm.setAmount(grossAmount);
                    bm.setDebitCredit(DebitCredit.valueOf(debitCredit));
//                    bm.setTransDate(formattedTimeDate);
                    getTransDate=formattedTimeDate;
                    bankMutationRepository.persist(bm);

                }

                headerPaymentRepository.updateDate(parentId, getTransDate);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveDetailGrabFood(MultipartFormDataInput file, String pmId, String branchId, String parentId, String transDate, String user) {
        try {
            InputPart inputPart = getInputPart(file);
            try (InputStream inputStream = inputPart.getBody(InputStream.class, null);
                 Workbook workbook = new XSSFWorkbook(inputStream)) {

                Sheet sheet = workbook.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    Cell timeTransCell = row.getCell(5);
                    String formattedTimeDate = getDate(timeTransCell);
                    if(!formattedTimeDate.equalsIgnoreCase(transDate)){
                        continue;
                    }
                    Cell timeCell = row.getCell(5);
                    String formattedTime = getTime(timeCell);

                    Cell timeTransCellSett = row.getCell(27);
                    String formattedTimeDateSett = getDate(timeTransCellSett);

                    Cell timeCellSett = row.getCell(27);
                    String formattedTimeSett = getTime(timeCellSett);
                    String branchID = masterMerchantRepository.getBranchIdByBranchName(row.getCell(2).getStringCellValue());
                    if(!branchID.equals(branchId)){
                        continue;
                    }

                    Cell grossAmountCell = row.getCell(39);
                    BigDecimal grossAmount = null;
                    if (grossAmountCell.getCellType() == CellType.NUMERIC) {
                        grossAmount = BigDecimal.valueOf(grossAmountCell.getNumericCellValue());
                    } else if (grossAmountCell.getCellType() == CellType.STRING) {
                        grossAmount = new BigDecimal(grossAmountCell.getStringCellValue());
                    }
                    Cell nettAmountCell = row.getCell(52);
                    BigDecimal nettAmount = null;
                    if (nettAmountCell.getCellType() == CellType.NUMERIC) {
                        nettAmount = BigDecimal.valueOf(nettAmountCell.getNumericCellValue());
                    } else if (nettAmountCell.getCellType() == CellType.STRING) {
                        nettAmount = new BigDecimal(nettAmountCell.getStringCellValue());
                    }


                    Cell cell = row.getCell(10);
                    String transId = "";

                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING:
                                transId = cell.getStringCellValue();
                                break;
                            case NUMERIC:
                                transId = String.valueOf((long) cell.getNumericCellValue()); // atau gunakan format sesuai kebutuhan
                                break;
                            case BOOLEAN:
                                transId = String.valueOf(cell.getBooleanCellValue());
                                break;
                            case FORMULA:
                                transId = cell.getCellFormula();
                                break;
                            default:
                                transId = "";
                        }
                    }
                    DetailPaymentAggregator dpa = new DetailPaymentAggregator();
                    dpa.setBranchId(branchID);
                    dpa.setPmId(pmId);
                    dpa.setTransDate(formattedTimeDate);
                    dpa.setTransId(transId);
                    dpa.setTransTime(formattedTime);
                    dpa.setGrossAmount(grossAmount);
                    dpa.setNetAmount(nettAmount);
//                    dpa.setCharge(dpa.getGrossAmount().subtract(dpa.getNetAmount()));
                    dpa.setPaymentId(dpa.getTransId() + dpa.getPmId());
                    dpa.setSettlementDate(formattedTimeDateSett);
                    dpa.setSettlementTime(formattedTimeSett);
                    dpa.setParentId(parentId);
                    dpa.setCreatedBy(user);
                    detailPaymentAggregatorRepository.persist(dpa);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    InputPart getInputPart(MultipartFormDataInput file) {
        Map<String, List<InputPart>> fileMap = file.getFormDataMap();

        List<InputPart> fileParts = fileMap.get("file"); // Sesuaikan key dengan yang dikirim di Postman
        if (fileParts == null || fileParts.isEmpty()) {
            throw new IllegalArgumentException("File Not Found!");
        }

        InputPart inputPart = fileParts.get(0);
        return inputPart;
    }


    private void saveDetailPos(MultipartFormDataInput file, String parentId) {
        try {
            InputPart inputPart = getInputPart(file);
            try (InputStream inputStream = inputPart.getBody(InputStream.class, null);
                 Workbook workbook = new XSSFWorkbook(inputStream)) {

                Sheet sheet = workbook.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    if (row == null) break;
                    Cell pmIdCell = row.getCell(18);
                    String transId = row.getCell(12).getStringCellValue();
                    String branchName = row.getCell(0).getStringCellValue();
                    String branchId = masterMerchantRepository.getBranchIdByBranchName(branchName);
                    String pmId = "";
                    String pmIdString ="";
                    if (pmIdCell.getCellType() == CellType.NUMERIC) {
                        DecimalFormat df = new DecimalFormat("#");
                        pmId = df.format(pmIdCell.getNumericCellValue());
                    } else if (pmIdCell.getCellType() == CellType.STRING) {
                        pmId = pmIdCell.getStringCellValue();
                    }
                    pmIdString= paymentMethodRepository.getPaymentIdByPaymentMethod(pmId);
                    Cell grossAmountCell = row.getCell(10);
                    BigDecimal grossAmount = null;
                    if (grossAmountCell.getCellType() == CellType.NUMERIC) {
                        grossAmount = BigDecimal.valueOf(grossAmountCell.getNumericCellValue());
                    } else if (grossAmountCell.getCellType() == CellType.STRING) {
                        grossAmount = new BigDecimal(grossAmountCell.getStringCellValue());
                    }
                    Cell timeTransCell = row.getCell(1);
                    String formattedTimeDate = getDate(timeTransCell);

                    Cell timeCell = row.getCell(2);
                    String formattedTime = getTime(timeCell);
                    DetailPaymentPos detailPaymentPos = new DetailPaymentPos();
                    detailPaymentPos.setPmId("0");
                    detailPaymentPos.setBranchId(branchId);
                    detailPaymentPos.setTransDate(formattedTimeDate);
                    detailPaymentPos.setTransId(transId);
                    detailPaymentPos.setTransTime(formattedTime);
                    System.out.println("detail "+ detailPaymentPos.getTransTime());
                    detailPaymentPos.setGrossAmount(grossAmount);
                    detailPaymentPos.setParentId(parentId);
                    detailPaymentPos.setPayMethodAggregator(pmIdString);
                    posRepository.persist(detailPaymentPos);
                }

                // update header payment
                String getTransDate = posRepository.getTransDateByParentId(parentId);
                headerPaymentRepository.updateDate(parentId, getTransDate);
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void saveDetailShopeeFood(MultipartFormDataInput file, String pmId, String parentId, String branchId, String transDate, String user) {
        try {
            InputPart inputPart = getInputPart(file);
            try (InputStream inputStream = inputPart.getBody(InputStream.class, null);
                 Workbook workbook = new XSSFWorkbook(inputStream)) {

                Sheet sheet = workbook.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    String transId = row.getCell(0).getStringCellValue();
                    Cell timeTransCell = row.getCell(4);
                    String formattedTimeDate = getDate(timeTransCell);

                    Cell timeTransCellSett = row.getCell(17);
                    String formattedTimeDateSett = getDate(timeTransCellSett);

                    Cell timeCell = row.getCell(4);
                    String formattedTime = getTime(timeCell);
                    String branchID = masterMerchantRepository.getBranchIdByBranchName(row.getCell(3).getStringCellValue());
                    if(!branchID.equals(branchId)){
                        continue;
                    }


                    Cell grossAmountCell = row.getCell(5);
                    BigDecimal grossAmount = null;
                    if (grossAmountCell.getCellType() == CellType.NUMERIC) {
                        grossAmount = BigDecimal.valueOf(grossAmountCell.getNumericCellValue());
                    } else if (grossAmountCell.getCellType() == CellType.STRING) {
                        grossAmount = new BigDecimal(grossAmountCell.getStringCellValue());
                    }
                    Cell nettAmountCell = row.getCell(13);
                    BigDecimal nettAmount = null;
                    if (nettAmountCell.getCellType() == CellType.NUMERIC) {
                        nettAmount = BigDecimal.valueOf(nettAmountCell.getNumericCellValue());
                    } else if (nettAmountCell.getCellType() == CellType.STRING) {
                        nettAmount = new BigDecimal(nettAmountCell.getStringCellValue());
                    }

                    DetailPaymentAggregator dpa = new DetailPaymentAggregator();
                    dpa.setBranchId(branchID);
                    dpa.setPmId(pmId);
                    dpa.setTransDate(formattedTimeDate);
                    System.out.println("ini tggl 1 "+ dpa.getTransDate());
                    System.out.println("ini tggl 2 "+ transDate);
                    if(!dpa.getTransDate().equals(transDate)){
                        continue;
                    }
                    dpa.setTransId(transId);
                    dpa.setTransTime(formattedTime);
                    dpa.setGrossAmount(grossAmount);
                    dpa.setNetAmount(nettAmount);
                    dpa.setParentId(parentId);
                    dpa.setSettlementDate(formattedTimeDateSett);
                    dpa.setSettlementTime(formattedTime);
                    dpa.setCreatedBy(user);
                    detailPaymentAggregatorRepository.persist(dpa);
                }
// update header payment
                /*String getTransDate = detailPaymentAggregatorRepository.getTransDateByParentId(parentId);
                headerPaymentRepository.updateDate(parentId, getTransDate);*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getDate(Cell timeTransCell) {
        if (timeTransCell == null) {
            return null;
        }

        if (timeTransCell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(timeTransCell)) {
                // Format cell sebagai date
                Date dateTime = timeTransCell.getDateCellValue();
                return new SimpleDateFormat("yyyy-MM-dd").format(dateTime);
            } else {
                double numericValue = timeTransCell.getNumericCellValue();
                // Serial number untuk 1 Jan 2000 = 36526
                if (numericValue > 36500) {
                    Date possibleDate = DateUtil.getJavaDate(numericValue);
                    return new SimpleDateFormat("yyyy-MM-dd").format(possibleDate);
                } else {
                    System.out.println("Cell numeric terlalu kecil untuk dianggap sebagai tanggal: " + numericValue);
                    return null;
                }
            }
        } else if (timeTransCell.getCellType() == CellType.STRING) {
            String rawValue = timeTransCell.getStringCellValue().trim();

            // Daftar format tanggal yang mungkin
            String[] patterns = {
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy/MM/dd HH:mm:ss",
                    "yyyy-MM-dd"
            };

            for (String pattern : patterns) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat(pattern);
                    Date parsedDate = inputFormat.parse(rawValue);
                    return new SimpleDateFormat("yyyy-MM-dd").format(parsedDate);
                } catch (ParseException e) {
                    // Lanjut ke pola berikutnya
                }
            }

            // Jika parsing gagal semua, return raw value atau null
            System.out.println("Gagal parse string date: " + rawValue);
            return null;
        }

        // Tipe cell tidak didukung
        return null;
    }




    private String getTime(Cell timeCell) {
        if (timeCell == null) {
            return null;
        }

        if (timeCell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(timeCell)) {
                Date dateTime = timeCell.getDateCellValue();
                return new SimpleDateFormat("HH:mm:ss").format(dateTime);
            } else {
                double numericValue = timeCell.getNumericCellValue();

                // Cek apakah angka cukup besar untuk dianggap sebagai waktu dari serial number Excel
                if (numericValue > 0.0) {
                    Date possibleDate = DateUtil.getJavaDate(numericValue);
                    return new SimpleDateFormat("HH:mm:ss").format(possibleDate);
                } else {
                    System.out.println("Cell numeric terlalu kecil untuk dianggap sebagai waktu: " + numericValue);
                    return null;
                }
            }
        } else if (timeCell.getCellType() == CellType.STRING) {
            String rawValue = timeCell.getStringCellValue().trim();
            try {
                // Coba parse sebagai ISO datetime format
                Date parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(rawValue);
                return new SimpleDateFormat("HH:mm:ss").format(parsedDate);
            } catch (ParseException e1) {
                try {
                    // Coba parse sebagai datetime biasa
                    Date parsedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(rawValue);
                    return new SimpleDateFormat("HH:mm:ss").format(parsedDate);
                } catch (ParseException e2) {
                    return rawValue; // fallback ke raw jika tidak bisa diparse
                }
            }
        }

        return null;
    }





    public static String generateRandomCode() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String datePart = dateFormat.format(new Date());

        Random random = new Random();
        int randomNumber = 1000 + random.nextInt(9000);
        return datePart + randomNumber;
    }


    public void processTransTime(GeneralRequest request) {
        try {
            List<String> transTimes = new ArrayList<>();
            List<String> transTimePos = posRepository.getListTransTime(request);
            List<String> transTimeAgg = detailPaymentAggregatorRepository.getListTransTime(request);

            if (transTimePos.size() >= transTimeAgg.size()) {
                transTimes.addAll(transTimePos);
            } else {
                transTimes.addAll(transTimeAgg);
            }

            List<String> pmIds =  headerPaymentRepository.getPaymentMethodsByRequest(request);
            for(String pmId : pmIds){
                request.setPmId(pmId);
                for(String transTime : transTimes){
                    request.setTransTime(transTime);
                    processUpdate(request, MessageConstant.ONE_VALUE);
                }
            }


        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Transactional
    /*public void reconBankAggregator(GeneralRequest request) {

        // get pm id by date
        List<String> pmIds = headerPaymentRepository.getPaymentMethodByDate(request.getTransDate());
        for(String pmId : pmIds){

            request.setPmId(pmId);
            String payMeth = paymentMethodRepository.getPaymentMethodByPmId(pmId);
            int countDataBank = bankMutationRepository.getCountBank(request, payMeth);
            List<BigDecimal> netAmountBank = bankMutationRepository.getAmontBank(request, payMeth);
            List<Map<String, Object>> dataBank = bankMutationRepository.getDataBank(request, payMeth);
            List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAgg(request, netAmountBank);
            int countDataAgg = detailPaymentAggregatorRepository.getCountDataAggByDate(request, netAmountBank);
            if(countDataAgg>0 && countDataBank>0){
                if(countDataAgg==countDataBank){
                    // update data agg
                    for(Map<String, Object> obj : dataBank){

                        detailPaymentAggregatorRepository.updateDataReconBank(request, (BigDecimal) obj.get("netAmount"), obj.get("bankMutationId").toString());
                    }

                }else if(countDataAgg>countDataBank){
                    // to do
                    System.out.println("apa masuk sini");
                }else{
                    // update data agg
                    int index = 0;
                    for(Map<String, Object> obj : dataAgg){
                        if (((BigDecimal) obj.get("netAmount")).compareTo((BigDecimal) dataBank.get(index).get("netAmount")) == 0) {
                            // Nilai BigDecimal sama
                            detailPaymentAggregatorRepository.updateDataReconAgg2Bank((Long) obj.get("detailPaymentId"), obj.get("bankMutationId").toString());
                        }

                    }
                }
            }
        }


    }*/

    /*public void reconBankAggregator(GeneralRequest request) {
        System.out.println("masukkkkkk");
        List<String> pmIds = headerPaymentRepository.getPaymentMethodByDate(request.getTransDate());

        for (String pmId : pmIds) {
            System.out.println("apakah");
            request.setPmId(pmId);
            String payMeth = paymentMethodRepository.getPaymentMethodByPmId(pmId);

            List<Map<String, Object>> dataBank = bankMutationRepository.getDataBank(request, payMeth);
            List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAgg(request,
                    bankMutationRepository.getAmontBank(request, payMeth));
            System.out.println(dataAgg.size());
            System.out.println(dataBank.size());

            // Simpan dataBank dalam Map<netAmount, Queue<bankMutationId>>
            Map<BigDecimal, Queue<String>> bankMap = new HashMap<>();
            for (Map<String, Object> obj : dataBank) {
                BigDecimal amount = (BigDecimal) obj.get("netAmount");
                String bankMutationId = obj.get("bankMutationId").toString();
                bankMap.putIfAbsent(amount, new LinkedList<>());
                bankMap.get(amount).add(bankMutationId);
                System.out.println("woyyyyy");
            }

            // Simpan dataAgg dalam Map<netAmount, Queue<detailPaymentId>>
            Map<BigDecimal, Queue<Long>> aggMap = new HashMap<>();
            for (Map<String, Object> obj : dataAgg) {
                BigDecimal amount = (BigDecimal) obj.get("netAmount");
                Long detailPaymentId = (Long) obj.get("detailPaymentId");
                aggMap.putIfAbsent(amount, new LinkedList<>());
                aggMap.get(amount).add(detailPaymentId);
            }

            // Proses hanya data yang punya pasangan di kedua map
            for (BigDecimal amount : aggMap.keySet()) {
                if (!bankMap.containsKey(amount)) {
                    // Skip jika tidak ada jumlah yang sama di dataBank
                    continue;
                }

                Queue<Long> aggQueue = aggMap.get(amount);
                Queue<String> bankQueue = bankMap.get(amount);

                while (!aggQueue.isEmpty() && !bankQueue.isEmpty()) {
                    System.out.println("ada yg masuk sini? ");
                    Long detailPaymentId = aggQueue.poll();
                    String bankMutationId = bankQueue.poll();

                    detailPaymentAggregatorRepository.updateDataReconAgg2Bank(
                            detailPaymentId, bankMutationId
                    );
                }
            }
        }
    }*/

    // from git
    public void reconBankAggregator(GeneralRequest request) {
        List<String> pmIds =  headerPaymentRepository.getPaymentMethodsByRequest(request);
        for(String pmId : pmIds){
            request.setPmId(pmId);
            String payMeth = paymentMethodRepository.getPaymentMethodByPmId(pmId);
            if(payMeth.equalsIgnoreCase(MessageConstant.GOPAY)
                    || payMeth.equalsIgnoreCase(MessageConstant.GOFOOD) || payMeth.equalsIgnoreCase(MessageConstant.SHOPEEFOOD) || payMeth.equalsIgnoreCase(MessageConstant.GRABFOOD)){
                reconBankAggregatorForGoTo(request);
            }else if(payMeth.equalsIgnoreCase("QRIS (ESB)")){
                reconBankAggregatorForEsb(request);
            }
            else{
                List<BigDecimal> netAmountBank = bankMutationRepository.getAmountBank(request, payMeth);
                List<Map<String, Object>> dataBank = bankMutationRepository.getDataBank(request, payMeth);
                List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAgg(request, netAmountBank, payMeth);
                LinkedList<Map<String, Object>> queueBank = new LinkedList<>(dataBank);

                for (Map<String, Object> agg : dataAgg) {

                    BigDecimal aggAmount = (BigDecimal) agg.get("netAmount");
                    boolean matched = false;

                    Iterator<Map<String, Object>> iterator = queueBank.iterator();
                    while (iterator.hasNext()) {
                        Map<String, Object> bank = iterator.next();
                        BigDecimal bankAmount = (BigDecimal) bank.get("netAmount");
                        if (aggAmount.compareTo(bankAmount) == 0) {
                            detailPaymentAggregatorRepository.updateDataReconAgg2Bank(
                                    (Long) agg.get("detailPaymentId"),
                                    bank.get("bankMutationId").toString(), request.getUser()
                            );

                            // Hapus dari queueBank agar tidak digunakan dua kali
                            bankMutationRepository.updateFlagBank( bank.get("bankMutationId").toString(), request.getUser());
                            iterator.remove();
                            matched = true;
                            break; // Stop iterasi setelah menemukan pasangan pertama
                        }
                    }

                    if (!matched) {
                        System.out.println("❌ Tidak ada pasangan untuk transaksi di dataAgg: " + agg.get("detailPaymentId"));
                    }
                }
            }

        }
    }

    private void reconBankAggregatorForEsb(GeneralRequest request) {
        List<String> pmIds = headerPaymentRepository.getPaymentMethodsByRequest(request);
        String transDate = request.getTransDate();

        for (String pmId : pmIds) {
            request.setTransDate(transDate);
            request.setPmId(pmId);

            String payMeth = paymentMethodRepository.getPaymentMethodByPmId(pmId);
            List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAggEsb(request, payMeth);

            System.out.println("data agg: " + dataAgg.size());

            // Grouping dataAgg by settlement date
            Map<String, List<Map<String, Object>>> aggBySettDate = new HashMap<>();
            for (Map<String, Object> agg : dataAgg) {
                String settDate = agg.get("settDate").toString();
                aggBySettDate.computeIfAbsent(settDate, k -> new ArrayList<>()).add(agg);
            }

            for (String settDate : aggBySettDate.keySet()) {
                request.setTransDate(settDate);
                System.out.println("get transdate2: " + request.getTransDate());

                List<Map<String, Object>> dataBank = bankMutationRepository.getDataBank(request, payMeth);
                System.out.println("data bank: " + dataBank.size());

                BigDecimal totalAggAmount = BigDecimal.ZERO;
                List<Map<String, Object>> currentAggList = aggBySettDate.get(settDate);
                for (Map<String, Object> agg : currentAggList) {
                    totalAggAmount = totalAggAmount.add((BigDecimal) agg.get("netAmount"));
                }

                for (Map<String, Object> bank : dataBank) {
                    BigDecimal bankAmount = (BigDecimal) bank.get("netAmount");

                    // Khusus ShopeeFood: tidak peduli tanggal
                    if (payMeth.equalsIgnoreCase(MessageConstant.SHOPEEFOOD)) {
                        totalAggAmount = dataAgg.stream()
                                .map(a -> (BigDecimal) a.get("netAmount"))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }

                    if (totalAggAmount.compareTo(bankAmount) == 0) {
                        // Update detail aggregator
                        for (Map<String, Object> agg : currentAggList) {
                            detailPaymentAggregatorRepository.updateDataReconAgg2Bank(
                                    (Long) agg.get("detailPaymentId"),
                                    bank.get("bankMutationId").toString(), request.getUser()
                            );
                        }
                        // Update flag bank
                        bankMutationRepository.updateFlagBank(bank.get("bankMutationId").toString(), request.getUser());
                        break; // break karena sudah match
                    }
                }
            }
        }
    }


    /*public void reconBankAggregatorForGoTo(GeneralRequest request) {
        List<String> pmIds = headerPaymentRepository.getPaymentMethodByDate(request.getTransDate());
        String transDate = request.getTransDate();
        for(String pmId : pmIds){
            request.setTransDate(transDate);
            request.setPmId(pmId);
            String payMeth = paymentMethodRepository.getPaymentMethodByPmId(pmId);
            List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAggGoTo(request, payMeth);
//            request.setTransDate(settlementDate.toString());
            System.out.println("data agg "+ dataAgg.size());
            for (Map<String, Object> agg2 : dataAgg) {
                request.setTransDate(agg2.get("settDate").toString());
                System.out.println("get transdate "+ request.getTransDate());
                List<Map<String, Object>> dataBank = bankMutationRepository.getDataBank(request, payMeth);
                System.out.println("data bank "+ dataBank.size());
                BigDecimal aggAmountGofood = BigDecimal.ZERO;
                LinkedList<Map<String, Object>> queueBank = new LinkedList<>(dataBank);
                Iterator<Map<String, Object>> iterator = queueBank.iterator();
                while (iterator.hasNext()) {

                    Map<String, Object> bank = iterator.next();
                    BigDecimal bankAmount = (BigDecimal) bank.get("netAmount");

                    for (Map<String, Object> agg : dataAgg) {
                        if(payMeth.equalsIgnoreCase(MessageConstant.SHOPEEFOOD)){
                            aggAmountGofood =aggAmountGofood.add((BigDecimal)agg.get("netAmount"));
                        }else{
                            if(agg.get("settDate").toString().equalsIgnoreCase(bank.get("settDate").toString())){
                                aggAmountGofood =aggAmountGofood.add((BigDecimal)agg.get("netAmount"));
                            }
                        }
                    }

                    if (aggAmountGofood.compareTo(bankAmount) == 0) {
                        // Cocok, lakukan update
                        for (Map<String, Object> agg : dataAgg) {
                            detailPaymentAggregatorRepository.updateDataReconAgg2Bank(
                                    (Long) agg.get("detailPaymentId"),
                                    bank.get("bankMutationId").toString()
                            );
                        }
                        bankMutationRepository.updateFlagBank(bank.get("bankMutationId").toString());
                        break;
                    }
                }
            }

        }
    }*/

    public void reconBankAggregatorForGoTo(GeneralRequest request) {
        List<String> pmIds = headerPaymentRepository.getPaymentMethodsByRequest(request);
        String transDate = request.getTransDate();

        for (String pmId : pmIds) {
            request.setTransDate(transDate);
            request.setPmId(pmId);

            String payMeth = paymentMethodRepository.getPaymentMethodByPmId(pmId);
            List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAggGoTo(request, payMeth);

            System.out.println("data agg: " + dataAgg.size());

            // Grouping dataAgg by settlement date
            Map<String, List<Map<String, Object>>> aggBySettDate = new HashMap<>();
            for (Map<String, Object> agg : dataAgg) {
                String settDate = agg.get("settDate").toString();
                aggBySettDate.computeIfAbsent(settDate, k -> new ArrayList<>()).add(agg);
            }

            for (String settDate : aggBySettDate.keySet()) {
                request.setTransDate(settDate);
                System.out.println("get transdate2: " + request.getTransDate());

                List<Map<String, Object>> dataBank = bankMutationRepository.getDataBank(request, payMeth);
                System.out.println("data bank: " + dataBank.size());

                BigDecimal totalAggAmount = BigDecimal.ZERO;
                List<Map<String, Object>> currentAggList = aggBySettDate.get(settDate);
                for (Map<String, Object> agg : currentAggList) {
                    totalAggAmount = totalAggAmount.add((BigDecimal) agg.get("netAmount"));
                }

                for (Map<String, Object> bank : dataBank) {
                    BigDecimal bankAmount = (BigDecimal) bank.get("netAmount");

                    // Khusus ShopeeFood: tidak peduli tanggal
                    if (payMeth.equalsIgnoreCase(MessageConstant.SHOPEEFOOD)) {
                        totalAggAmount = dataAgg.stream()
                                .map(a -> (BigDecimal) a.get("netAmount"))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }

                    if (totalAggAmount.compareTo(bankAmount) == 0) {
                        // Update detail aggregator
                        for (Map<String, Object> agg : currentAggList) {
                            detailPaymentAggregatorRepository.updateDataReconAgg2Bank(
                                    (Long) agg.get("detailPaymentId"),
                                    bank.get("bankMutationId").toString(), request.getUser()
                            );
                        }
                        // Update flag bank
                        bankMutationRepository.updateFlagBank(bank.get("bankMutationId").toString(), request.getUser());
                        break; // break karena sudah match
                    }
                }
            }
        }
    }


    public void reconPosWithEBS(GeneralRequest request) {
        List<String> pmIds = headerPaymentRepository.getPaymentMethodsByRequest(request);
        String transDate = request.getTransDate();

        for (String pmId : pmIds) {
            request.setTransDate(transDate);
            request.setPmId(pmId);

            String payMeth = paymentMethodRepository.getPaymentMethodByPmId(pmId);
            List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAggGoTo(request, payMeth);

            System.out.println("data agg: " + dataAgg.size());

            // Grouping dataAgg by settlement date
            Map<String, List<Map<String, Object>>> aggBySettDate = new HashMap<>();
            for (Map<String, Object> agg : dataAgg) {
                String settDate = agg.get("settDate").toString();
                aggBySettDate.computeIfAbsent(settDate, k -> new ArrayList<>()).add(agg);
            }

            for (String settDate : aggBySettDate.keySet()) {
                request.setTransDate(settDate);
                System.out.println("get transdate: " + request.getTransDate());

                List<Map<String, Object>> dataBank = bankMutationRepository.getDataBank(request, payMeth);
                System.out.println("data bank: " + dataBank.size());

                BigDecimal totalAggAmount = BigDecimal.ZERO;
                List<Map<String, Object>> currentAggList = aggBySettDate.get(settDate);
                for (Map<String, Object> agg : currentAggList) {
                    totalAggAmount = totalAggAmount.add((BigDecimal) agg.get("netAmount"));
                }

                for (Map<String, Object> bank : dataBank) {
                    BigDecimal bankAmount = (BigDecimal) bank.get("netAmount");

                    // Khusus ShopeeFood: tidak peduli tanggal
                    if (payMeth.equalsIgnoreCase(MessageConstant.SHOPEEFOOD)) {
                        totalAggAmount = dataAgg.stream()
                                .map(a -> (BigDecimal) a.get("netAmount"))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }

                    if (totalAggAmount.compareTo(bankAmount) == 0) {
                        // Update detail aggregator
                        for (Map<String, Object> agg : currentAggList) {
                            detailPaymentAggregatorRepository.updateDataReconAgg2Bank(
                                    (Long) agg.get("detailPaymentId"),
                                    bank.get("bankMutationId").toString(), request.getUser()
                            );
                        }
                        // Update flag bank
                        bankMutationRepository.updateFlagBank(bank.get("bankMutationId").toString(), request.getUser());
                        break; // break karena sudah match
                    }
                }
            }
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Response saveDataLog(GeneralRequest request) {
        BaseResponse baseResponse;
        try {
            LogRecon logRecon = new LogRecon();
//            logRecon.setLogId(0L);
            logRecon.setBranchId(request.getBranchId());
            logRecon.setSubmittedAt(getTime(new Date()));
            logRecon.setDate(request.getTransDate());
            logRecon.setCreatedBy(request.getUser());
            // Add validation if needed
            if(logRecon.getBranchId() == null) {
                throw new IllegalArgumentException("Branch ID cannot be null");
            }

            System.out.println("Saving LogRecon: " + logRecon);
            logReconRepository.persist(logRecon);
            System.out.println("Saved LogRecon ID: " + logRecon.getLogId());

            baseResponse = new BaseResponse(MessageConstant.SUCCESS_CODE, MessageConstant.SUCCESS_MESSAGE);
            return Response.status(baseResponse.result).entity(baseResponse).build();
        } catch (Exception e) {
            System.out.println("langsung gagal");
            e.printStackTrace();

            baseResponse = new BaseResponse(MessageConstant.FAILED_CODE, MessageConstant.FAILED_MESSAGE);
            return Response.status(baseResponse.result)
                    .entity(baseResponse)
                    .build();
        }
    }

    private String getTime(Date transDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss"); // Format hanya time
        return sdf.format(transDate);
    }


    public void processWithoutTransTime(GeneralRequest request) {
        try {
            List<String> pmIds =  headerPaymentRepository.getPaymentMethodsByRequest(request);
            for(String pmId : pmIds){
                request.setPmId(pmId);
                System.out.println("phase 2 "+ request.getPmId());
                processUpdate(request, MessageConstant.TWO_VALUE);
            }


        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void processUpdate(GeneralRequest request, String updateMessage){
        List<Map<String, Object>> dataAgg = detailPaymentAggregatorRepository.getDataAggByTransTime(request);
        List<Map<String, Object>> dataPos = posRepository.getDataPosByTransTime(request);
        LinkedList<Map<String, Object>> queueBank = new LinkedList<>(dataPos);
        for (Map<String, Object> agg : dataAgg) {
            BigDecimal aggAmount = (BigDecimal) agg.get("grossAmount");
            boolean matched = false;

            Iterator<Map<String, Object>> iterator = queueBank.iterator();
            while (iterator.hasNext()) {
                Map<String, Object> pos = iterator.next();
                BigDecimal posAmount = (BigDecimal) pos.get("grossAmount");
                if (aggAmount.compareTo(posAmount) == 0) {
                    // Cocok, lakukan update
                    LocalDateTime now = LocalDateTime.now();
                    String dateOnly = now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    String timeOnly = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                    detailPaymentAggregatorRepository.updateDataAggWithChange(
                            (Long) agg.get("detailPaymentId"),
                            updateMessage, dateOnly, timeOnly, request.getUser()
                    );
                    posRepository.updateDataPos2((Long) agg.get("detailPaymentId"),
                            updateMessage, (Long) pos.get("detailPosId"), dateOnly, timeOnly, request.getUser());

                    // Hapus dari queueBank agar tidak digunakan dua kali
                    iterator.remove();
                    matched = true;
                    break; // Stop iterasi setelah menemukan pasangan pertama
                }
            }

            if (!matched) {
                System.out.println("❌ Tidak ada pasangan untuk transaksi di dataAgg: " + agg.get("detailPaymentId"));
            }
        }
    }

    public void processWithTransDateAndBranch(GeneralRequest request) {
        processUpdate(request, MessageConstant.THREE_VALUE);
    }

    public void summaryReconEcom2Pos(GeneralRequest request) {
        List<HeaderPayment> headerPayments = headerPaymentRepository.getByTransDateAndBranchId(request.getTransDate(), request.getBranchId());
        for(HeaderPayment hp  : headerPayments){
            String pmName = paymentMethodRepository.getPaymentMethodByPmId(hp.getPmId());
            if(pmName.equalsIgnoreCase(MessageConstant.POS)){
                int countFailedPos = posRepository.getCountFailedByParentId(hp.getParentId());
                if(countFailedPos==0){
                    headerPaymentRepository.updateHeader(hp.getParentId());
                }
            }else if(pmName.equalsIgnoreCase("BCA") || pmName.equalsIgnoreCase("Bank Mandiri")){
                int countFailedAggregator= bankMutationRepository.getFailedRecon(hp.getParentId());
                if(countFailedAggregator==0){
                    headerPaymentRepository.updateHeaderBank(hp.getParentId());
                }
            }
            else{
                int countFailedAggregator= detailPaymentAggregatorRepository.getFailedRecon(hp.getParentId());
                if(countFailedAggregator==0){
                    headerPaymentRepository.updateHeaderEcom(hp.getParentId());
                }

            }
        }
    }

    private void saveDetailGoFood(MultipartFormDataInput file, String pmId, String branchId, String parentId, String transDate, String user) {
        try {
            InputPart inputPart = getInputPart(file);
            try (InputStream inputStream = inputPart.getBody(InputStream.class, null);
                 Workbook workbook = new XSSFWorkbook(inputStream)) {

                Sheet sheet = workbook.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    String transId = row.getCell(4).getStringCellValue();
                    Cell timeTransCell = row.getCell(8);
                    String formattedTimeDate = getDate(timeTransCell);
                    if(!formattedTimeDate.equalsIgnoreCase(transDate)){
                        continue;
                    }
                    Cell timeSettCell = row.getCell(18);
                    String formattedTimeSett = getDate(timeSettCell);

                    Cell timeCell = row.getCell(8);
                    String formattedTime = getTime(timeCell);

                    Cell timeSett = row.getCell(19);
                    String formattedTimSett = getTime(timeSett);
                    String branchID = masterMerchantRepository.getBranchIdByBranchName(row.getCell(0).getStringCellValue());
                    if(!branchID.equals(branchId)){
                        continue;
                    }

                    Cell grossAmountCell = row.getCell(5);
                    BigDecimal grossAmount = null;
                    if (grossAmountCell.getCellType() == CellType.NUMERIC) {
                        grossAmount = BigDecimal.valueOf(grossAmountCell.getNumericCellValue());
                    } else if (grossAmountCell.getCellType() == CellType.STRING) {
                        grossAmount = new BigDecimal(grossAmountCell.getStringCellValue());
                    }
                    Cell nettAmountCell = row.getCell(6);
                    BigDecimal nettAmount = null;
                    if (nettAmountCell.getCellType() == CellType.NUMERIC) {
                        nettAmount = BigDecimal.valueOf(nettAmountCell.getNumericCellValue());
                    } else if (nettAmountCell.getCellType() == CellType.STRING) {
                        nettAmount = new BigDecimal(nettAmountCell.getStringCellValue());
                    }

                    DetailPaymentAggregator dpa = new DetailPaymentAggregator();
                    dpa.setBranchId(branchID);
                    dpa.setPmId(pmId);
                    dpa.setTransDate(formattedTimeDate);
                    dpa.setTransId(transId);
                    dpa.setTransTime(formattedTime);
                    dpa.setGrossAmount(grossAmount);
                    dpa.setNetAmount(nettAmount);
                    dpa.setCharge(dpa.getGrossAmount().subtract(dpa.getNetAmount()));
                    dpa.setPaymentId(dpa.getTransId() + dpa.getPmId());
                    dpa.setSettlementDate(formattedTimeSett);
                    dpa.setSettlementTime(formattedTimSett);
                    dpa.setParentId(parentId);
                    dpa.setCreatedBy(user);
                    detailPaymentAggregatorRepository.persist(dpa);
                }
                /*String getTransDate = detailPaymentAggregatorRepository.getTransDateByParentId(parentId);
                headerPaymentRepository.updateDate(parentId, getTransDate);*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatePosToZeroValue(GeneralRequest request) {
        posRepository.updateToZeroByRequest(request);
    }

    public void updateAggToZeroValue(GeneralRequest request) {detailPaymentAggregatorRepository.updateToZeroByRequest(request);
    }


}
