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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        // Bad: We just put i = 10 here and this was not caught by any test
        // @todo: Open an issue? Write a new test?
        int i = 0;  // @todo lines? characters?
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;

        do {
            // Process one line of the csv file
            pet = new Pet();
            //extractName(pet, popNextField(i, csv));  // @todo separate parsing and composition of pet.
            i = extractName(pet, i, csv);
            i = extractBirthDate(pet, i, csv);
            i = extractType(pet, i, csv);
            i = extractOwner(pet, i, csv);
            i = updateDatabase(pet, i, csv);
            pets.add(pet);
        } while (i < csv.length() && pet != null);

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    private int popNextField(int i, String field, String csv) {
        while (i < csv.length() && csv.charAt(i) != ';') {
            assert(csv.charAt(i) != '\n');
            field += csv.charAt(i++);
        }
        assert(csv.charAt(i) != '\n');
        assert(csv.charAt(i) == ';');
        i++;
        return i;
    }

    private int popNextFieldV2(int i, String field, String csv){
        while (i < csv.length() && (csv.charAt(i) != ';' && csv.charAt(i) != '\n')) {
            field += csv.charAt(i++);
        }
        return i;
    }

    private int popNextFieldV3(int i, String field, String csv){
        while (i < csv.length() && csv.charAt(i) != '\n') {
            assert(csv.charAt(i) != ';');
            field += csv.charAt(i++);
        }
        assert(csv.charAt(i) != ';');
        assert(csv.charAt(i) == '\n');
        return i;
    }

    private boolean areEqual(Pet onePet, Pet anotherPet) {
        return onePet.getName().equals(anotherPet.getName()) &&
            onePet.getType().getId().equals(anotherPet.getType().getId()) &&
            onePet.getBirthDate().equals(anotherPet.getBirthDate());
    }

    private int extractName(Pet pet, int i, String csv) {
        String field = "";
        i = popNextField(i, field, csv);
        pet.setName(field);
        i++;
        return i;
    }

    private int extractBirthDate(Pet pet, int i, String csv){
        String field = "";
        i = popNextField(i, field, csv);

        Date date;

        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse(field);
        } catch (ParseException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "date " + field + " not valid");
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
        }
        pet.setBirthDate(date);
        return i;
    }

    private int extractType(Pet pet, int i, String csv){
        String field = "";
        i = popNextField(i, field, csv);
        i++;

        if (pet != null) {
            ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
            for (int j = 0; j < ts.size(); j++) {
                if (ts.get(j).getName().toLowerCase().equals(field)) {
                    pet.setType(ts.get(j));
                    break;
                }
            }
        }

        return i;
    }

    private int extractOwner(Pet pet, int i, String csv) {
        String field = "";
        i = popNextFieldV2(i, field, csv);

        if (pet != null) {
            String owner = field;
            List<Owner> matchingOwners = clinicService.findAllOwners()
                .stream()
                .filter(o -> o.getLastName().equals(owner))
                .collect(Collectors.toList());

            if (matchingOwners.size() == 0) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "Owner not found");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
            if (matchingOwners.size() > 1) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "Owner not unique");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
            pet.setOwner(matchingOwners.iterator().next());
        }
        return i;
    }

    private int updateDatabase(Pet pet, int i, String csv) {
        if (csv.charAt(i) == ';') {
            i++;

            String field = "";
            i = popNextFieldV3(i, field, csv);

            if (field.toLowerCase().equals("add")) {
                clinicService.savePet(pet);
            } else if (field.toLowerCase().equals("delete")) {
                boolean petFound = false;
                for (Pet q : pet.getOwner().getPets()) {
                    if(areEqual(q, pet)){
                        clinicService.deletePet(q);
                        petFound = true;
                    }
                }
                assert(petFound);  // make sure that if a pet should be deleted it was also found. Otherwise: Invalid input.
            } else {
                assert(false);  // unexpected keyword
            }
        } else {
            clinicService.savePet(pet);
        }
        if(i < csv.length()) {
            assert(csv.charAt(i) != '\n');  // this is not the last line, but it must be terminated with a newline character
        }
        i++;
        return i;
    }
}
