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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    private static class ResultPair {
        private Pet pet = null;
        private final ResponseEntity<List<Pet>> responseEntity;

        public void setPet(Pet pet) {
            this.pet = pet;
        }

        public ResponseEntity<List<Pet>> getResponseEntity() {
            return responseEntity;
        }

        public Pet getPet() {
            return pet;
        }

        public ResultPair(Pet pet) {
            this.pet = pet;
            responseEntity = new ResponseEntity<>(HttpStatus.OK);
        }

        public ResultPair(ResponseEntity<List<Pet>> responseEntity) {
            this.responseEntity = responseEntity;
        }

    }

    @Autowired
    private ClinicService clinicService;


    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {
        List<Pet> pets = new LinkedList<>();

        String[] lines = csv.split("\n");

        for (String line : lines) {
            ResultPair parsedPet = parseCsvLine(line);
            ResponseEntity<List<Pet>> responseEntity = parsedPet.getResponseEntity();
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                pets.add(parsedPet.getPet());
            } else {
                return responseEntity;
            }
        }
        return new ResponseEntity<>(pets, HttpStatus.OK);
    }
    private ResultPair generateErrorResponse(String errorMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", errorMessage);
        return new ResultPair(new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST));
    }

    private static boolean petEquals(Pet lhs, Pet rhs) {
        return
            lhs.getName().equals(rhs.getName()) &&
            lhs.getType().getId().equals(rhs.getType().getId()) &&
            lhs.getBirthDate().equals(rhs.getBirthDate());
    }


    private ResultPair parseCsvLine(String csvLine) {
        Pet pet = new Pet();

        String[] fields = csvLine.split(";");
        int curFieldId = 0;
        String petName = fields[curFieldId++];
        pet.setName(petName);

        String birthdate= fields[curFieldId++];

        ResultPair birthdateParseError = setPetBirthdate(pet, birthdate);
        if (birthdateParseError != null) return birthdateParseError;

        String petType = fields[curFieldId++];

        setPetType(pet, petType);

        String owner = fields[curFieldId++];

        ResultPair ownerNotFound = setPetOwner(pet, owner);
        if (ownerNotFound != null) return ownerNotFound;

        executeOperation(fields, pet, curFieldId);

        return new ResultPair(pet);
    }

    private ResultPair setPetBirthdate(Pet pet, String birthdate) {
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(birthdate));
        } catch (ParseException e) {
            return generateErrorResponse("date  "+ birthdate + " not valid");
        }
        return null;
    }

    private void executeOperation(String[] fields, Pet pet, int curFieldId) {
        if (curFieldId < fields.length) {
            String operation = fields[curFieldId++];

            if (operation.equalsIgnoreCase("add")) {
                clinicService.savePet(pet);
            } else {
                removePet(pet);
            }
        } else {
            clinicService.savePet(pet);
        }
    }

    private ResultPair setPetOwner(Pet pet, String owner) {
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(owner))
            .collect(Collectors.toList());

        if (matchingOwners.isEmpty()) {
            return generateErrorResponse("Owner not found");
        }
        if (matchingOwners.size() > 1) {
            return generateErrorResponse("Owner not unique");
        }
        pet.setOwner(matchingOwners.get(0));
        return null;
    }

    private void setPetType(Pet pet, String petType) {
        Collection<PetType> petTypes = clinicService.findPetTypes();
        for (PetType t : petTypes) {
            if (t.getName().equalsIgnoreCase(petType)) {
                pet.setType(t);
                break;
            }
        }
    }

    private void removePet(Pet pet) {
        for (Pet q : pet.getOwner().getPets()) {
            if (petEquals(q, pet)) {
                clinicService.deletePet(q);
            }
        }
    }
}
