package com.mannit.chatbot.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.mannit.chatbot.config.Configvalues;
import com.mannit.chatbot.model.CurrentPatient;
import com.mannit.chatbot.model.Latestdatecollection;
import com.mannit.chatbot.model.Noappointment;
import com.mannit.chatbot.model.QueriedPatient;
import com.mannit.chatbot.repository.Currentpatientsrepo;
import com.mannit.chatbot.repository.Latestdatecollectionrepo;
import com.mannit.chatbot.repository.Noappointmentrepo;
import com.mannit.chatbot.repository.QuriedpRepo;

@RestController
public class SheetsQuickstart {
    private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private final static Logger logger = LoggerFactory.getLogger(SheetsQuickstart.class);
    @Autowired
    private Currentpatientsrepo repo;
    @Autowired
    private Noappointmentrepo no_app_repo;
    @Autowired
    private QuriedpRepo quried_repo;
    @Autowired
    private Latestdatecollectionrepo date_repo;
    @Autowired
    private Configvalues spreadsheetVal;

    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private String lastProcessedTimestampYesPatients = "";
    private String lastProcessedTimestampNoAppointment = "";
    private String lastProcessedTimestampCallBack = "";

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        logger.info("<START in the getcredentials method>");
        InputStream in = SheetsQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                clientSecrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                        .setAccessType("offline").build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        logger.info("</END in the getcredentials method>");
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    @Scheduled(fixedRate = 600000)
    public void getsheetdata() throws GeneralSecurityException, IOException {
        logger.info("<In the getSheetdata() method start>");
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        // final String spreadsheetId = "16mQkUXa6PeeH96WDFeATHPLFx_dYUUOGcXqJ9clkBek";
        final String spreadsheetId = spreadsheetVal.getSpreadsheetId();
        final String range_1 = "Yes-patients";
        final String range_2 = "No-appointment";
        final String range_3 = "Call-me-back";
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME).build();
        logger.info("<Started Reading the spreadsheet with spreadsheet id >" + spreadsheetId);
        ValueRange response_1 = service.spreadsheets().values().get(spreadsheetId, range_1).execute();
        List<List<Object>> values1 = response_1.getValues();
        lastProcessedTimestampYesPatients = processSheetData("Yes-patients", values1,
                lastProcessedTimestampYesPatients);

        ValueRange response_2 = service.spreadsheets().values().get(spreadsheetId, range_2).execute();
        List<List<Object>> values2 = response_2.getValues();
        lastProcessedTimestampNoAppointment = processSheetData("No-appointment", values2,
                lastProcessedTimestampNoAppointment);

        ValueRange response_3 = service.spreadsheets().values().get(spreadsheetId, range_3).execute();
        List<List<Object>> values3 = response_3.getValues();
        lastProcessedTimestampCallBack = processSheetData("Call-me-back", values3, lastProcessedTimestampCallBack);
    }

    private String processSheetData(String sheetName, List<List<Object>> values, String lastProcessedTimestamp) {
        boolean isFirstRow = true;
        if (values == null || values.isEmpty()) {
            System.out.println("No data found for " + sheetName);
        } else {
            logger.info("<Sheet value>" + values.size());
            for (List<Object> row : values) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }
                String timestamp = row.get(0).toString();

                Optional<Latestdatecollection> ct = date_repo.findById(insertDummyDocument());
                Latestdatecollection ct3 = ct.orElse(null);
                String c_date = ct3.getAppointment_lastupdated();
                String n_date = ct3.getRejected_lastupdated();
                String q_date = ct3.getQueried_lastupdated();

                // if (timestamp.compareTo(lastProcessedTimestamp) > 0) {
                if (sheetName.equals("Yes-patients") && compare(timestamp, c_date)) {
                    logger.info("<START Yes-patients>");
                    CurrentPatient cp = new CurrentPatient();
                    cp.setTimestamp(row.get(0).toString());
                    cp.setName(row.get(1).toString());
                    cp.setPhone_number(row.get(2).toString());
                    cp.setDoctor_choice(row.get(3).toString());
                    ct3.setAppointment_lastupdated(row.get(0).toString());
                    repo.save(cp);
                    logger.info("</Insert Yes-patients ::>" + cp);
                } else if (sheetName.equals("No-appointment") && compare(timestamp, n_date)) {
                    logger.info("<START Insert No-appointment>");
                    Noappointment noAppointment = new Noappointment();
                    noAppointment.setName(row.get(1).toString());
                    noAppointment.setPhone_number(row.get(2).toString());
                    noAppointment.setTimestamp(row.get(0).toString());
                    ct3.setRejected_lastupdated(row.get(0).toString());
                    no_app_repo.save(noAppointment);
                    logger.info("</End Insert No-appointment ::>" + noAppointment);
                } else if (sheetName.equals("Call-me-back") && compare(timestamp, q_date)) {
                    logger.info("<START Call-me-back>");
                    QueriedPatient qp = new QueriedPatient();
                    qp.setName(row.get(1).toString());
                    qp.setPhone_number(row.get(2).toString());
                    qp.setTimestamp(row.get(0).toString());
                    ct3.setQueried_lastupdated(row.get(0).toString());
                    quried_repo.save(qp);
                    logger.info("</End Insert Call-me-back ::>" + qp);
                }
                date_repo.save(ct3);
                lastProcessedTimestamp = timestamp;
            }
        }
        // }
        return lastProcessedTimestamp;
    }

    @RequestMapping(value = "/api/getbydate", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin(origins = "https://ahamedsapple.mannit.co")
    public Map<String, List<?>> getByDate(@RequestParam("date") String date) {
        String formattedDate = convertDateFormat(date, "yyyy-MM-dd", "MM/dd/yyyy");
        List<CurrentPatient> cp = repo.findByDate(formattedDate);
        System.out.println(cp.toString());
        List<QueriedPatient> qp = quried_repo.findByDate(formattedDate);
        List<Noappointment> np = no_app_repo.findByDate(formattedDate);
        System.out.println(cp);
        List<Noappointment> uniqueNp = removeDuplicates(np, Noappointment::getTimestamp);
        List<CurrentPatient> uniqueCp = removeDuplicates(cp, CurrentPatient::getTimestamp);
        List<QueriedPatient> uniqueQp = removeDuplicates(qp, QueriedPatient::getTimestamp);

        Map<String, List<?>> result = new HashMap<>();
        result.put("currentPatients", uniqueCp);
        result.put("queriedPatients", uniqueQp);
        result.put("noAppointments", uniqueNp);
        return result;
    }

    @RequestMapping(value = "/getdata", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, List<?>> getcurrentpatient(Model model) throws ParseException {
        LocalDateTime current_time = LocalDateTime.now();
        String formattedDate = convertDateFormat(current_time.toString(), "yyyy-MM-dd", "MM/dd/yyyy");
        System.out.println("formated date------------s" + formattedDate);
        List<CurrentPatient> cp = repo.findByDate(formattedDate);
        List<QueriedPatient> qp = quried_repo.findByDate(formattedDate);
        List<Noappointment> np = no_app_repo.findByDate(formattedDate);
        List<Noappointment> uniqueNp = removeDuplicates(np, Noappointment::getTimestamp);
        List<CurrentPatient> uniqueCp = removeDuplicates(cp, CurrentPatient::getTimestamp);
        List<QueriedPatient> uniqueQp = removeDuplicates(qp, QueriedPatient::getTimestamp);
        ;
        Map<String, List<?>> result = new HashMap<>();

        result.put("currentPatients", uniqueCp);
        result.put("queriedPatients", uniqueQp);
        result.put("noAppointments", uniqueNp);
        return result;
    }

    private static <T> List<T> removeDuplicates(List<T> list,
            java.util.function.Function<T, String> timestampExtractor) {
        return list.stream()
                .collect(Collectors.toMap(timestampExtractor, item -> item, (existing, replacement) -> existing))
                .values().stream().collect(Collectors.toList());
    }

    public String convertDateFormat(String inputDateStr, String inputFormat, String outputFormat) {
        DateFormat inputDateFormat = new SimpleDateFormat(inputFormat);
        DateFormat outputDateFormat = new SimpleDateFormat(outputFormat);
        try {
            Date date = inputDateFormat.parse(inputDateStr);
            return outputDateFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

    }

//@GetMapping("/m") 
    public boolean compare(String sheetdate, String dbdate) {
        // String dateString = "01/09/2024 10:33:42";
        String dateFormat = "MM/dd/yyyy HH:mm:ss";
//	sheetdate = "01/09/2024 10:35:42";
//	dbdate = "01/09/2024 10:33:42";

        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);

        try {
            Date parsedDate = sdf.parse(sheetdate);
            Date parseddbdate = sdf.parse(dbdate);
            parseddbdate.compareTo(parsedDate);
            System.out.println(parsedDate.after(parseddbdate));
            boolean parsevalue = parsedDate.after(parseddbdate);
            // System.out.println(parsevalue);
            return parsevalue;
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String insertDummyDocument() {
        if (date_repo.count() > 0) {
            Optional<Latestdatecollection> existingDocument = date_repo.findAll().stream().findFirst();

            // Return the ID if the existing document is found
            return existingDocument.map(Latestdatecollection::getId).orElse("No document found");
        } else {
            // Document doesn't exist, create and save the dummy document
            Latestdatecollection dummyDocument = new Latestdatecollection();
            dummyDocument.setRejected_lastupdated("01/23/2024 10:32:51");
            dummyDocument.setAppointment_lastupdated("01/23/2024 9:51:56");
            dummyDocument.setQueried_lastupdated("12/21/2023 20:00:02");

            // Save the dummy document
            Latestdatecollection savedDocument = date_repo.save(dummyDocument);

            // Return the ID of the saved document
            return savedDocument.getId();
        }
    }

    public void getsortedlist(List<CurrentPatient> CpatientList) {
        // ,List<Noappointment>NpatientList,List<QueriedPatient>QpatientList
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Comparator<CurrentPatient> timestampComparator = Comparator.comparing(patient -> {
            try {
                return dateFormat.parse(patient.getTimestamp());
            } catch (ParseException e) {
                e.printStackTrace();
                return new Date(0);
            }
        });
        Collections.sort(CpatientList, timestampComparator);

        for (CurrentPatient patient : CpatientList) {
            System.out.println(patient);
        }
    }
    /*
     * @PostMapping("/generate") public ResponseEntity<byte[]>
     * generatePdf(@RequestBody Map<String, List<Map<String, String>>> rowData) {
     * System.out.println("the data to convert in to pdf" + rowData.toString());
     * byte[] pdfBytes = generatePdf2(rowData);
     * 
     * try { Path downloadsDirectory = Path.of(System.getProperty("user.home"),
     * "Downloads"); String baseFileName = "report"; String fileExtension = ".pdf";
     * 
     * int counter = 1; Path pdfPath; do { String fileName = baseFileName + (counter
     * > 1 ? "(" + counter + ")" : "") + fileExtension; pdfPath =
     * downloadsDirectory.resolve(fileName); counter++; } while
     * (Files.exists(pdfPath)); Files.write(pdfPath, pdfBytes,
     * StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
     * System.out.println("PDF file saved to: " + pdfPath.toString()); } catch
     * (IOException e) { e.printStackTrace(); }
     * 
     * HttpHeaders headers = new HttpHeaders();
     * headers.setContentType(MediaType.APPLICATION_PDF);
     * headers.setContentDispositionFormData("attachment", "report.pdf");
     * 
     * return
     * ResponseEntity.ok().headers(headers).contentLength(pdfBytes.length).body(
     * pdfBytes); }
     * 
     * public byte[] generatePdf2(Map<String, List<Map<String, String>>> rowData) {
     * ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
     * 
     * try (PdfWriter pdfWriter = new PdfWriter(byteArrayOutputStream); PdfDocument
     * pdfDocument = new PdfDocument(pdfWriter); Document document = new
     * Document(pdfDocument)) {
     * 
     * for (Map.Entry<String, List<Map<String, String>>> entry : rowData.entrySet())
     * { String tableName = entry.getKey(); List<Map<String, String>> tableData =
     * entry.getValue(); if (!tableData.isEmpty()) { document.add(new
     * Paragraph(tableName)); Table table = createTable(tableData);
     * document.add(table); document.add(new Paragraph("\n"));
     * 
     * } else { document.add(new Paragraph("no data available for this table")); } }
     * document.close(); } catch (IOException e) { e.printStackTrace(); }
     * 
     * return byteArrayOutputStream.toByteArray(); }
     * 
     * private Table createTable(List<Map<String, String>> tableData) { Table table
     * = new Table(tableData.get(0).size()); float columnWidth = 200f;
     * 
     * for (Map.Entry<String, String> header : tableData.get(0).entrySet()) { Cell
     * headerCell = new Cell().add(new Paragraph(header.getKey()));
     * headerCell.setWidth(columnWidth); table.addHeaderCell(headerCell); }
     * 
     * for (Map<String, String> rowData : tableData) { for (String value :
     * rowData.values()) { Cell cell = new Cell().add(new Paragraph(value));
     * cell.setWidth(columnWidth); table.addCell(cell); } } return table; }
     */

}
