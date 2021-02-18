package org.springframework.samples.petclinic.rest.importcsv;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
// not sure if doing the star import is a good practice in Java
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    public enum PetType {
        DOG, CAT;
    }

    private class Entry{
        String petName;
        String date;
        PetType petType;
        String ownerName;
        String operation;

        private Entry(){
            // not sure which keyword does Java use to refer to itself, it is assumed 'this' is the right one
            this.petName = petName
            this.date = date
            this.petType = petType
            this.ownerName = ownerName
            this.operation = operation
        }
    }

    public List<String> parseCSV(String csv){
        // split csv with '\n' sign to get its rows
        List<String> lines = csv.split('\n');

        return lines
    }

    public List<Entry> createEntries(List<String> lines){
        List<Entry> entries = new List<Entry>();
        // now parse each field
        for(line: lines){
            if(line.count(';') != 4){
                continue
            }
            // it is assumed that these functions are properly defined to check the validity of each field,
            // and returns the parsed fields . This can also be wrapped in another function 'toEntry(line)''
            // some of the validity check of the original code is included, e.g., check for none or multiple pet owner
            petName = toName(line[0]);
            date = toDate(line[1]);
            petType = toPetType(line[2]);
            ownerName = toName(line[3]);
            operation = toOperation(line[4]);
            // ...
            entries.add(toEntry(petName, date, petType, ownerName, operation));
        }
        return entries;
    }
    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {
        // parse CSV and do validity check

        List<String> lines = parseCSV(csv);
        List<Entry> entries = createEntries(lines);

        List<Pet> pets = new LinkedList<Pet>();
        for(entry: entries){
            // we assume pet now accepts an entry, and set its attributes accordingly, e.g. : pet.setName(entry.petName)
            Pet pet(entry);

            switch(entry.operation.toLowerCase()){
                case "add":
                    clinicService.savePet(pet);
                case "delete":
                // original logic is wrapped in this function
                    clinicService.deletePet(pet);
            }
            pets.add(pet); 
        }

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }
}
