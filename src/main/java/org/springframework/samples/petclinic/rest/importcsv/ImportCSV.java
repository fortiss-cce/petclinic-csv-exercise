package org.springframework.samples.petclinic.rest.importcsv;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
/* This class implements the backend for the Spring Petclinic service.
The input data is expected to be in csv format.*/
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    private String field;
    private int i;
    private ArrayList<PetType> petTypes;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    /* This method implements the addition/deletion of pets in the clinic service.*/
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        field = "";
        i = 0;
        petTypes = (ArrayList<PetType>) clinicService.findPetTypes();
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;
        boolean petInformationIsAvailable;

        do {
            pet = new Pet();

            setPetName(csv, pet);

            ResponseEntity<List<Pet>> invalidDateHeaders = setPetBirthDate(csv, pet);
            if (invalidDateHeaders != null) return invalidDateHeaders;

            setPetType(csv, pet);

            ResponseEntity<List<Pet>> ownerExceptionHeaders = setPetOwner(csv, pet);
            if (ownerExceptionHeaders != null) return ownerExceptionHeaders;

            managePetInfoInClinicService(csv, pet);

            i++;
            pets.add(pet);

            petInformationIsAvailable = i < csv.length() && pet != null;
        } while (petInformationIsAvailable);

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    private void managePetInfoInClinicService(String csv, Pet pet) {
        if (csv.charAt(i) == ';') {
            i++;

            parseField(csv);

            if (field.toLowerCase().equals("add")) {
                clinicService.savePet(pet);
            } else {
                deletePetFromClinicService(pet);
            }

        } else {
            clinicService.savePet(pet);
        }
    }

    private void deletePetFromClinicService(Pet pet) {
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

    private ResponseEntity<List<Pet>> setPetOwner(String csv, Pet pet) {
        parseField(csv);

        if (pet != null) {
            String owner = field;
            List<Owner> matchingOwners = getMatchingOwners(owner);

            ResponseEntity<List<Pet>> ownerNotFoundHeaders = checkOwnerInDatabase(matchingOwners);
            if (ownerNotFoundHeaders != null) return ownerNotFoundHeaders;

            ResponseEntity<List<Pet>> ownerNotUniqueHeaders = checkOwnerUniqueness(matchingOwners);
            if (ownerNotUniqueHeaders != null) return ownerNotUniqueHeaders;
            pet.setOwner(matchingOwners.iterator().next());
        }
        return null;
    }

    private ResponseEntity<List<Pet>> checkOwnerUniqueness(List<Owner> matchingOwners) {
        if (matchingOwners.size() > 1) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "Owner not unique");
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    private ResponseEntity<List<Pet>> checkOwnerInDatabase(List<Owner> matchingOwners) {
        if (matchingOwners.size() == 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "Owner not found");
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    private List<Owner> getMatchingOwners(String owner) {
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(owner))
            .collect(Collectors.toList());
        return matchingOwners;
    }

    private void setPetType(String csv, Pet pet) throws DataAccessException {
        parseField(csv);
        if (pet != null) {
            for (int j = 0; j < petTypes.size(); j++) {
                if (petTypes.get(j).getName().toLowerCase().equals(field)) {
                    pet.setType(petTypes.get(j));
                    break;
                }
            }
        }
    }

    private ResponseEntity<List<Pet>> setPetBirthDate(String csv, Pet pet) {
        parseField(csv);
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(field));
        } catch (ParseException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "date " + field + " not valid");
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    private void setPetName(String csv, Pet pet) {
        parseField(csv);
        pet.setName(field);
    }

    private void parseField(@RequestBody String csv) {
        field = "";
        while (i < csv.length() && csv.charAt(i) != ';') {
            field += csv.charAt(i++);
        }
        i++;
    }
}
