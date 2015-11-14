package fi.jamo.mokkicam.temp.gdocs;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    public static final HttpTransport HTTP_TRANSPORT = new ApacheHttpTransport();

    private static final String SPREADSHEET_FEED = "https://spreadsheets.google.com/feeds/spreadsheets/private/full";

    private static final URL SPREADSHEET_FEED_URL;

    private static final String ACCOUNT_ID = "serviceAccountId";
    private static final String P12_PATH = "p12Path";
    private static final String DOCUMENT_NAME = "documentName";
    private static final String SHEET_NAME = "sheetName";
    private static final String VALUES = "values";
    private static final String FILE = "file";
    private static final String KEYS = "keys";

    private static final Set<String> COMMANDS = new HashSet<>(5);

    private static final List<String> SCOPES = Arrays.asList(new String[]{"https://spreadsheets.google.com/feeds", SPREADSHEET_FEED});

    static {
        COMMANDS.add(ACCOUNT_ID);
        COMMANDS.add(P12_PATH);
        COMMANDS.add(DOCUMENT_NAME);
        COMMANDS.add(SHEET_NAME);
        COMMANDS.add(VALUES);
        COMMANDS.add(FILE);
        COMMANDS.add(KEYS);

        try {
            SPREADSHEET_FEED_URL = new URL(SPREADSHEET_FEED);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args)
            throws AuthenticationException, MalformedURLException, IOException, ServiceException, GeneralSecurityException {

        Map<String, String> params = new HashMap<>(5);
        Iterator<String> argsIterator = Arrays.asList(args).iterator();
        while (argsIterator.hasNext()) {
            String option = argsIterator.next();
            option = option.substring(2);
            if (COMMANDS.contains(option)) {
                params.put(option, argsIterator.next());
            } else {
                throw new RuntimeException("Unknown arg: " + option + " params: " + Arrays.deepToString(args));
            }
        }

        try {
            uploadData(params);
        } catch (Exception e) {
            System.out.println("Failed to upload: " + params.get(VALUES));
            throw new IllegalStateException(e);
        }
    }

    public static void uploadData(Map<String, String> params)
            throws AuthenticationException, MalformedURLException, IOException, ServiceException, GeneralSecurityException {

        GoogleCredential googleCredentials = new GoogleCredential.Builder()
                .setJsonFactory(JSON_FACTORY)
                .setTransport(HTTP_TRANSPORT)
                .setServiceAccountId(params.get(ACCOUNT_ID))
                .setServiceAccountPrivateKeyFromP12File(new File(params.get(P12_PATH)))
                .setServiceAccountScopes(SCOPES)
                .build();

        SpreadsheetService service
                = new SpreadsheetService("SpreadsheetUploader");
        service.setProtocolVersion(SpreadsheetService.Versions.V3);

        service.setOAuth2Credentials(googleCredentials);

        // Make a request to the API and get all spreadsheets.
        SpreadsheetFeed feed = service.getFeed(SPREADSHEET_FEED_URL,
                SpreadsheetFeed.class
        );
        List<SpreadsheetEntry> spreadsheets = feed.getEntries();
        SpreadsheetEntry spreadsheet = getSpreadSheetByName(spreadsheets, params.get(DOCUMENT_NAME));

        WorksheetFeed worksheetFeed = service.getFeed(
                spreadsheet.getWorksheetFeedUrl(), WorksheetFeed.class);
        List<WorksheetEntry> worksheets = worksheetFeed.getEntries();
        WorksheetEntry worksheet = getWorksheetByName(worksheets, params.get(SHEET_NAME));

        // Fetch the list feed of the worksheet.
        URL listFeedUrl = worksheet.getListFeedUrl();
//        ListFeed listFeed = service.getFeed(listFeedUrl, ListFeed.class);

        // Create a local representation of the new row.
        ListEntry toBeInstertedRow = new ListEntry();

        if (params.containsKey(VALUES)) {
            String[] entries = params.get(VALUES).split(";");
            for (String entry : entries) {
                String[] keyAndValue = entry.split("=");
                toBeInstertedRow.getCustomElements().setValueLocal(keyAndValue[0], keyAndValue[1]);
            }
            service.insert(listFeedUrl, toBeInstertedRow);
            System.out.println("Successfully uploaded: " + params.get(VALUES));
        } else if (params.containsKey(FILE) && params.containsKey(KEYS)) {
            Scanner fileReader = new Scanner(new File(params.get(FILE)));
            String[] keys = params.get(KEYS).split(",");
            while (fileReader.hasNextLine()) {
                toBeInstertedRow = new ListEntry();
                String[] entries = fileReader.nextLine().split(",");
                for (int i = 0; i < keys.length; i++) {
                    toBeInstertedRow.getCustomElements().setValueLocal(keys[i], entries[i]);
                }
                service.insert(listFeedUrl, toBeInstertedRow);
                System.out.println("Successfully uploaded: " + Arrays.deepToString(entries));
            }
        }

    }

    private static WorksheetEntry getWorksheetByName(List<WorksheetEntry> worksheets, String name) {
        for (WorksheetEntry worksheet : worksheets) {
            if (worksheet.getTitle().getPlainText().equals(name)) {
                return worksheet;
            }
        }
        throw new IllegalStateException("No worksheet found with name: " + name);
    }

    private static SpreadsheetEntry getSpreadSheetByName(List<SpreadsheetEntry> spreadsheets, String name) {
        for (SpreadsheetEntry spreadsheet : spreadsheets) {
            if (spreadsheet.getTitle().getPlainText().equals(name)) {
                return spreadsheet;
            }
        }
        throw new IllegalStateException("No spreadsheet found with name: " + name);
    }
}
