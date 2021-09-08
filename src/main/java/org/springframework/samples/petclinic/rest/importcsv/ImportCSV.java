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
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) throws ParseException {

        int currentPosition = 0;
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;

        do {
            pet = new Pet();

            currentPosition = extractAndSetName(csv, currentPosition, pet);
            try {
                currentPosition = extractAndSetBirthDate(csv, currentPosition, pet);
            }
            catch (ParseException error) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", error.getMessage());
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            currentPosition = extractAndSetType(csv, currentPosition, pet);

            try {
                currentPosition = extractAndOwner(csv, currentPosition, pet);
            }
            catch (ParseException error) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", error.getMessage());
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            if (csv.charAt(currentPosition) == ';') {
                currentPosition = applyExtraAction(csv, currentPosition, pet);

            } else {
                clinicService.savePet(pet);
            }

            pets.add(pet);

        } while (currentPosition < csv.length() && pet != null);

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    public class Result {
        public Result(int currPosition) {
            field = "";
            position = currPosition;
        }
        public String field;
        public int position;
    }

    private Result getNextWord(String csv, int currPosition, Character[] delimiters) {
        Result result = new Result(currPosition);
        if (result.position < csv.length()) {
            boolean canAdvance = true;
            for (Character delimiter: delimiters) {
                if (csv.charAt(result.position) == delimiter) {
                    canAdvance = false;
                    break;
                }
            }
            if (canAdvance) {
                result.field += csv.charAt(result.position++);
            }
        }
        return result;
    }

    private int extractAndSetName(String csv, int currPosition, Pet pet) {
        Character[] semicolon = {';'};
        Result result = getNextWord(csv, currPosition, semicolon);
        pet.setName(result.field);
        return ++result.position;
    }

    private int extractAndSetBirthDate(String csv, int currPosition, Pet pet) throws ParseException {
        Character[] semicolon = {';'};
        Result result = getNextWord(csv, currPosition, semicolon);
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(result.field));
        } catch (ParseException e) {
            throw new ParseException("date " + result.field + " not valid", result.position);
        }
        return ++result.position;
    }


    private int extractAndSetType(String csv, int currPosition, Pet pet) {
        Character[] semicolon = {';'};
        Result result = getNextWord(csv, currPosition, semicolon);

        if (pet != null) {
            ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
            for (int j = 0; j < ts.size(); j++) {
                if (ts.get(j).getName().toLowerCase().equals(result.field)) {
                    pet.setType(ts.get(j));
                    break;
                }
            }
        }
        return ++result.position;
    }

    private int extractAndOwner(String csv, int currPosition, Pet pet) throws ParseException {
        Character[] semicolon = {';', '\n'};
        Result result = getNextWord(csv, currPosition, semicolon);

        if (pet != null) {
            String owner = result.field;
            List<Owner> matchingOwners = clinicService.findAllOwners()
                .stream()
                .filter(o -> o.getLastName().equals(owner))
                .collect(Collectors.toList());

            if (matchingOwners.size() == 0) {
                throw new ParseException("Owner not found", result.position);

            }
            if (matchingOwners.size() > 1) {
                throw new ParseException("Owner not unique", result.position);

            }
            pet.setOwner(matchingOwners.iterator().next());
        }
        return ++result.position;
    }

    private int applyExtraAction(String csv, int currPosition, Pet pet) {
        Character[] semicolon = {'\n'};
        Result result = getNextWord(csv, currPosition, semicolon);

        if (result.field.toLowerCase().equals("add")) {
            clinicService.savePet(pet);
        } else {
            for (Pet q : pet.getOwner().getPets()) {
                if (q.getName().equals(pet.getName())) {
                    if (q.getType().getId().equals(pet.getType().getId())) {
                        if (pet.getBirthDate().equals(q.getBirthDate())) {
                            clinicService.deletePet(q);
                        }
                    }
                }
            }
        }

        return ++result.position;
    }
}
