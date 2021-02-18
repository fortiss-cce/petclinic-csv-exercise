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
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")


    private class Entry{
        String petName;
        Date date;
        PetType petType;
        Owner owner;
        String operation;

        private Entry(String petName, Date date, PetType petType, Owner owner, String operation){
            // not sure which keyword does Java use to refer to itself, it is assumed 'this' is the right one
            this.petName = petName;
            this.date = date;
            this.petType = petType;
            this.owner = owner;
            this.operation = operation;
        }
    }

    public String[] parseCSV(String csv){
        // split csv with '\n' sign to get its rows
        String[] lines = csv.split("\n");

        return lines;
    }

    public List<Entry> createEntries(String[] lines){
        List<Entry> entries = new LinkedList<Entry>();
        // now parse each field
        for (String line: lines) {
            // quick check if the number of ';' is correct in this line
            if(line.count(';') != 4){
                // print or log error
                continue;
            }
            // it is assumed that these functions are properly defined to check the validity of each field,
            // and returns the parsed fields. This can also be wrapped in another function 'toEntry(line)'.
            // some of the validity check of the original code is included, e.g., check for none or multiple pet owner
            String[] field = line.split(";");
            String petName = toPetName(field[0]);
            Date date = toDate(field[1]);
            PetType petType = toPetType(field[2]);
            Owner owner = toOwner(field[3]);
            String operation = toOperation(field[4]);
            entries.add(new Entry(petName, date, petType, owner, operation));
        }
        return entries;
    }

    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        int i = 0;
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;
        String[] lines = parseCSV(csv);
        List<Entry> entries = createEntries(lines);

        for (Entry entry : entries) {
            // we assume pet now accepts an entry, and set its attributes accordingly, e.g. : pet.setName(entry.petName)
            pet = new Pet();
            pet.setName(entry.petName);
            pet.setBirthDate(entry.date);
            pet.setType(entry.petType);
            pet.setOwner(entry.ownerName);

            switch (entry.operation.toLowerCase()) {
                case "add":
                    clinicService.savePet(pet);
                case "delete":
                    // originally provided logic can be wrapped in this function
                    clinicService.deletePet(pet);
            }
            pets.add(pet);
        }
    }

    private HttpHeaders constructHttpHeaders(String headerValue){
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", headerValue);
        return headers;
    }

    private Serializable toPetName(String name) {
        if (name != "null") {
            return name;
        } else {
            return constructHttpHeaders("Pet name is empty");
        }
    }

    private Serializable toDate(String date) {
        try {
            return (new SimpleDateFormat("yyyy-MM-dd")).parse(date);
        } catch (ParseException e) {
            return constructHttpHeaders("date " + date + " not valid");
        }
    }

    private Object toPetType(String petTypeCSV) {
        ArrayList<PetType> petTypes = (ArrayList<PetType>) clinicService.findPetTypes();
        for (PetType petType : petTypes) {
            if (petType.getName().equalsIgnoreCase(petTypeCSV)) {
                return petType;
            }
        }
        return constructHttpHeaders("Pet type does not exist");
    }

    private Object toOwner(String name) {
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(owner))
            .collect(Collectors.toList());

        if (matchingOwners.size() == 0) {
            return constructHttpHeaders("Owner not found");
        }
        // why should we throw error for owner not unique? Can't an owner have more than one pets at our clinic?
        // Or two person with same family names?
        if (matchingOwners.size() > 1) {
            return constructHttpHeaders("Owner not unique");
        }
        return matchingOwners.iterator().next();
    }

    private boolean isSamePet(Pet pet1, Pet pet2) {
        return (pet1.getName().equals(pet2.getName())) &&
            (pet1.getType().getId().equals(pet2.getType().getId())) &&
            (pet1.getBirthDate().equals(pet2.getBirthDate()));
    }
}
