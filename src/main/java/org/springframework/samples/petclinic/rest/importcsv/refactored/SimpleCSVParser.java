package org.springframework.samples.petclinic.rest.importcsv.refactored;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

final class SimpleCSVParser {
    private final String SEPARATORS = ";";

    private final Scanner inputScanner;

    public SimpleCSVParser(String csvData) {
        this.inputScanner = new Scanner(csvData);
    }

    public boolean hasNextRecord() {
        return inputScanner.hasNext();
    }

    public List<String> nextRecord() {
        String line = inputScanner.nextLine();
        String[] entries = line.split(SEPARATORS);
        return Arrays.asList(entries);
    }
}
